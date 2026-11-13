package plm

import attachment.Attachment
import grails.gorm.transactions.Transactional

/** An immutable attachment association to one exact engineering CAD version.
 * Lifecycle is deliberately derived from the version's part snapshot.
 */
class PlmCadVersionFile {
    PlmFreeCadPart part
    Attachment attachment
    Integer cadMajorVersion
    Integer cadMinorVersion
    String fileType // DRAWING, GLB, ASSEMBLY, CAD
    String originalName
    String contentShaOne
    Date dateCreated
    String generatedByJobId

    static constraints = {
        part nullable: false
        attachment nullable: false
        cadMajorVersion min: 1
        cadMinorVersion min: 0
        fileType inList: ['CAD', 'DRAWING', 'GLB', 'ASSEMBLY']
        originalName blank: false, maxSize: 512
        contentShaOne nullable: true, maxSize: 40
        generatedByJobId nullable: true, maxSize: 128
    }

    static mapping = {
        version false
        table 'plm_cad_version_file'
        part column: 'part_id'
        attachment column: 'attachment_id'
        cadMajorVersion column: 'cad_major_version'
        cadMinorVersion column: 'cad_minor_version'
        fileType column: 'file_type'
        originalName column: 'original_name'
        contentShaOne column: 'content_sha_one'
        generatedByJobId column: 'generated_by_job_id'
        dateCreated column: 'date_created'
    }
}
