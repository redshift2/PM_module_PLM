package plm

import grails.compiler.GrailsCompileStatic
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured

import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartRequest

import java.nio.file.Files
import java.util.zip.ZipFile

@GrailsCompileStatic
@Secured(["ROLE_PLM_USER", "ROLE_ADMIN"])
class PlmProtoController {

    static scope = "session"

    PlmFreeCadProtoService plmFreeCadProtoService

    // ------------------------------------------------------------
    // Upload protobuf metadata
    // ------------------------------------------------------------

    @Transactional
    def uploadProto() {

        MultipartFile proto =
                (request as MultipartRequest).getFile('proto.bin')

        if (proto == null || proto.isEmpty()) {
            response.status = 400
            render "Missing proto.bin"
            return
        }

        File zipProto = Files.createTempFile(
                "plm-proto-",
                ".zip"
        ).toFile()

        try {

            proto.transferTo(zipProto)

            byte[] result

            try (ZipFile zipFile = new ZipFile(zipProto)) {
                result = plmFreeCadProtoService
                        .processProto(zipFile)
                        .toByteArray()
            }

            response.status = 200
            response.contentType = 'application/octet-stream'
            response.outputStream.write(result)
            response.outputStream.flush()

        } catch (IllegalStateException e) {
            transactionStatus.setRollbackOnly()
            log.warn "PLM protobuf upload rejected: ${e.message}"

            plmFreeCadProtoService.incomingBucket = null

            response.status = 409
            render "PLM_CONFLICT: ${e.message}"

        } catch (Exception e) {
            transactionStatus.setRollbackOnly()
            log.error("PLM protobuf upload failed", e)

            plmFreeCadProtoService.incomingBucket = null

            response.status = 500
            render "PLM_UPLOAD_ERROR"

        } finally {

            if (zipProto.exists()) {
                zipProto.delete()
            }
        }
    }


    // ------------------------------------------------------------
    // Upload FreeCAD files
    // ------------------------------------------------------------

    @Transactional
    def uploadZip() {

        MultipartFile proto =
                (request as MultipartRequest).getFile('proto.bin')

        if (proto == null || proto.isEmpty()) {
            response.status = 400
            render "Missing proto.bin"
            return
        }

        File zipProto = Files.createTempFile(
                "plm-upload-",
                ".zip"
        ).toFile()

        try {

            proto.transferTo(zipProto)

            byte[] result

            try (ZipFile zipFile = new ZipFile(zipProto)) {
                result = plmFreeCadProtoService
                        .processZip(zipFile)
                        .toByteArray()
            }

            response.status = 200
            response.contentType = 'application/octet-stream'
            response.outputStream.write(result)
            response.outputStream.flush()

        } catch (IllegalStateException e) {
            transactionStatus.setRollbackOnly()
            log.warn "PLM file upload rejected: ${e.message}"

            plmFreeCadProtoService.incomingBucket = null

            response.status = 409
            render "PLM_CONFLICT: ${e.message}"

        } catch (Exception e) {
            transactionStatus.setRollbackOnly()
            log.error("PLM file upload failed", e)

            plmFreeCadProtoService.incomingBucket = null

            response.status = 500
            render "PLM_UPLOAD_ERROR"

        } finally {

            if (zipProto.exists()) {
                zipProto.delete()
            }
        }
    }


    // ------------------------------------------------------------
    // Reset upload session
    // ------------------------------------------------------------

    def reset() {

        plmFreeCadProtoService.incomingBucket = null

        render "OK"
    }
}