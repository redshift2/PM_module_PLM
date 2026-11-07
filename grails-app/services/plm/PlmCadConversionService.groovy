package plm

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import taack.ui.TaackUiConfiguration
import java.nio.file.*
import java.security.MessageDigest
import java.util.zip.*

/** Bridges released CAD revisions to the existing remote-worker queue. */
class PlmCadConversionService {
    // Resolve Server Administration dynamically: PLM is compiled as a separate Gradle module.
    def grailsApplication

    private Object jobService() {
        grailsApplication.mainContext.getBean('serverJobService')
    }
    PlmPreviewStorageService plmPreviewStorageService

    // Do not wrap job submission and package assignment in an outer transaction.
    // Each ServerJobService call must commit independently for worker polling.
    void enqueueReleasedPart(Long partId, String version, String sha, String actor) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part || part.lifecycleState != PlmPartLifecycleState.RELEASED ||
                "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}" != version ||
                part.plmContentShaOne != sha || !part.plmFilePath) {
            throw new IllegalStateException('Released CAD revision no longer matches the enqueue request')
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

    /** Called after a worker result ZIP is stored; validates revision before accepting GLB. */
    @Transactional
    void importWorkerResult(Object job, Path resultZip) {
        if (job.jobType != 'CAD_TO_GLB') return
        Map metadata = (Map)new JsonSlurper().parseText(job.parametersJson ?: '{}')
        Long partId = Long.valueOf(metadata.partId.toString())
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part || part.lifecycleState != PlmPartLifecycleState.RELEASED ||
            "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}" != metadata.cadVersion ||
            part.plmContentShaOne != metadata.cadSha1)
            throw new IllegalStateException('CAD result revision does not match released part')
        ZipFile zip = new ZipFile(resultZip.toFile())
        try {
            ZipEntry entry = zip.getEntry('model.glb')
            if (!entry || entry.directory || entry.size < 12 || entry.size > 250L*1024L*1024L)
                throw new IllegalArgumentException('Worker result does not contain valid model.glb')
            zip.getInputStream(entry).withCloseable { stream ->
                plmPreviewStorageService.store(part, stream, entry.size, 'model.glb')
            }
        } finally { zip.close() }
    }
}
