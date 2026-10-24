package plm

import attachement.AttachmentSecurityService
import attachment.WriteAccess
import crew.User
import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import jakarta.annotation.PostConstruct
import org.codehaus.groovy.runtime.MethodClosure
import taack.app.TaackApp
import taack.app.TaackAppRegisterService
import taack.render.TaackUiEnablerService

@GrailsCompileStatic
class PlmFreeCadSecurityService {

    static lazyInit = false

    SpringSecurityService springSecurityService

    private securityCanDownloadClosure(Long id, Map p) {
        if (!id && !p) return true
        if (!id) return true
        canDownloadFile(PlmFreeCadPart.get(id), springSecurityService.currentUser as User)
    }

    private securityCanEditClosure(Long id, Map p) {
        if (!id && !p) return true
        if (!id) return true
        canEditFile(PlmFreeCadPart.get(id), springSecurityService.currentUser as User)
    }

    @PostConstruct
    void init() {
        TaackUiEnablerService.securityClosure(
                this.&securityCanDownloadClosure,
                PlmController.&downloadBinPart as MethodClosure,
                PlmController.&addComment as MethodClosure,
                PlmController.&previewPart as MethodClosure,
                PlmController.&editPart as MethodClosure,
                PlmController.&editPartCategory as MethodClosure,
                PlmController.&previewPart as MethodClosure,
                PlmController.&previewAsciidoc as MethodClosure,
                PlmController.&showPart as MethodClosure,
                PlmController.&saveComment as MethodClosure
        )
        TaackUiEnablerService.securityClosure(
                this.&securityCanEditClosure,
                PlmController.&editPart as MethodClosure,
                PlmController.&editPartCategory as MethodClosure,
                PlmController.&savePart as MethodClosure,
                PlmController.&addAttachment as MethodClosure,
                PlmController.&saveAttachment as MethodClosure,
                PlmController.&importAttachment as MethodClosure,
                PlmController.&savePartCategory as MethodClosure,
                PlmController.&saveComment as MethodClosure
        )

        TaackAppRegisterService.register(new TaackApp(PlmController.&parts as MethodClosure, new String(this.class.getResourceAsStream("/plm/plm.svg").readAllBytes())))
    }

    // General editing permissions.
    boolean canEditFile(PlmFreeCadPart plmDoc, User user) {
        if (plmDoc == null || user == null) return false

        if (plmDoc.lifecycleState == PlmPartLifecycleState.RELEASED) {
            return false
        }

        if (plmDoc.lifecycleState == PlmPartLifecycleState.CHECKED_OUT) {
            return plmDoc.checkedOutBy == null ||
                    plmDoc.checkedOutBy.id == user.id
        }

        return plmDoc.lifecycleState == PlmPartLifecycleState.WIP
    }

    // A released part may be checked out by creating a new major revision.
    boolean canCheckOutPart(PlmFreeCadPart part, User user) {
        if (part == null || user == null ||
                part.active != true || part.nextVersion != null) {
            return false
        }

        if (part.lifecycleState == PlmPartLifecycleState.RELEASED) {
            return canCreateRevision(part, user)
        }

        return part.lifecycleState == PlmPartLifecycleState.WIP &&
                canEditFile(part, user)
    }

    // Unowned checkouts may be uploaded by any authenticated user.
    boolean canUploadPart(PlmFreeCadPart part, User user) {
        if (part == null || user == null ||
                part.active != true || part.nextVersion != null) {
            return false
        }

        if (part.lifecycleState != PlmPartLifecycleState.CHECKED_OUT) {
            return false
        }

        return (part.checkedOutBy == null ||
                part.checkedOutBy.id == user.id) &&
                canEditFile(part, user)
    }

    boolean canReleasePart(PlmFreeCadPart part, User user) {
        if (part == null || user == null) return false

        return part.lifecycleState == PlmPartLifecycleState.WIP &&
                canEditFile(part, user)
    }

    boolean canCreateRevision(PlmFreeCadPart part, User user) {
        if (part == null || user == null ||
                part.active != true || part.nextVersion != null) {
            return false
        }

        return part.lifecycleState == PlmPartLifecycleState.RELEASED &&
                canDownloadFile(part, user)
    }

    boolean canDownloadFile(PlmFreeCadPart plmDoc) {
        canDownloadFile(plmDoc, springSecurityService.currentUser as User)
    }

    boolean canDownloadFile(PlmFreeCadPart plmDoc, User user) {
        if (plmDoc == null || user == null) return false
        if (plmDoc.nextVersion) plmDoc = plmDoc.nextVersion
        if (user == plmDoc.userCreated) return true
        if (!plmDoc.documentAccess) return true
        return AttachmentSecurityService.canDownloadFile(plmDoc.documentAccess, plmDoc.userCreated, user)
    }
}