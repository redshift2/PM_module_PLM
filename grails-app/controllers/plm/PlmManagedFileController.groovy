package plm

import attachment.Attachment
import attachment.DocumentAccess
import attachment.DocumentCategory
import attachment.config.AttachmentContentType
import attachment.config.AttachmentContentTypeCategory
import attachment.config.DocumentCategoryEnum
import crew.User
import grails.gorm.transactions.Transactional
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory
import grails.plugin.springsecurity.annotation.Secured
import groovy.json.JsonOutput
import taack.domain.TaackAttachmentService

import java.nio.file.Files
import java.nio.file.Path

/** Read-only version-specific engineering file API. Does not change PLM UI. */
@Secured(['ROLE_PLM_USER', 'ROLE_ADMIN'])
class PlmManagedFileController {
    PlmCadVersionFileService plmCadVersionFileService
    PlmFreeCadSecurityService plmFreeCadSecurityService
    TaackAttachmentService taackAttachmentService
    def springSecurityService

    /** GET /plmManagedFile/list?partId=...&major=...&minor=...&type=GLB */
    def list() {
        Long partId = params.long('partId')
        Integer major = params.int('major')
        Integer minor = params.int('minor')
        if (partId == null || major == null || minor == null || major < 1 || minor < 0) {
            response.sendError(400, 'partId, major and minor are required'); return
        }
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part || !plmFreeCadSecurityService.canDownloadFile(part)) {
            response.sendError(404); return
        }
        String type = params.type?.toString()?.toUpperCase(java.util.Locale.ROOT)
        if (type && !(type in PlmCadVersionFileService.TYPES)) {
            response.sendError(400, 'Invalid engineering file type'); return
        }
        def records = plmCadVersionFileService.listForVersion(partId, major, minor, type)
        response.contentType = 'application/json;charset=UTF-8'
        response.setHeader('Cache-Control', 'private, no-store')
        render JsonOutput.toJson([partId: partId, major: major, minor: minor,
            files: records.collect { f ->
                [id: f.id, type: f.fileType, name: f.originalName,
                 sha1: f.contentShaOne, created: f.dateCreated?.time,
                 download: '/plmManagedFile/download/' + f.id]
            }])
    }

    /** POST /plmManagedFile/uploadDrawing — only checkout owner can add drawings. */
    @Transactional
    def uploadDrawing() {
        if (!request.method.equalsIgnoreCase('POST')) {
            response.sendError(405); return
        }
        Long partId = params.long('partId')
        PlmFreeCadPart part = partId == null ? null : PlmFreeCadPart.get(partId)
        User actor = springSecurityService.currentUser as User
        if (!part || !plmFreeCadSecurityService.canDownloadFile(part)) {
            response.sendError(404); return
        }
        if (part.active != true || part.nextVersion != null ||
            part.lifecycleState?.toString() != 'CHECKED_OUT' ||
            !plmFreeCadSecurityService.canUploadPart(part, actor)) {
            response.sendError(403, 'Only the checkout owner can upload drawings'); return
        }
        MultipartFile upload = request.getFile('drawing')
        if (upload == null || upload.empty) {
            response.sendError(400, 'Select a drawing file'); return
        }
        String filename = upload.originalFilename?.replace('\\', '/')?.tokenize('/')?.last()
        if (!filename || filename.length() > 255 || filename.contains('..') ||
            !(filename.toLowerCase(java.util.Locale.ROOT).endsWith('.pdf') ||
              filename.toLowerCase(java.util.Locale.ROOT).endsWith('.svg'))) {
            response.sendError(400, 'Only PDF and SVG drawings are accepted'); return
        }
        final long maxBytes = 25L * 1024L * 1024L
        if (upload.size <= 0 || upload.size > maxBytes) {
            response.sendError(413, 'Drawing must be 25 MB or smaller'); return
        }
        byte[] bytes = upload.bytes
        // Reject obvious extension spoofing; SVG is XML, not arbitrary HTML.
        String lower = filename.toLowerCase(java.util.Locale.ROOT)
        if (lower.endsWith('.pdf') && !(bytes.length >= 5 &&
            new String(bytes, 0, 5, 'US-ASCII') == '%PDF-')) {
            response.sendError(400, 'Invalid PDF file'); return
        }
        if (lower.endsWith('.svg')) {
            String head = new String(bytes, 0, Math.min(bytes.length, 4096), 'UTF-8')
            if (!(head =~ /(?is)<svg(?:\s|>)/).find() ||
                (head =~ /(?is)<script(?:\s|>)/).find() ||
                (head =~ /(?is)<!DOCTYPE/).find()) {
                response.sendError(400, 'Invalid or unsafe SVG file'); return
            }
        }
        String sha = MessageDigest.getInstance('SHA-1').digest(bytes).encodeHex().toString()
        Attachment attachment = Attachment.findByContentShaOneAndOriginalName(sha, filename)
        if (!attachment) {
            Path store = Path.of(taackAttachmentService.storePath).toAbsolutePath().normalize()
            Files.createDirectories(store)
            String ext = lower.endsWith('.pdf') ? 'pdf' : 'svg'
            Path target = store.resolve(sha + '.' + ext)
            if (!Files.isRegularFile(target)) {
                Path temp = Files.createTempFile(store, 'drawing-', '.tmp')
                try {
                    Files.write(temp, bytes)
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                } finally { Files.deleteIfExists(temp) }
            }
            String mime = ext == 'pdf' ? 'application/pdf' : 'image/svg+xml'
            attachment = new Attachment(
                filePath: target.fileName.toString(), originalName: filename,
                contentShaOne: sha, fileSize: (long) bytes.length,
                contentType: mime, contentTypeEnum: AttachmentContentType.fromMimeType(mime),
                contentTypeCategoryEnum: AttachmentContentTypeCategory.DOCUMENT,
                userCreated: actor, userUpdated: actor,
                documentCategory: DocumentCategory.findOrCreateByCategory(DocumentCategoryEnum.OTHER),
                documentAccess: DocumentAccess.findOrCreateByIsInternalAndIsRestrictedToMyBusinessUnitAndIsRestrictedToMySubsidiaryAndIsRestrictedToMyManagersAndIsRestrictedToEmbeddingObjects(false, false, false, false, true)
            ).save(failOnError: true, flush: true)
        }
        Long replaceId = params.long('replaceId')
        if (replaceId != null) {
            PlmCadVersionFile old = PlmCadVersionFile.get(replaceId)
            if (!old || old.part?.id != part.id || old.fileType != 'DRAWING' ||
                old.cadMajorVersion != (part.majorVersion ?: 1) ||
                old.cadMinorVersion != (part.minorVersion ?: 0)) {
                response.sendError(400, 'Invalid drawing to replace'); return
            }
            // Retain the original Attachment; only the active registry link is replaced.
            plmCadVersionFileService.register(part.id as Long, attachment.id as Long, 'DRAWING')
            old.delete(flush: true)
            log.info('PLM DRAWING REPLACE part={} version={}.{} oldRecord={} newAttachment={} actor={}',
                part.id, part.majorVersion, part.minorVersion, old.id, attachment.id, actor?.id)
        } else {
            plmCadVersionFileService.register(part.id as Long, attachment.id as Long, 'DRAWING')
            log.info('PLM DRAWING ADD part={} version={}.{} attachment={} actor={}',
                part.id, part.majorVersion, part.minorVersion, attachment.id, actor?.id)
        }
        redirect(controller: 'plm', action: 'showPart', id: part.id, fragment: 'plm-tab-drawings')
    }


    /** POST: remove a drawing association, never the underlying attachment. */
    @Transactional
    def removeDrawing(Long id) {
        if (!request.method.equalsIgnoreCase('POST')) { response.sendError(405); return }
        PlmCadVersionFile record = id == null ? null : PlmCadVersionFile.get(id)
        PlmFreeCadPart part = record?.part
        User actor = springSecurityService.currentUser as User
        if (!record || record.fileType != 'DRAWING' || !part ||
            !plmFreeCadSecurityService.canDownloadFile(part)) { response.sendError(404); return }
        if (part.active != true || part.nextVersion != null ||
            part.lifecycleState?.toString() != 'CHECKED_OUT' ||
            !plmFreeCadSecurityService.canUploadPart(part, actor) ||
            record.cadMajorVersion != (part.majorVersion ?: 1) ||
            record.cadMinorVersion != (part.minorVersion ?: 0)) {
            response.sendError(403, 'Only the checkout owner can remove drawings'); return
        }
        Long recordId = record.id
        record.delete(flush: true)
        log.info('PLM DRAWING REMOVE part={} version={}.{} record={} actor={}',
            part.id, part.majorVersion, part.minorVersion, recordId, actor?.id)
        redirect(controller: 'plm', action: 'showPart', id: part.id, fragment: 'plm-tab-drawings')
    }

    /** Authenticated inline PDF preview. SVG stays download-only to avoid active-content execution. */
    def previewPdf(Long id) {
        PlmCadVersionFile record = id == null ? null : PlmCadVersionFile.get(id)
        if (!record || record.fileType != 'DRAWING' ||
            !record.originalName?.toLowerCase(java.util.Locale.ROOT)?.endsWith('.pdf') ||
            !plmFreeCadSecurityService.canDownloadFile(record.part)) {
            response.sendError(404); return
        }
        Path store = Path.of(taackAttachmentService.storePath).toAbsolutePath().normalize()
        Path target = store.resolve(record.attachment?.filePath ?: '').normalize()
        if (!target.startsWith(store) || !Files.isRegularFile(target) || Files.isSymbolicLink(target)) {
            response.sendError(404); return
        }
        response.contentType = 'application/pdf'
        response.setHeader('Content-Disposition', 'inline')
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('Content-Security-Policy', "sandbox; default-src 'none'")
        response.setHeader('Cache-Control', 'private, no-store')
        response.setContentLengthLong(Files.size(target))
        Files.newInputStream(target).withCloseable { input -> response.outputStream << input }
        response.outputStream.flush()
    }

    /** Admin-only emergency restoration of a *missing* released GLB visualization.
     * Never changes CAD geometry, lifecycle, or an existing registered GLB.
     */
    @Transactional
    def repairMissingGlb() {
        if (!request.method.equalsIgnoreCase('POST')) { response.sendError(405); return }
        User actor = springSecurityService.currentUser as User
        if (!springSecurityService.isLoggedIn() ||
            !(actor?.authorities?.any { it.authority == 'ROLE_ADMIN' })) {
            response.sendError(403); return
        }
        Long partId = params.long('partId')
        PlmFreeCadPart part = partId == null ? null : PlmFreeCadPart.get(partId)
        if (!part || part.lifecycleState?.toString() != 'RELEASED' ||
            !plmFreeCadSecurityService.canDownloadFile(part)) {
            response.sendError(403, 'A released part is required'); return
        }
        Integer major = (part.majorVersion ?: 1) as Integer
        Integer minor = (part.minorVersion ?: 0) as Integer
        if (plmCadVersionFileService.listForVersion(part.id as Long, major, minor, 'GLB')) {
            response.sendError(409, 'GLB already registered; replacement is prohibited'); return
        }
        String reason = params.reason?.toString()?.trim()
        if (!reason || reason.length() < 10 || reason.length() > 200) {
            response.sendError(400, 'Repair reason (10–200 characters) required'); return
        }
        MultipartFile upload = request.getFile('glb')
        if (!upload || upload.empty || upload.size > 100L * 1024L * 1024L ||
            !upload.originalFilename?.toLowerCase(java.util.Locale.ROOT)?.endsWith('.glb')) {
            response.sendError(400, 'A GLB file of at most 100 MB is required'); return
        }
        byte[] bytes = upload.bytes
        if (bytes.length < 20 || bytes[0] != (byte) 0x67 || bytes[1] != (byte) 0x6c ||
            bytes[2] != (byte) 0x54 || bytes[3] != (byte) 0x46) {
            response.sendError(400, 'Invalid GLB magic'); return
        }
        long declaredSize = java.nio.ByteBuffer.wrap(bytes, 8, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL
        if (declaredSize != bytes.length) { response.sendError(400, 'Invalid GLB length'); return }
        String sha = MessageDigest.getInstance('SHA-1').digest(bytes).encodeHex().toString()
        String filename = upload.originalFilename.replace('\\','/').tokenize('/').last()
        Path store = Path.of(taackAttachmentService.storePath).toAbsolutePath().normalize()
        Files.createDirectories(store)
        Path target = store.resolve(sha + '.glb')
        if (!Files.isRegularFile(target)) {
            Path temp = Files.createTempFile(store, 'glb-repair-', '.tmp')
            try { Files.write(temp, bytes); Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING) }
            finally { Files.deleteIfExists(temp) }
        }
        Attachment attachment = Attachment.findByContentShaOneAndOriginalName(sha, filename)
        if (!attachment) {
            String mime = 'model/gltf-binary'
            attachment = new Attachment(
                filePath: target.fileName.toString(), originalName: filename,
                contentShaOne: sha, fileSize: (long) bytes.length,
                contentType: mime, contentTypeEnum: AttachmentContentType.fromMimeType(mime),
                contentTypeCategoryEnum: AttachmentContentTypeCategory.DOCUMENT,
                userCreated: actor, userUpdated: actor,
                documentCategory: DocumentCategory.findOrCreateByCategory(DocumentCategoryEnum.OTHER),
                documentAccess: DocumentAccess.findOrCreateByIsInternalAndIsRestrictedToMyBusinessUnitAndIsRestrictedToMySubsidiaryAndIsRestrictedToMyManagersAndIsRestrictedToEmbeddingObjects(false, false, false, false, true)
            ).save(failOnError: true, flush: true)
        }
        String auditId = 'GLB_REPAIR_' + System.currentTimeMillis() + '_' + actor.id
        // Explicitly bypass ordinary register(), which rightly forbids released edits.
        new PlmCadVersionFile(part: part, attachment: attachment,
            cadMajorVersion: major, cadMinorVersion: minor, fileType: 'GLB',
            originalName: filename, contentShaOne: sha,
            generatedByJobId: auditId).save(failOnError: true, flush: true)
        log.warn('PLM RELEASED GLB REPAIR audit={} part={} version={}.{} sha1={} actor={} reason={}',
            auditId, part.id, major, minor, sha, actor.id, reason.replaceAll(/[\r\n]/, ' '))
        redirect(controller: 'plm', action: 'showPart', id: part.id, fragment: 'plm-tab-drawings')
    }

    /** GET /plmManagedFile/download/{registryId} */
    def download(Long id) {
        PlmCadVersionFile record = id == null ? null : PlmCadVersionFile.get(id)
        if (!record || !record.part || !plmFreeCadSecurityService.canDownloadFile(record.part)) {
            response.sendError(404); return
        }
        Attachment attachment = record.attachment
        if (!attachment?.filePath) { response.sendError(404); return }
        // File paths stored in Attachment must never escape the configured store.
        Path store = Path.of(taackAttachmentService.storePath).toAbsolutePath().normalize()
        Path target = store.resolve(attachment.filePath).normalize()
        if (!target.startsWith(store) || !Files.isRegularFile(target) || Files.isSymbolicLink(target)) {
            response.sendError(404); return
        }
        String filename = record.originalName?.replaceAll(/[\\"\r\n]/, '_') ?: 'engineering-file'
        response.contentType = 'application/octet-stream'
        response.setHeader('Content-Disposition', 'attachment; filename="' + filename + '"')
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('Cache-Control', 'private, no-store')
        response.setContentLengthLong(Files.size(target))
        Files.newInputStream(target).withCloseable { input -> response.outputStream << input }
        response.outputStream.flush()
    }
}
