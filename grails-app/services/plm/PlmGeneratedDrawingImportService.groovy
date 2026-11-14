package plm

/** Dedicated DRAWINGS route entry point. */
class PlmGeneratedDrawingImportService {
    PlmGeneratedAttachmentImportService plmGeneratedAttachmentImportService

    String importJobResult(Object job, java.nio.file.Path resultZip) {
        plmGeneratedAttachmentImportService.importDrawingJobResult(job, resultZip)
    }
}
