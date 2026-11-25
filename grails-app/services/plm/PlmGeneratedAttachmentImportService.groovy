package plm

import attachment.Attachment
import attachment.DocumentAccess
import attachment.DocumentCategory
import attachment.config.AttachmentContentType
import attachment.config.AttachmentContentTypeCategory
import attachment.config.DocumentCategoryEnum
import crew.User
import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import taack.domain.TaackAttachmentService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Stores ordinary worker outputs on the originating PLM part. */
class PlmGeneratedAttachmentImportService {
    TaackAttachmentService taackAttachmentService
    PlmCadVersionFileService plmCadVersionFileService

    @Transactional
    void importJobResult(Object job, Path resultZip) {
        if (!job || !Files.isRegularFile(resultZip))
            throw new IllegalArgumentException('Missing job or result archive')
        Map parameters = (Map) new JsonSlurper().parseText(job.parametersJson ?: '{}')
        Long partId = parsePartId(parameters.partId ?: parameters.entityId)
        if (!partId) throw new IllegalArgumentException('Worker job does not identify a PLM part')
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part) throw new IllegalArgumentException('PLM part not found: ' + partId)

        // A queued job must not silently register its output on a newer CAD revision.
        // Workers should submit cadMajorVersion/cadMinorVersion in job parameters.
        if (parameters.cadMajorVersion != null && parameters.cadMinorVersion != null) {
            if (parameters.cadMajorVersion.toString() != (part.majorVersion ?: 1).toString() ||
                parameters.cadMinorVersion.toString() != (part.minorVersion ?: 0).toString())
                throw new IllegalStateException('Worker result targets a different CAD revision')
        }

        // Worker API authentication is token based; currentUser is normally null.
        // Resolve an attributable user from the submitted job instead.
        User actor = job.submittedBy ? User.findByUsername(job.submittedBy.toString()) : null
        if (!actor) actor = part.userCreated
        if (!actor) throw new IllegalStateException('No user available to own generated attachments')

        ZipFile archive = new ZipFile(resultZip.toFile())
        try {
            def entries = archive.entries()
            int attached = 0
            long totalBytes = 0L
            while (entries.hasMoreElements()) {
                def entry = entries.nextElement()
                if (entry.directory) continue
                String name = entry.name
                if (!name || name.startsWith('/') || name.startsWith('\\') ||
                    name.contains('\\') || name.split('/').contains('..'))
                    throw new IllegalArgumentException('Unsafe worker output path: ' + name)
                // Metadata is not a generated document.
                if (name == 'conversion.json' || name == 'manifest.json') continue
                if (++attached > 100) throw new IllegalArgumentException('Too many generated attachments')
                if (entry.size > 100L * 1024 * 1024) throw new IllegalArgumentException('Output file too large')
                byte[] content = archive.getInputStream(entry).withCloseable { it.bytes }
                totalBytes += content.length
                if (totalBytes > 250L * 1024 * 1024) throw new IllegalArgumentException('Result exceeds attachment limit')
                if (!content.length) throw new IllegalArgumentException('Empty output: ' + name)
                String filename = name.tokenize('/').last()
                if (!filename || filename in ['.', '..']) throw new IllegalArgumentException('Invalid output filename')
                String engineeringType = engineeringFileType(filename)
                // CAD_TO_META's dedicated importer has already registered the
                // server-resolved assembly. The raw worker ASMJ still contains
                // unresolved partId values; never let it replace that attachment.
                if (job.jobType?.toString() == 'CAD_TO_META' && engineeringType == 'ASSEMBLY') {
                    log.info('Skipping raw CAD_TO_META ASMJ part={} job={} filename={}; resolved ASMJ is registered by metadata importer',
                        part.id, job.id, filename)
                    continue
                }
                Attachment attachment = persistAttachment(filename, content, actor)
                if (engineeringType != null) {
                    // The registry owns engineering associations; do not duplicate them
                    // in the unversioned comment-attachment list.
                    plmCadVersionFileService.register(part.id as Long, attachment.id as Long,
                        engineeringType, job.id?.toString())
                } else {
                    part.addToCommentVersionAttachmentList(attachment)
                }
            }
            if (!attached) throw new IllegalArgumentException('Worker returned no generated files')
            part.save(failOnError: true, flush: true)
        } finally {
            archive.close()
        }
    }


    /** Import declared drawing outputs, regardless of extension. Metadata and logs are never drawings. */
    @Transactional
    String importDrawingJobResult(Object job, Path resultZip) {
        if (!job || !Files.isRegularFile(resultZip))
            throw new IllegalArgumentException('Missing job or result archive')
        Map parameters = (Map) new JsonSlurper().parseText(job.parametersJson ?: '{}')
        Long partId = parsePartId(parameters.partId ?: parameters.entityId)
        if (!partId) throw new IllegalArgumentException('Drawing job does not identify a PLM part')
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part) throw new IllegalArgumentException('PLM part not found: ' + partId)
        if (part.active != true || part.nextVersion != null)
            throw new IllegalStateException('Drawing job targets a historical CAD part')
        Integer major = part.majorVersion ?: 1
        Integer minor = part.minorVersion ?: 0
        String version = "${major}.${minor}"
        if (parameters.cadVersion != null && parameters.cadVersion.toString() != version)
            throw new IllegalStateException('Drawing job targets a different CAD version')
        if (parameters.cadMajorVersion != null && parameters.cadMajorVersion.toString() != major.toString())
            throw new IllegalStateException('Drawing job targets a different CAD major version')
        if (parameters.cadMinorVersion != null && parameters.cadMinorVersion.toString() != minor.toString())
            throw new IllegalStateException('Drawing job targets a different CAD minor version')
        if (parameters.cadSha1 != null &&
            !parameters.cadSha1.toString().equalsIgnoreCase(part.plmContentShaOne ?: ''))
            throw new IllegalStateException('Drawing job targets a different CAD file')
        User actor = job.submittedBy ? User.findByUsername(job.submittedBy.toString()) : null
        if (!actor) actor = part.userCreated
        if (!actor) throw new IllegalStateException('No user available to own generated drawings')

        ZipFile archive = new ZipFile(resultZip.toFile())
        try {
            def manifestEntry = archive.getEntry('drawing_result.json')
            if (!manifestEntry || manifestEntry.directory || manifestEntry.size > 1024L * 1024L)
                throw new IllegalArgumentException('Missing or oversized drawing_result.json')
            byte[] metadataBytes = archive.getInputStream(manifestEntry).withCloseable { it.bytes }
            if (metadataBytes.length > 1024 * 1024) throw new IllegalArgumentException('Drawing manifest too large')
            def result = new JsonSlurper().parseText(new String(metadataBytes, 'UTF-8'))
            if (!(result instanceof Map) || !(result.drawings instanceof List))
                throw new IllegalArgumentException('Invalid drawing result manifest')
            if (result.partId != null && result.partId.toString() != partId.toString())
                throw new IllegalStateException('Drawing manifest targets a different part')
            if (result.cadVersion != null && result.cadVersion.toString() != version)
                throw new IllegalStateException('Drawing manifest targets a different CAD version')
            if (result.cadSha1 != null &&
                !result.cadSha1.toString().equalsIgnoreCase(part.plmContentShaOne ?: ''))
                throw new IllegalStateException('Drawing manifest targets a different CAD file')
            if (result.drawings.size() > 100) throw new IllegalArgumentException('Too many drawing files')
            Set<String> names = new HashSet<>()
            long totalBytes = 0L
            int count = 0
            for (def drawing : result.drawings) {
                if (!(drawing instanceof Map) || !(drawing.file instanceof String))
                    throw new IllegalArgumentException('Invalid drawing file entry')
                String filename = drawing.file
                if (!filename || filename.length() > 255 || filename in ['.', '..'] ||
                    filename.contains('/') || filename.contains('\\') || !names.add(filename))
                    throw new IllegalArgumentException('Unsafe or duplicate drawing filename')
                def entry = archive.getEntry(filename)
                if (!entry || entry.directory || entry.size > 100L * 1024L * 1024L)
                    throw new IllegalArgumentException('Missing or oversized drawing: ' + filename)
                byte[] content = archive.getInputStream(entry).withCloseable { it.bytes }
                if (!content.length || content.length > 100L * 1024L * 1024L)
                    throw new IllegalArgumentException('Invalid drawing content: ' + filename)
                totalBytes += content.length
                if (totalBytes > 250L * 1024L * 1024L)
                    throw new IllegalArgumentException('Drawings exceed total size limit')
                // Name generated files after the part, retaining page suffix and extension.
                String partBase = (part.originalName ?: part.label ?: 'part')
                    .replace('\\', '/').tokenize('/').last().replaceFirst(/(?i)\.FCStd$/, '')
                    .replaceAll(/[\\\/:*?"<>|\r\n]/, '_')
                String storedName = filename.replaceFirst(/(?i)^drawing(?=-|\.)/, java.util.regex.Matcher.quoteReplacement(partBase))
                Attachment attachment = persistAttachment(storedName, content, actor)
                plmCadVersionFileService.registerGeneratedDrawing(part.id as Long,
                    attachment.id as Long, job.id?.toString(), major, minor)
                count++
            }
            String message = count ? "${count} drawing(s) imported into PLM" : 'No drawing created'
            log.info('DRAWINGS result part={} version={} job={} count={}', part.id, version, job.id, count)
            return message
        } finally { archive.close() }
    }

    /**
     * Store the server-resolved assembly as an ASMJ engineering attachment.
     * CAD_TO_META uses a dedicated importer, so its result ZIP never passes
     * through importJobResult().
     */
    @Transactional
    Attachment saveResolvedAssemblyJson(Object job, PlmFreeCadPart part, String resolvedJson) {
        if (!job || !part || !resolvedJson?.trim())
            throw new IllegalArgumentException('Missing job, part, or resolved assembly JSON')
        if (job.jobType?.toString() != 'CAD_TO_META')
            throw new IllegalArgumentException('Only CAD_TO_META may save a resolved ASMJ')
        if (part.active != true || part.nextVersion != null ||
            part.lifecycleState == PlmPartLifecycleState.RELEASED)
            throw new IllegalStateException('Cannot register ASMJ on a historical or released part')

        Map parameters = (Map) new JsonSlurper().parseText(job.parametersJson ?: '{}')
        if (parameters.partId != null && parameters.partId.toString() != part.id.toString())
            throw new IllegalStateException('Metadata job targets a different part')
        String version = "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"
        if (parameters.cadVersion != null && parameters.cadVersion.toString() != version)
            throw new IllegalStateException('Metadata job targets a different CAD version')
        if (parameters.cadSha1 != null &&
            !parameters.cadSha1.toString().equalsIgnoreCase(part.plmContentShaOne ?: ''))
            throw new IllegalStateException('Metadata job targets a different CAD file')

        def parsed = new JsonSlurper().parseText(resolvedJson)
        if (!(parsed instanceof Map) || parsed.format?.toString() != 'PartsManagerAssembly')
            throw new IllegalArgumentException('Invalid resolved ASMJ document')
        byte[] bytes = resolvedJson.getBytes('UTF-8')
        if (bytes.length > 10L * 1024 * 1024)
            throw new IllegalArgumentException('Resolved ASMJ exceeds 10 MB')

        User actor = job.submittedBy ? User.findByUsername(job.submittedBy.toString()) : null
        if (!actor) actor = part.userCreated
        if (!actor) throw new IllegalStateException('No user available to own ASMJ attachment')

        // Stable filename plus content SHA-1 makes repeated result submission idempotent.
        Attachment attachment = persistAttachment('assembly.asmj', bytes, actor)
        plmCadVersionFileService.register(part.id as Long, attachment.id as Long,
            'ASSEMBLY', job.id?.toString())
        return attachment
    }

    private static String engineeringFileType(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT)
        if (lower.endsWith('.glb')) return 'GLB'
        if (lower.endsWith('.asmj')) return 'ASSEMBLY'
        if (lower.endsWith('.pdf') || lower.endsWith('.svg')) return 'DRAWING'
        if (lower.endsWith('.fcstd')) return 'CAD'
        return null
    }

    private static Long parsePartId(Object value) {
        if (value == null || !(value.toString() ==~ /[0-9]+/)) return null
        Long.valueOf(value.toString())
    }

    Attachment persistAttachment(String filename, byte[] content, User actor) {
        String sha = MessageDigest.getInstance('SHA-1').digest(content).encodeHex().toString()
        Attachment existing = Attachment.findByContentShaOneAndOriginalName(sha, filename)
        if (existing) return existing
        String ext = filename.contains('.') ? filename.substring(filename.lastIndexOf('.') + 1) : 'NONE'
        ext = ext.replaceAll(/[^a-zA-Z0-9]/, '').take(16) ?: 'NONE'
        Path store = Path.of(taackAttachmentService.storePath).toAbsolutePath().normalize()
        Files.createDirectories(store)
        Path target = store.resolve(sha + '.' + ext)
        if (!Files.exists(target)) {
            Path tmp = Files.createTempFile(store, 'worker-attachment-', '.tmp')
            try {
                Files.write(tmp, content)
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(tmp) }
        }
        String mime = Files.probeContentType(target) ?: 'application/octet-stream'
        Attachment attachment = new Attachment(
            filePath: target.fileName.toString(), originalName: filename,
            contentShaOne: sha, fileSize: (long) content.length,
            contentType: mime, contentTypeEnum: AttachmentContentType.fromMimeType(mime),
            contentTypeCategoryEnum: AttachmentContentTypeCategory.DOCUMENT,
            userCreated: actor, userUpdated: actor,
            documentCategory: DocumentCategory.findOrCreateByCategory(DocumentCategoryEnum.OTHER),
            documentAccess: DocumentAccess.findOrCreateByIsInternalAndIsRestrictedToMyBusinessUnitAndIsRestrictedToMySubsidiaryAndIsRestrictedToMyManagersAndIsRestrictedToEmbeddingObjects(false, false, false, false, true)
        )
        attachment.save(failOnError: true, flush: true)
        attachment
    }
}
