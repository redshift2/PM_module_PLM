package plm

import crew.User
import grails.gorm.transactions.Transactional

@Transactional
class PlmPartLifecycleService {

    PlmFreeCadSecurityService plmFreeCadSecurityService

    // ------------------------------------------------------------
    // Check out a WIP part
    // ------------------------------------------------------------

    PlmFreeCadPart checkOutPart(Long partId, User user) {

        PlmFreeCadPart part = PlmFreeCadPart.get(partId)

        if (part == null || part.active != true ||
                part.nextVersion != null ||
                !plmFreeCadSecurityService.canCheckOutPart(part, user)) {
            throw new IllegalStateException(
                    "Part cannot be checked out."
            )
        }

        part.lifecycleState = PlmPartLifecycleState.CHECKED_OUT
        part.checkedOutBy = user
        part.checkedOutAt = new Date()

        part.save(flush: true, failOnError: true)

        return part
    }

    // ------------------------------------------------------------
    // Cancel checkout without changing version
    // ------------------------------------------------------------

    PlmFreeCadPart cancelCheckOut(Long partId, User user) {

        PlmFreeCadPart part = PlmFreeCadPart.get(partId)

        if (part == null || part.active != true ||
                part.nextVersion != null ||
                !plmFreeCadSecurityService.canUploadPart(part, user)) {
            throw new IllegalStateException(
                    "Checkout cannot be cancelled."
            )
        }

        part.lifecycleState = PlmPartLifecycleState.WIP
        part.checkedOutBy = null
        part.checkedOutAt = null

        part.save(flush: true, failOnError: true)

        return part
    }

    // ------------------------------------------------------------
    // Release a WIP part without changing version
    // ------------------------------------------------------------

    PlmFreeCadPart releasePart(Long partId, User user) {

        PlmFreeCadPart part = PlmFreeCadPart.get(partId)

        if (part == null || part.active != true ||
                part.nextVersion != null ||
                !plmFreeCadSecurityService.canReleasePart(part, user)) {
            throw new IllegalStateException(
                    "Part cannot be released."
            )
        }

        part.lifecycleState = PlmPartLifecycleState.RELEASED
        part.checkedOutBy = null
        part.checkedOutAt = null
        part.releasedBy = user
        part.releasedAt = new Date()

        part.save(flush: true, failOnError: true)

        return part
    }

    // ------------------------------------------------------------
    // Create new major revision from a RELEASED part
    //
    // Example:
    // 1.2 RELEASED -> 2.0 WIP
    // ------------------------------------------------------------

    PlmFreeCadPart createRevision(Long partId, User user) {

        PlmFreeCadPart part = PlmFreeCadPart.get(partId)

        if (part == null || part.active != true ||
                part.nextVersion != null ||
                !plmFreeCadSecurityService.canCreateRevision(part, user)) {
            throw new IllegalStateException(
                    "Only an active RELEASED part can be revised."
            )
        }

        // Preserve the released revision in history.
        PlmFreeCadPart oldPart = part.cloneDirectObjectData()

        if (oldPart == null) {
            throw new IllegalStateException(
                    "Unable to create revision history."
            )
        }

        // Preserve the existing assembly links in the snapshot.
        part.plmLinks?.each { PlmFreeCadLink link ->
            oldPart.addToPlmLinks(link)
        }

        oldPart.userUpdated = user
        oldPart.save(flush: true, failOnError: true)

        // Advance the active part to the next major revision.
        part.majorVersion = (part.majorVersion ?: 1) + 1
        part.minorVersion = 0

        part.lifecycleState = PlmPartLifecycleState.WIP

        part.checkedOutBy = null
        part.checkedOutAt = null

        part.releasedBy = null
        part.releasedAt = null

        part.userUpdated = user
        part.save(flush: true, failOnError: true)

        return part
    }
}