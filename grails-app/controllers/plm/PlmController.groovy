package plm

import attachment.DocumentCategory
import crew.User
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Secured(['ROLE_PLM_USER', 'ROLE_ADMIN'])
class PlmController {
    def springSecurityService
    DataSource dataSource
    PlmFreeCadSecurityService plmFreeCadSecurityService
    PlmPartLifecycleService plmPartLifecycleService
    PlmFreeCadUiService plmFreeCadUiService
    def taackAttachmentService
    PlmSearchService plmSearchService
    PlmEventBridgeService plmEventBridgeService
    PlmPreviewStorageService plmPreviewStorageService
    PlmCadVersionFileService plmCadVersionFileService
    PlmCadWeightService plmCadWeightService

    def index() { redirect(action: 'parts') }

    private List<PlmFreeCadPart> visible(List<PlmFreeCadPart> parts) {
        parts.findAll { PlmFreeCadPart p -> plmFreeCadSecurityService.canDownloadFile(p) }
    }

    private Map listModel(String mode) {
        int max = Math.min(Math.max(params.int('max') ?: 25, 1), 100)
        int offset = Math.max(params.int('offset') ?: 0, 0)
        String q = (params.q ?: '').toString().trim()
        String pattern = '%' + q.toLowerCase(java.util.Locale.ROOT) + '%'
        String condition = mode == 'obsolete' ? 'p.status = :obsolete' : 'p.status <> :obsolete'
        String parentCondition = mode == 'parents' ? ''' AND NOT EXISTS (
            SELECT l.id FROM PlmFreeCadLink l WHERE l.part = p AND l.parentPart.active = true
        )''' : ''
        List<PlmFreeCadPart> all = PlmFreeCadPart.executeQuery('''
            SELECT DISTINCT p FROM PlmFreeCadPart p
            WHERE p.active = true AND p.nextVersion IS NULL AND ''' + condition + parentCondition + '''
              AND (:pattern = '%%' OR LOWER(COALESCE(p.originalName,'')) LIKE :pattern
                OR LOWER(COALESCE(p.label,'')) LIKE :pattern
                OR LOWER(COALESCE(p.description1,'')) LIKE :pattern
                OR LOWER(COALESCE(p.description2,'')) LIKE :pattern
                OR LOWER(COALESCE(p.description3,'')) LIKE :pattern
                OR LOWER(COALESCE(p.material,'')) LIKE :pattern
                OR LOWER(COALESCE(p.supplier,'')) LIKE :pattern
                OR LOWER(COALESCE(p.dcn,'')) LIKE :pattern)
            ORDER BY p.label ASC
        ''', [obsolete: PlmFreeCadPartStatus.OBSOLETE, pattern:pattern])
        List<PlmFreeCadPart> permitted = visible(all)
        [parts:permitted.drop(offset).take(max), total:permitted.size(), max:max,
         offset:offset, query:q, mode:mode]
    }

    def parts() { render(view:'parts', model:listModel('parts')) }
    def parentParts() { render(view:'parts', model:listModel('parents')) }
    def obsoleteParts() { render(view:'parts', model:listModel('obsolete')) }
    def search(String q) { params.q = q ?: params.q; render(view:'parts', model:listModel('parts')) }

    private PlmFreeCadPart checkedPart(Long id) {
        PlmFreeCadPart part = id == null ? null : PlmFreeCadPart.get(id)
        if (!part || !plmFreeCadSecurityService.canDownloadFile(part)) return null
        part
    }

    def showPart(Long id, Long partVersion, Boolean isHistory) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }

        // Convert domain associations to simple view data before rendering.
        // This also avoids GSP expression resolution problems inside g:each.
        List<Map> components = []
        for (PlmFreeCadLink component : (part.plmLinks ?: [])) {
            PlmFreeCadPart child = component.part
            if (child != null && plmFreeCadSecurityService.canDownloadFile(child)) {
                components.add([
                    id: child.id,
                    label: child.label ?: child.originalName ?: 'Unnamed part',
                    linkVersion: component.partLinkVersion
                ])
            }
        }

        // CAD_TO_META stores extracted BOM rows in cadBomJson.items. The
        // PLM link association is a separate, optional source of related parts.
        List<Map> extractedBom = []
        try {
            def bomJson = part.cadBomJson
            if (bomJson) {
                def parsed = new groovy.json.JsonSlurper().parseText(bomJson.toString())
                def rows = parsed instanceof Map ? parsed.items : parsed
                if (rows instanceof Collection) {
                    rows.each { row ->
                        if (row instanceof Map) extractedBom.add(new LinkedHashMap(row))
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to read extracted CAD BOM for part ${part.id}: ${ex.message}")
        }

        // Include BOM-resolved components in Related Parts even when legacy
        // PlmFreeCadLink relationships have not been created.
        Set<Long> relatedIds = components.collect { it.id as Long } as Set<Long>
        extractedBom.each { row ->
            Long childId = null
            try {
                def rawId = row.partId ?: row.id
                if (rawId != null) childId = rawId.toString().toLong()
            } catch (Exception ignored) { }
            if (childId != null && !relatedIds.contains(childId)) {
                PlmFreeCadPart child = PlmFreeCadPart.get(childId)
                if (child != null && plmFreeCadSecurityService.canDownloadFile(child)) {
                    components.add([
                        id: child.id,
                        label: child.label ?: child.originalName ?: row.name ?: 'Unnamed part',
                        linkVersion: child.majorVersion == null || child.minorVersion == null ?
                            null : "${child.majorVersion}.${child.minorVersion}"
                    ])
                    relatedIds.add(childId)
                }
            }
        }

        List<Map> cadHistory = []
        int historyIndex = 0
        for (PlmFreeCadPart revision : (part.history ?: [])) {
            Integer major = revision.majorVersion == null ? 1 : revision.majorVersion
            Integer minor = revision.minorVersion == null ? 0 : revision.minorVersion
            // Registry entries may be attached to the root part or the historical
            // revision row. Never use files from another CAD version.
            List<PlmCadVersionFile> versionFiles = []
            for (Long registryPartId : ([part.id, revision.id].unique())) {
                versionFiles.addAll(plmCadVersionFileService.listForVersion(registryPartId, major, minor))
            }
            // Preview storage is keyed by the stable active part ID plus CAD
            // version. Historical snapshots are separate database rows and
            // must not be used as the sole preview storage key.
            File legacyGlb = plmPreviewStorageService.modelFileForVersion(part.id, major, minor)
            if (!legacyGlb.isFile() && revision.id != part.id) {
                // Compatibility with previews written against snapshot IDs.
                legacyGlb = plmPreviewStorageService.modelFileForVersion(revision.id, major, minor)
            }
            PlmCadVersionFile glbFile = versionFiles.find { it.fileType == 'GLB' }
            // Prefer the latest registry association if legacy duplicates remain.
            // Prefer the active part's entry over a historical snapshot for the
            // same CAD version; do not change any other file selection logic.
            PlmCadVersionFile asmjFile = versionFiles.findAll {
                it.fileType == 'ASSEMBLY' &&
                    it.originalName?.toLowerCase(java.util.Locale.ROOT)?.endsWith('.asmj')
            }.sort { a, b ->
                int aPriority = a.part?.id == part.id ? 1 : 0
                int bPriority = b.part?.id == part.id ? 1 : 0
                (bPriority <=> aPriority) ?: ((b.id ?: 0L) <=> (a.id ?: 0L))
            }.find { true }
            cadHistory.add([
                index: historyIndex++,
                previewTimestamp: revision.mTimeNs ?: part.mTimeNs,
                version: "${major}.${minor}",
                lifecycle: revision.lifecycleState?.toString() ?: '—',
                updated: revision.lastUpdated,
                glbFileId: glbFile?.id,
                // Legacy 3D previews are stored outside the managed-file registry.
                // Check the exact historical revision's preview path, not the latest part.
                previewGlbRevisionId: (!glbFile && legacyGlb.isFile()) ? revision.id : null,
                asmjFileId: asmjFile?.id
            ])
        }

        List<Map> attachments = []
        for (def attachment : (part.commentVersionAttachmentList ?: [])) {
            if (attachment?.originalName) {
                attachments.add([name: attachment.originalName.toString()])
            }
        }

        // Build DCN history from CAD revisions first. A DcnPart association
        // usually points to the active part, not its historical snapshot.
        List<Map> dcnReleaseHistory = []
        List<PlmFreeCadPart> dcnRevisions = ([part] + (part.history ?: []).findAll { it != null }).unique { it.id }
        List dcnLinks = []
        Class dcnClass = null
        try {
            Class dcnPartClass = grailsApplication.classLoader.loadClass('changemanagement.DcnPart')
            dcnClass = grailsApplication.classLoader.loadClass('changemanagement.DesignChangeNotice')
            dcnLinks = dcnPartClass.findAllByPartInList(dcnRevisions) ?: []
        } catch (ClassNotFoundException ignored) {
            // The optional Change Management module is not installed.
        }

        // Historical snapshots carry the lifecycle and DCN metadata at the
        // moment of release. Include all RELEASED CAD versions, even if their
        // Change Management association is missing or was later moved.
        List<PlmFreeCadPart> releasedRevisions = dcnRevisions.findAll {
            it.lifecycleState?.toString() == 'RELEASED'
        }.sort { x, y ->
            ((y.majorVersion ?: 1) <=> (x.majorVersion ?: 1)) ?:
            ((y.minorVersion ?: 0) <=> (x.minorVersion ?: 0))
        }
        Set<String> recordedVersions = new LinkedHashSet<String>()
        releasedRevisions.each { PlmFreeCadPart revision ->
            Integer major = revision.majorVersion ?: 1
            Integer minor = revision.minorVersion ?: 0
            String version = "${major}.${minor}"
            if (!recordedVersions.add(version)) return
            def link = dcnLinks.find { l ->
                l.majorVersion == major && l.minorVersion == minor &&
                    l.dcn?.dcnNumber?.toString() == revision.dcn?.toString()
            } ?: dcnLinks.find { l ->
                l.majorVersion == major && l.minorVersion == minor
            }
            String number = revision.dcn?.toString()?.trim() ?: link?.dcn?.dcnNumber?.toString()
            def notice = link?.dcn
            if (number && dcnClass && notice?.dcnNumber?.toString() != number) {
                notice = dcnClass.findByDcnNumber(number)
            }
            boolean approved = notice?.status?.toString() == 'RELEASED'
            dcnReleaseHistory << [
                status: approved ? 'Released' : 'Draft',
                releasedDcn: approved,
                lom: revision.lom?.toString() ?: '—',
                version: version,
                major: major,
                minor: minor,
                dcnId: notice?.id,
                dcnNumber: number ?: '—',
                approvedAt: approved ? revision.releasedAt : null,
                username: (approved ? revision.releasedBy?.username : revision.userUpdated?.username) ?:
                    revision.userCreated?.username ?: '—'
            ]
        }

        // An unreleased DCN tracks the active part's *current* CAD version,
        // even when the version has advanced since the DCN was assigned.
        String currentNumber = part.dcn?.toString()?.trim()
        def currentNotice = currentNumber && dcnClass ? dcnClass.findByDcnNumber(currentNumber) : null
        if (currentNotice && currentNotice.status?.toString() in ['DRAFT', 'IN_REVIEW', 'APPROVED']) {
            String currentVersion = "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"
            if (!recordedVersions.contains(currentVersion)) {
                dcnReleaseHistory << [
                    status: 'Draft', releasedDcn: false,
                    lom: part.lom?.toString() ?: '—',
                    version: currentVersion,
                    major: part.majorVersion ?: 1, minor: part.minorVersion ?: 0,
                    dcnId: currentNotice.id, dcnNumber: currentNumber,
                    approvedAt: null,
                    username: part.userUpdated?.username ?: part.userCreated?.username ?: '—'
                ]
            }
        }

        // Only the newest released CAD version with a RELEASED DCN is valid.
        // Older approved releases are superseded. Pending DCNs remain Draft.
        Map validRelease = dcnReleaseHistory.findAll { it.releasedDcn }.sort { x, y ->
            (y.major <=> x.major) ?: (y.minor <=> x.minor)
        }.find { true }
        dcnReleaseHistory.each { row ->
            if (row.releasedDcn) row.status = row.is(validRelease) ? 'Valid' : 'Superseded'
        }
        dcnReleaseHistory.sort { x, y ->
            (y.major <=> x.major) ?: (y.minor <=> x.minor)
        }

        render(view: 'showPart', model: [
            part: part, components: components, extractedBom: extractedBom, cadHistory: cadHistory, attachments: attachments,
            dcnReleaseHistory: dcnReleaseHistory,
            previewAvailable: plmPreviewStorageService.modelFile(part).isFile() || plmPreviewStorageService.x3dFile(part).isFile(),
            previewGlbAvailable: plmPreviewStorageService.modelFile(part).isFile(),
            previewX3dAvailable: plmPreviewStorageService.x3dFile(part).isFile(),
            canCheckOut: plmFreeCadSecurityService.canCheckOutPart(part, springSecurityService.currentUser as User),
            canRelease: plmFreeCadSecurityService.canReleasePart(part, springSecurityService.currentUser as User),
            canCancel: plmFreeCadSecurityService.canUploadPart(part, springSecurityService.currentUser as User)
        ])
    }

    /** Download a legacy GLB preview belonging to a specific historical CAD revision.
     *  The parent part is permission-checked, and revision membership is verified.
     */
    def downloadHistoryPreviewGlb(Long id, Long revisionId) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part || revisionId == null) { response.sendError(404); return }
        PlmFreeCadPart revision = (part.history ?: []).find { it?.id == revisionId }
        if (!revision) { response.sendError(404); return }
        Integer major = revision.majorVersion == null ? 1 : revision.majorVersion
        Integer minor = revision.minorVersion == null ? 0 : revision.minorVersion
        File model = plmPreviewStorageService.modelFileForVersion(part.id, major, minor)
        if (!model.isFile() && revision.id != part.id) {
            model = plmPreviewStorageService.modelFileForVersion(revision.id, major, minor)
        }
        if (!model.isFile()) { response.sendError(404); return }
        String version = "${revision.majorVersion == null ? 1 : revision.majorVersion}.${revision.minorVersion == null ? 0 : revision.minorVersion}"
        response.contentType = 'model/gltf-binary'
        response.setHeader('Content-Disposition', 'attachment; filename="plm-part-' + part.id + '-v' + version + '.glb"')
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('Cache-Control', 'private, no-store')
        response.setContentLengthLong(model.length())
        model.withInputStream { input -> response.outputStream << input }
        response.outputStream.flush()
    }

    /** Serve only the GLB stored for this exact part and CAD revision. */
    def previewGlb(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        File model = plmPreviewStorageService.modelFile(part)
        if (!model.isFile()) { response.sendError(404); return }
        response.contentType = 'model/gltf-binary'
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('Cache-Control', 'private, max-age=120')
        response.setContentLengthLong(model.length())
        model.withInputStream { input -> response.outputStream << input }
        response.outputStream.flush()
    }

    def downloadPreviewGlb(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        File model = plmPreviewStorageService.modelFile(part)
        if (!model.isFile()) { response.sendError(404); return }
        response.contentType = 'model/gltf-binary'
        response.setHeader('Content-Disposition', 'attachment; filename="part-' + id + '-v' +
            (part.majorVersion ?: 1) + '.' + (part.minorVersion ?: 0) + '.glb"')
        response.setContentLengthLong(model.length())
        model.withInputStream { input -> response.outputStream << input }
        response.outputStream.flush()
    }

    /** Serve XML X3D for this part and CAD revision. */
    def previewX3d(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        File model = plmPreviewStorageService.x3dFile(part)
        if (!model.isFile()) { response.sendError(404); return }
        response.contentType = 'model/x3d+xml'
        response.characterEncoding = 'UTF-8'
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('Cache-Control', 'private, max-age=120')
        response.setHeader('Content-Security-Policy', "default-src 'none'; sandbox")
        response.setContentLengthLong(model.length())
        model.withInputStream { input -> response.outputStream << input }
        response.outputStream.flush()
    }

    def downloadPreviewX3d(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        File model = plmPreviewStorageService.x3dFile(part)
        if (!model.isFile()) { response.sendError(404); return }
        response.contentType = 'application/octet-stream'
        response.setHeader('Content-Disposition', 'attachment; filename="part-' + id + '-v' +
            (part.majorVersion == null ? 1 : part.majorVersion) + '.' +
            (part.minorVersion == null ? 0 : part.minorVersion) + '.x3d"')
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setContentLengthLong(model.length())
        model.withInputStream { input -> response.outputStream << input }
        response.outputStream.flush()
    }

    @Secured(['ROLE_ADMIN'])
    def uploadPreviewX3d(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        def upload = request.getFile('model')
        if (!upload || upload.empty) { response.sendError(400, 'Select an X3D file'); return }
        try {
            plmPreviewStorageService.storeX3d(part, upload.inputStream, upload.size, upload.originalFilename)
            flash.message = 'X3D preview uploaded.'
        } catch (IllegalArgumentException ex) {
            flash.message = 'X3D upload rejected: ' + ex.message
            response.sendRedirect('/plm/showPart/' + part.id + '#plm-tab-preview')
            return
        }
        response.sendRedirect('/plm/showPart/' + part.id + '#plm-tab-preview')
    }

    /** Temporary administrator-only ingestion until the worker result hook is wired. */
    @Secured(['ROLE_ADMIN'])
    def uploadPreviewGlb(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        def upload = request.getFile('model')
        if (!upload || upload.empty) { response.sendError(400, 'Select a GLB file'); return }
        try {
            plmPreviewStorageService.store(part, upload.inputStream, upload.size, upload.originalFilename)
            flash.message = '3D preview uploaded.'
        } catch (IllegalArgumentException ex) {
            response.sendError(400, ex.message); return
        }
        response.sendRedirect('/plm/showPart/' + part.id + '#plm-tab-preview')
    }

    def editPartMetadata(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        boolean installed = grailsApplication.mappingContext.getPersistentEntity('materials.Material') != null &&
            grailsApplication.mappingContext.getPersistentEntity('materials.MaterialCategory') != null
        List<Map> categories = []
        List<Map> materials = []
        if (installed) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource)
            categories = jdbc.queryForList('SELECT id, name FROM pm_material_category WHERE active = TRUE ORDER BY name')
            materials = jdbc.queryForList('SELECT id, name, category_id FROM pm_material WHERE active = TRUE ORDER BY name')
        }
        render(view:'editPartMetadata', model:[part:part, materialLibraryInstalled:installed,
            categories:categories, materials:materials])
    }

    def savePartMetadata() {
        Long id = params.long('id')
        PlmFreeCadPart part = checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        Map previousMetadata = [description1: part.description1, description2: part.description2,
            description3: part.description3, material: part.material, supplier: part.supplier,
            surfaceTreatment: part.surfaceTreatment, dcn: part.dcn,
            cost: part.cost, weight: part.weight, lom: part.lom]
        String[] names = ['description1','description2','description3','material','supplier','surfaceTreatment','dcn']
        Map values = [:]
        for (String name : names) {
            String v = params[name]?.toString()?.trim()
            if (v && v.length() > (name.startsWith('description') ? 1000 : 255)) {
                response.sendError(400, "${name} is too long"); return
            }
            values[name] = v ?: null
        }
        boolean installed = grailsApplication.mappingContext.getPersistentEntity('materials.Material') != null &&
            grailsApplication.mappingContext.getPersistentEntity('materials.MaterialCategory') != null
        JdbcTemplate jdbc = new JdbcTemplate(dataSource)
        if (installed && values.material) {
            Integer count = jdbc.queryForObject('''SELECT COUNT(*) FROM pm_material m JOIN pm_material_category c
                ON c.id = m.category_id WHERE LOWER(m.name) = LOWER(?) AND m.active = TRUE AND c.active = TRUE''',
                Integer, values.material)
            if (!count) { response.sendError(400, 'Select an active material from the Materials Library'); return }
        }
        BigDecimal cost, weight
        try {
            cost = params.cost?.trim() ? new BigDecimal(params.cost.trim()) : null
            weight = params.weight?.trim() ? new BigDecimal(params.weight.trim()) : null
        } catch (NumberFormatException e) { response.sendError(400, 'Invalid cost or weight'); return }
        if ([cost,weight].any { it != null && (it < 0 || it.scale() > 4 || it.precision() - it.scale() > 15) }) {
            response.sendError(400, 'Cost and weight must be nonnegative DECIMAL(19,4)'); return
        }
        // Material changes invalidate the previously calculated mass. Recompute
        // from the stored FreeCAD volume and the selected material density.
        if (values.material != previousMetadata.material) {
            if (values.material && part.cadVolumeMm3 != null) {
                BigDecimal recalculated = plmCadWeightService.calculate(part.cadVolumeMm3, values.material)
                if (recalculated == null) {
                    response.sendError(400, 'Unable to calculate weight: material density is missing or invalid'); return
                }
                weight = recalculated
            } else {
                weight = null
            }
        }
        String lom = params.lom?.trim()
        if (lom && !(lom in ['A','B','C','P'])) { response.sendError(400, 'Invalid LOM'); return }
        int updated = jdbc.update('''UPDATE plm_free_cad_part SET description1=?, description2=?, description3=?,
            cost=?, weight=?, material=?, supplier=?, surface_treatment=?, lom=?, dcn=?
            WHERE id=? AND active=TRUE AND next_version_id IS NULL''',
            values.description1,values.description2,values.description3,cost,weight,
            values.material,values.supplier,values.surfaceTreatment,lom ?: null,values.dcn,id)
        if (updated != 1) { response.sendError(409, 'Part changed during update'); return }
        Map updatedMetadata = [description1: values.description1, description2: values.description2,
            description3: values.description3, material: values.material, supplier: values.supplier,
            surfaceTreatment: values.surfaceTreatment, dcn: values.dcn,
            cost: cost, weight: weight, lom: lom ?: null]
        Map changes = [:]
        updatedMetadata.each { key, value ->
            if (previousMetadata[key]?.toString() != value?.toString()) {
                changes[key] = [before: previousMetadata[key]?.toString(), after: value?.toString()]
            }
        }
        if (changes) plmEventBridgeService.report('PART_METADATA_EDIT', part,
            (springSecurityService.currentUser as User)?.username, [changes: changes])
        // The original PLM metadata save must not depend on Change Management.
        // Synchronize only when the DCN value changes and a compatible module is installed.
        if (values.dcn != previousMetadata.dcn) {
            try {
                def ctx = grailsApplication.mainContext
                if (ctx.containsBean('changeManagementService')) {
                    def cm = ctx.getBean('changeManagementService')
                    cm.validatePartDcnMetadata(id, values.dcn)
                    cm.syncPartDcnMetadata(id, values.dcn)
                }
            } catch (Exception ex) {
                log.warn('DCN association not synchronized for PLM part ' + id + ': ' + ex.message, ex)
                flash.message = 'Part metadata saved; DCN association could not be synchronized: ' + ex.message
            }
        }
        if (!flash.message) flash.message='Part metadata saved.' 
        response.setStatus(303)
        response.setHeader('Location', (request.contextPath ?: '') + '/plm/showPart/' + id.toString())
    }

    def editPart(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        render(view:'editPart',model:[part:part])
    }

    @Transactional
    def savePart() {
        Long id = params.long('id')
        PlmFreeCadPart part = checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        String target = params.lifecycleState
        User user = springSecurityService.currentUser as User
        try {
            if (target == 'CHECKED_OUT' && part.lifecycleState != PlmPartLifecycleState.CHECKED_OUT)
                plmPartLifecycleService.checkOutPart(id,user)
            else if (target == 'WIP' && part.lifecycleState == PlmPartLifecycleState.CHECKED_OUT)
                plmPartLifecycleService.cancelCheckOut(id,user)
            else if (target == 'RELEASED' && part.lifecycleState == PlmPartLifecycleState.WIP)
                plmPartLifecycleService.releasePart(id,user)
            else if (target != part.lifecycleState?.name()) { response.sendError(409, 'Invalid lifecycle transition'); return }
        } catch (IllegalStateException e) { response.sendError(403,e.message); return }
        flash.message='Lifecycle updated.'
        response.setStatus(303)
        response.setHeader('Location', (request.contextPath ?: '') + '/plm/showPart/' + id.toString())
    }

    @Transactional
    def releasePart(Long id) {
        params.lifecycleState='RELEASED'
        savePart()
    }

    def previewPart(Long id, Long partVersion, String timestamp) {
        PlmFreeCadPart part=checkedPart(id)
        if (!part) { response.sendError(404); return }
        if (partVersion != null && (partVersion < 0 || partVersion >= (part.history?.size() ?: 0))) {
            response.sendError(404); return
        }
        File file=plmFreeCadUiService.preview(part,partVersion)
        response.contentType=file.name.endsWith('.png') ? 'image/png' : 'image/webp'
        response.setHeader('Cache-Control','max-age=3200')
        response.outputStream << file.bytes
    }

    def downloadBinPart(Long id, Long partVersion) {
        PlmFreeCadPart part=checkedPart(id)
        if (!part) { response.sendError(404); return }
        if (partVersion != null && (partVersion < 0 || partVersion >= (part.history?.size() ?: 0))) {
            response.sendError(404); return
        }
        File file=plmFreeCadUiService.zipPart(part,partVersion)
        response.contentType='application/zip'
        response.setHeader('Content-Disposition', 'attachment; filename="plm-part-' + id + (partVersion == null ? '-latest' : '-history-' + partVersion) + '.zip"')
        response.outputStream << file.bytes
    }

    def editPartCategory(Long id) {
        PlmFreeCadPart part=checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        render(view:'editPartCategory',model:[part:part,
            categories:DocumentCategory.list(sort:'id')])
    }

    @Transactional
    def savePartCategory(Long id) {
        PlmFreeCadPart part=checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        part.documentCategory=params.long('documentCategory') ? DocumentCategory.get(params.long('documentCategory')) : null
        part.save(flush:true,failOnError:true)
        flash.message='Category updated.'
        response.sendRedirect('/plm/showPart/' + id.toString())
    }

    @Transactional
    def saveComment(Long id) {
        PlmFreeCadPart part=checkedPart(id)
        if (!part || !part.active || part.nextVersion) { response.sendError(404); return }
        part.comment=params.comment?.toString()
        part.save(flush:true,failOnError:true)
        flash.message='Comment saved.'
        response.sendRedirect('/plm/showPart/' + id.toString())
    }

    def addComment(Long id) {
        PlmFreeCadPart part=checkedPart(id)
        if (!part) { response.sendError(404); return }
        render(view:'addComment',model:[part:part])
    }

    // Compatibility endpoints retained while PlmFreeCadUiService still
    // compiles its legacy Taack UI builders. These methods do not render Taack UI.
    def addAttachment(Long id) {
        PlmFreeCadPart part = checkedPart(id)
        if (!part) { response.sendError(404); return }
        render(status: 501, text: 'Attachment picker has not yet been migrated to GSP.')
    }

    @Transactional
    def onDrop(Long id) {
        PlmFreeCadPart part = checkedPart(id ?: params.long('id'))
        if (!part) { response.sendError(404); return }
        render(status: 501, text: 'Attachment drop upload has not yet been migrated to GSP.')
    }

    def previewAsciidoc(Long id) {
        PlmFreeCadPart part = PlmFreeCadPart.get(id)
        if (!part || !plmFreeCadSecurityService.canDownloadFile(part)) {
            response.sendError(404)
            return
        }
        render(contentType: 'text/html', text: plmFreeCadUiService.genAsciidoc(part))
    }

    def downloadBinCommentVersionFiles(Long id, String path) {
        PlmFreeCadPart part = PlmFreeCadPart.get(id)
        if (!part || !plmFreeCadSecurityService.canDownloadFile(part)) {
            response.sendError(404)
            return
        }
        part = part.nextVersion ?: part
        String fileName = path?.startsWith('/') ? path.substring(1) : path
        if (!fileName || fileName.contains('/') || fileName.contains('\\')) {
            response.sendError(400)
            return
        }
        def attachment = part.commentVersionAttachmentList?.find { it.originalName == fileName }
        if (!attachment) { response.sendError(404); return }
        taackAttachmentService.downloadAttachment(attachment)
    }
}