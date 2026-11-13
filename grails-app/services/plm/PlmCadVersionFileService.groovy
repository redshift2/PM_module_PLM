package plm

import attachment.Attachment
import grails.gorm.transactions.Transactional

/** Version-specific file registry. No independent drawing release/version state. */
@Transactional
class PlmCadVersionFileService {
    static final Set<String> TYPES = ['CAD', 'DRAWING', 'GLB', 'ASSEMBLY'] as Set

    /** Register an already-stored Attachment without overwriting any prior association.
     * Only current WIP/checked-out engineering versions accept ordinary changes.
     * Released GLB repair is deliberately NOT implemented by this method.
     */
    PlmCadVersionFile register(Long partId, Long attachmentId, String fileType,
                               String generatedByJobId = null) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        Attachment attachment = Attachment.get(attachmentId)
        if (!part || !attachment) throw new IllegalArgumentException('Unknown part or attachment')
        if (!TYPES.contains(fileType)) throw new IllegalArgumentException('Unsupported file type')
        if (part.active != true || part.nextVersion != null)
            throw new IllegalStateException('Cannot modify a historical part version')
        if (part.lifecycleState == PlmPartLifecycleState.RELEASED)
            throw new IllegalStateException('Released engineering files are immutable')
        if (!attachment.originalName) throw new IllegalArgumentException('Attachment has no filename')
        Integer major = part.majorVersion ?: 1
        Integer minor = part.minorVersion ?: 0
        PlmCadVersionFile existing = PlmCadVersionFile.findByPartAndAttachmentAndCadMajorVersionAndCadMinorVersionAndFileType(
            part, attachment, major, minor, fileType)
        if (fileType == 'ASSEMBLY') {
            // A metadata refresh supersedes the previous ASMJ for this exact
            // CAD version. Keep the Attachment objects and their stored bytes.
            List<PlmCadVersionFile> previous =
                PlmCadVersionFile.findAllByPartAndCadMajorVersionAndCadMinorVersionAndFileType(
                    part, major, minor, 'ASSEMBLY')
            if (existing && previous.size() == 1) {
                log.info('ASMJ registry unchanged part={} version={}.{} registry={} attachment={} sha1={}',
                    part.id, major, minor, existing.id, attachment.id, attachment.contentShaOne)
                return existing
            }
            previous.each { PlmCadVersionFile old ->
                if (old.id != existing?.id) old.delete(flush: true)
            }
            if (existing) {
                log.info('ASMJ registry deduplicated part={} version={}.{} registry={} attachment={} removed={}',
                    part.id, major, minor, existing.id, attachment.id, previous.size() - 1)
                return existing
            }
        } else if (existing) return existing
        PlmCadVersionFile registered = new PlmCadVersionFile(part: part, attachment: attachment,
            cadMajorVersion: major, cadMinorVersion: minor,
            fileType: fileType, originalName: attachment.originalName,
            contentShaOne: attachment.contentShaOne,
            generatedByJobId: generatedByJobId
        ).save(failOnError: true, flush: true)
        if (fileType == 'ASSEMBLY')
            log.info('ASMJ registry current part={} version={}.{} registry={} attachment={} sha1={} job={}',
                part.id, major, minor, registered.id, attachment.id, attachment.contentShaOne, generatedByJobId)
        registered
    }

    /** Read files for a specific CAD version, including historical versions. */
    @Transactional(readOnly = true)
    List<PlmCadVersionFile> listForVersion(Long partId, Integer major, Integer minor,
                                            String fileType = null) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (!part || major == null || minor == null) return []
        if (fileType != null && !TYPES.contains(fileType))
            throw new IllegalArgumentException('Unsupported file type')
        if (fileType) return PlmCadVersionFile.findAllByPartAndCadMajorVersionAndCadMinorVersionAndFileType(
            part, major, minor, fileType, [sort: 'dateCreated', order: 'desc'])
        PlmCadVersionFile.findAllByPartAndCadMajorVersionAndCadMinorVersion(
            part, major, minor, [sort: 'dateCreated', order: 'desc'])
    }
}
