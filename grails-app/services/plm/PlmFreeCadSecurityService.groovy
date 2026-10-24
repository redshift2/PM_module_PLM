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


    // ------------------------------------------------------------
    // General editing permissions
    // ------------------------------------------------------------

    boolean canEditFile(PlmFreeCadPart plmDoc, User user) {

        if (plmDoc == null || user == null) {
            return false
        }

        // Released parts cannot be modified.
        if (plmDoc.lifecycleState ==
                PlmPartLifecycleState.RELEASED) {
            return false
        }

        // A checked-out part can only be edited by
        // the user who owns the checkout.
        if (plmDoc.lifecycleState ==
                PlmPartLifecycleState.CHECKED_OUT) {

            return plmDoc.checkedOutBy != null &&
                    plmDoc.checkedOutBy.id == user.id
        }

        // WIP metadata can be edited.
        // File uploads still require checkout.
        return plmDoc.lifecycleState ==
                PlmPartLifecycleState.WIP
    }


    // ------------------------------------------------------------
    // Checkout permissions
    // ------------------------------------------------------------

    boolean canCheckOutPart(PlmFreeCadPart part, User user) {

        if (part == null || user == null) {
            return false
        }

        if (part.lifecycleState !=
                PlmPartLifecycleState.WIP) {
            return false
        }

        return canEditFile(part, user)
    }


    // ------------------------------------------------------------
    // File upload permissions
    // ------------------------------------------------------------

    boolean canUploadPart(PlmFreeCadPart part, User user) {

        if (part == null || user == null) {
            return false
        }

        if (part.lifecycleState !=
                PlmPartLifecycleState.CHECKED_OUT) {
            return false
        }

        if (part.checkedOutBy == null) {
            return false
        }

        return part.checkedOutBy.id == user.id &&
                canEditFile(part, user)
    }


    // ------------------------------------------------------------
    // Release permissions
    // ------------------------------------------------------------

    boolean canReleasePart(PlmFreeCadPart part, User user) {

        if (part == null || user == null) {
            return false
        }

        return part.lifecycleState ==
                PlmPartLifecycleState.WIP &&
                canEditFile(part, user)
    }


    // ------------------------------------------------------------
    // Create revision permissions
    // ------------------------------------------------------------

    boolean canCreateRevision(
            PlmFreeCadPart part,
            User user) {

        if (part == null || user == null) {
            return false
        }

        return part.lifecycleState ==
                PlmPartLifecycleState.RELEASED &&
                canDownloadFile(part, user)
    }

    boolean canDownloadFile(PlmFreeCadPart plmDoc) {
        canDownloadFile(plmDoc, springSecurityService.currentUser as User)
    }

    boolean canDownloadFile(PlmFreeCadPart plmDoc, User user) {
        if (plmDoc.nextVersion) plmDoc = plmDoc.nextVersion
        if (user == plmDoc.userCreated) return true
        if (!plmDoc.documentAccess) return true
        return AttachmentSecurityService.canDownloadFile(plmDoc.documentAccess, plmDoc.userCreated, user)
    }

}
