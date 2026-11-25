package plm

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import taack.ui.TaackUiConfiguration
import java.nio.file.*
import java.security.MessageDigest
import java.util.zip.*

/** Packages CAD revisions to the existing remote-worker queue. */
class PlmCadConversionService {
    // Resolve Server Administration dynamically: PLM is compiled as a separate Gradle module.
    def grailsApplication

    private Object jobService() {
        grailsApplication.mainContext.getBean('serverJobService')
    }
    PlmPreviewStorageService plmPreviewStorageService

    // Do not wrap job submission and package assignment in an outer transaction.
    // Each ServerJobService call must commit independently for worker polling.
    void enqueuePartConversion(Long partId, String version, String sha, String actor) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part || "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}" != version ||
                part.plmContentShaOne != sha || !part.plmFilePath) {
            throw new IllegalStateException('CAD revision no longer matches the enqueue request')
        }
        if (!(part.plmFilePath ==~ /[a-fA-F0-9]{40}\.[a-zA-Z0-9]+/))
            throw new IllegalStateException('Invalid stored CAD filename')
        Path cad = Paths.get(TaackUiConfiguration.root, 'plmFreecad', 'model', part.plmFilePath)
        if (!Files.isRegularFile(cad)) throw new IllegalStateException('CAD file missing: ' + cad)
        String actualSha = MessageDigest.getInstance('SHA-1').digest(Files.readAllBytes(cad)).encodeHex().toString()
        if (!actualSha.equalsIgnoreCase(sha)) throw new IllegalStateException('CAD content SHA-1 mismatch')
        String root = System.getenv('SERVER_ADMIN_JOB_STORAGE')
        if (!root) throw new IllegalStateException('SERVER_ADMIN_JOB_STORAGE not configured')
        Path dir = Paths.get(root).toAbsolutePath().normalize()
        Files.createDirectories(dir)
        Map manifest = [jobType:'CAD_TO_GLB', partId:partId, cadVersion:version, cadSha1:sha,
                        inputFile:'part.FCStd', outputFormat:'glb']
        // Existing submit API accepts a map; it performs the database write transactionally.
        def job = jobService().submit('CAD_TO_GLB', actor, manifest)
        Path tmp = Files.createTempFile(dir, 'plm-cad-', '.tmp')
        try {
            ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))
            try {
                out.putNextEntry(new ZipEntry('manifest.json'))
                out.write(JsonOutput.toJson(manifest).getBytes('UTF-8'))
                out.closeEntry()
                out.putNextEntry(new ZipEntry('part.FCStd'))
                Files.copy(cad, out)
                out.closeEntry()
            } finally { out.close() }
            String filename = 'job-' + job.id + '.zip'
            Files.move(tmp, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING)
            jobService().setPackageFileName(job.id, filename)
            log.info("CAD_TO_GLB job ${job.id} package assigned: ${filename}")
        } finally { Files.deleteIfExists(tmp) }
    }

    PlmGeneratedAttachmentImportService plmGeneratedAttachmentImportService
    PlmCadVersionFileService plmCadVersionFileService

    /** Import event-generated CAD representations without filtering on job type. */
    @Transactional
    String importWorkerResult(Object job, Path resultZip) {
        if (!job || !Files.isRegularFile(resultZip))
            throw new IllegalArgumentException('Missing CAD History result ZIP')
        Map metadata = (Map)new JsonSlurper().parseText(job.parametersJson ?: '{}')
        if (!metadata.partId || !metadata.cadVersion || metadata.eventId == null)
            throw new IllegalArgumentException('CAD History routing requires event part, version and event ID')
        String version = metadata.cadVersion.toString()
        if (!(version ==~ /[0-9]+\.[0-9]+/)) throw new IllegalArgumentException('Invalid event CAD version')
        List<Integer> numbers = version.split(/\./).collect { Integer.valueOf(it) }
        Long partId = Long.valueOf(metadata.partId.toString())
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part) throw new IllegalArgumentException('Event PLM part not found: ' + partId)
        def actor = job.submittedBy ? crew.User.findByUsername(job.submittedBy.toString()) : null
        if (!actor) actor = part.userCreated
        if (!actor) throw new IllegalStateException('No owner for generated CAD files')
        int count = 0
        long total = 0L
        ZipFile archive = new ZipFile(resultZip.toFile())
        try {
            def entries = archive.entries()
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement()
                if (entry.directory) continue
                String name = entry.name
                if (!name || name.startsWith('/') || name.startsWith('\\') ||
                    name.contains('\\') || name.split('/').contains('..'))
                    throw new IllegalArgumentException('Unsafe result path: ' + name)
                String filename = name.tokenize('/').last()
                String lower = filename.toLowerCase(java.util.Locale.ROOT)
                // Do not attach job metadata, logs, or reports as CAD models.
                if (lower in ['manifest.json', 'conversion.json', 'drawing_result.json',
                              'assembly_report.json', 'execution.log', 'step_import_result.json']) continue
                if (!(lower ==~ /.*\.[a-z0-9][a-z0-9_+-]{0,15}/)) continue
                String ext = lower.substring(lower.lastIndexOf('.') + 1)
                if (ext in ['json', 'log', 'txt', 'csv', 'png', 'jpg', 'jpeg', 'pdf', 'svg', 'xml', 'html', 'py']) continue
                if (++count > 100) throw new IllegalArgumentException('Too many CAD result files')
                if (entry.size > 250L * 1024 * 1024) throw new IllegalArgumentException('CAD result file too large: ' + filename)
                byte[] bytes = archive.getInputStream(entry).withCloseable { it.bytes }
                total += bytes.length
                if (!bytes.length || total > 500L * 1024 * 1024)
                    throw new IllegalArgumentException('Empty or oversized CAD result ZIP')
                def attachment = plmGeneratedAttachmentImportService.persistAttachment(filename, bytes, actor)
                plmCadVersionFileService.registerEventOutput(partId, attachment.id as Long,
                    ext == 'glb' ? 'GLB' : 'EXCHANGE', job.id?.toString(), numbers[0], numbers[1])
                // Preserve existing GLB preview for the current revision only.
                if (ext == 'glb' && version == "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}" &&
                    (metadata.cadSha1 == null || metadata.cadSha1.toString().equalsIgnoreCase(part.plmContentShaOne ?: ''))) {
                    new ByteArrayInputStream(bytes).withCloseable { stream ->
                        plmPreviewStorageService.store(part, stream, bytes.length, filename)
                    }
                }
            }
        } finally { archive.close() }
        if (!count) throw new IllegalArgumentException('CAD History result ZIP contains no model files')
        return "Imported ${count} CAD History file(s) for part ${partId} v${version}"
    }
}
