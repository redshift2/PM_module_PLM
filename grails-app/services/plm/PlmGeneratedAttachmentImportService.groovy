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
import java.nio.charset.StandardCharsets
import taack.domain.TaackAttachmentService

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Stores ordinary worker outputs on the originating PLM part. */
class PlmGeneratedAttachmentImportService {
    TaackAttachmentService taackAttachmentService

    @Transactional
    void importJobResult(Object job, Path resultZip) {
        if (!job || !Files.isRegularFile(resultZip))
            throw new IllegalArgumentException('Missing job or result archive')
        Map parameters = (Map) new JsonSlurper().parseText(job.parametersJson ?: '{}')
        Long partId = parsePartId(parameters.partId ?: parameters.entityId)
        if (!partId) throw new IllegalArgumentException('Worker job does not identify a PLM part')
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part) throw new IllegalArgumentException('PLM part not found: ' + partId)

        // Name generated documents using the CAD revision submitted with the job.
        // Never use the current revision for an old queued job without checking it.
        String cadVersion = (parameters.cadVersion ?: "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}").toString()
        if (parameters.cadVersion && cadVersion != "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}")
            throw new IllegalStateException('Generated output revision no longer matches current PLM part')
        String partName = (part.label ?: part.originalName ?: 'part-' + part.id).toString()
        partName = partName.replaceAll(/(?i)\.(fcstd|step|stp|iges|igs|glb|obj|stl)$/, '')
        partName = partName.replaceAll(/[^\p{L}\p{N}._-]+/, '_').replaceAll(/^\.+|\.+$/, '').take(100)
        if (!partName) partName = 'part-' + part.id
        String prefix = partName + '_v' + cadVersion.replaceAll(/[^0-9A-Za-z._-]/, '_')

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
                String sourceName = name.tokenize('/').last()
                String lower = sourceName.toLowerCase(java.util.Locale.ROOT)
                // Logs, diagnostics and temporary worker files are never PLM attachments.
                if (lower == 'conversion.json' || lower == 'manifest.json' ||
                    lower == 'metadata.json' || lower == 'bom.json' || lower == 'assembly.json' ||
                    lower.endsWith('.log') || lower.endsWith('.tmp') || lower.endsWith('.trace') ||
                    lower.endsWith('.pyc') || lower == 'freecad_glb_geometry.json' ||
                    lower == 'freecad_glb_export.py' || lower == 'freecad_glb_error.log' ||
                    lower == 'stdout.txt' || lower == 'stderr.txt' ||
                    lower.startsWith('debug') || lower.startsWith('traceback')) continue
                if (++attached > 100) throw new IllegalArgumentException('Too many generated attachments')
                if (entry.size > 100L * 1024 * 1024) throw new IllegalArgumentException('Output file too large')
                byte[] content = archive.getInputStream(entry).withCloseable { it.bytes }
                totalBytes += content.length
                if (totalBytes > 250L * 1024 * 1024) throw new IllegalArgumentException('Result exceeds attachment limit')
                if (!content.length) throw new IllegalArgumentException('Empty output: ' + name)
                String filename = sourceName
                if (!filename || filename in ['.', '..']) throw new IllegalArgumentException('Invalid output filename')
                String extension = filename.contains('.') ? filename.substring(filename.lastIndexOf('.')) : ''
                extension = extension.replaceAll(/[^A-Za-z0-9.]/, '').take(18)
                // Preserve the original file extension, not the worker's generic base name.
                filename = prefix + extension
                Attachment attachment = persistAttachment(filename, content, actor)
                part.addToCommentVersionAttachmentList(attachment)
            }
            // CAD_TO_META may contain only metadata.json/bom.json/assembly.json.
            // These are consumed by the metadata importer, not generic attachments.
            // Zero generated attachments is a valid successful result.
            if (!attached) log.info('No generic generated attachments for job ' + job.id)
            part.save(failOnError: true, flush: true)
        } finally {
            archive.close()
        }
    }


    /** Save the *resolved* assembly JSON after BOM PLM IDs have been assigned.
     * Re-running a metadata job replaces only the generated assembly JSON attachment
     * for this CAD version; unrelated attachments remain untouched.
     */
    @Transactional
    void saveResolvedAssemblyJson(Object job, PlmFreeCadPart part, String json) {
        if (!part || !json) throw new IllegalArgumentException('Missing assembly JSON or part')
        String cadVersion = "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"
        String partName = (part.label ?: part.originalName ?: 'part-' + part.id).toString()
            .replaceAll(/(?i)\.fcstd$/, '')
            .replaceAll(/[^A-Za-z0-9._-]+/, '_').take(100)
        String filename = partName + '_v' + cadVersion + '.json'
        User actor = job?.submittedBy ? User.findByUsername(job.submittedBy.toString()) : null
        if (!actor) actor = part.userCreated
        if (!actor) throw new IllegalStateException('No user available for assembly JSON attachment')
        Attachment generated = persistAttachment(filename, json.getBytes(StandardCharsets.UTF_8), actor)
        // Remove only an earlier generated JSON with the exact same name.
        def old = part.commentVersionAttachmentList?.findAll { Attachment a ->
            a.originalName == filename && a.id != generated.id
        } ?: []
        old.each { Attachment a -> part.removeFromCommentVersionAttachmentList(a) }
        if (!part.commentVersionAttachmentList?.any { Attachment a -> a.id == generated.id })
            part.addToCommentVersionAttachmentList(generated)
        part.save(failOnError:true, flush:true)
    }

    private static Long parsePartId(Object value) {
        if (value == null || !(value.toString() ==~ /[0-9]+/)) return null
        Long.valueOf(value.toString())
    }

    private Attachment persistAttachment(String filename, byte[] content, User actor) {
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
