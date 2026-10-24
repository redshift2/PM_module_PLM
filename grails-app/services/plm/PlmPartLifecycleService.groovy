package plm

import crew.User
import grails.gorm.transactions.Transactional

@Transactional
class PlmPartLifecycleService {

    PlmFreeCadSecurityService plmFreeCadSecurityService

    // Check out a WIP part, or create and check out a new major
    // revision when the current part is RELEASED.
    PlmFreeCadPart checkOutPart(Long partId, User user) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)

        if (user == null || part == null || part.active != true ||
                part.nextVersion != null) {
            throw new IllegalStateException('Part cannot be checked out.')
        }

        if (part.lifecycleState == PlmPartLifecycleState.RELEASED) {
            // Released parts require revision permission, not WIP checkout permission.
            if (!plmFreeCadSecurityService.canCreateRevision(part, user)) {
                throw new IllegalStateException(
                    'You do not have permission to revise this RELEASED part.'
                )
            }
            // Same transaction: if checkout fails, revision creation rolls back.
            part = createRevision(partId, user)
        } else if (!plmFreeCadSecurityService.canCheckOutPart(part, user)) {
            throw new IllegalStateException('Part cannot be checked out.')
        }

        part.lifecycleState = PlmPartLifecycleState.CHECKED_OUT
        part.checkedOutBy = user
        part.checkedOutAt = new Date()
        part.userUpdated = user
        part.save(flush: true, failOnError: true)
        return part
    }

    // Cancel checkout without changing version.
    PlmFreeCadPart cancelCheckOut(Long partId, User user) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (part == null || part.active != true ||
                part.nextVersion != null ||
                !plmFreeCadSecurityService.canUploadPart(part, user)) {
            throw new IllegalStateException('Checkout cannot be cancelled.')
        }
        part.lifecycleState = PlmPartLifecycleState.WIP
        part.checkedOutBy = null
        part.checkedOutAt = null
        part.save(flush: true, failOnError: true)
        return part
    }

    // Release a WIP part without changing version.
    PlmFreeCadPart releasePart(Long partId, User user) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (part == null || part.active != true ||
                part.nextVersion != null ||
                !plmFreeCadSecurityService.canReleasePart(part, user)) {
            throw new IllegalStateException('Part cannot be released.')
        }
        part.lifecycleState = PlmPartLifecycleState.RELEASED
        part.checkedOutBy = null
        part.checkedOutAt = null
        part.releasedBy = user
        part.releasedAt = new Date()
        part.save(flush: true, failOnError: true)
        return part
    }

    // Create a new major revision from a RELEASED part.
    // Example: 1.2 RELEASED -> 2.0 WIP.
    // The checkOutPart method then transitions it to CHECKED_OUT.
    PlmFreeCadPart createRevision(Long partId, User user) {
        PlmFreeCadPart part = PlmFreeCadPart.get(partId)
        if (part == null || part.active != true ||
                part.nextVersion != null ||
                part.lifecycleState != PlmPartLifecycleState.RELEASED ||
                !plmFreeCadSecurityService.canCreateRevision(part, user)) {
            throw new IllegalStateException(
                'Only an active RELEASED part can be revised.'
            )
        }

        PlmFreeCadPart oldPart = part.cloneDirectObjectData()
        if (oldPart == null) {
            throw new IllegalStateException('Unable to create revision history.')
        }

        // Snapshot links independently; do not reparent the live links.
        oldPart.userUpdated = user
        oldPart.save(flush: true, failOnError: true)
        if (part.plmLinks != null) {
            for (PlmFreeCadLink link : part.plmLinks) {
                new PlmFreeCadLink(
                    parentPart: oldPart,
                    part: link.part,
                    partLinkVersion: link.partLinkVersion,
                    linkedObject: link.linkedObject,
                    linkClaimChild: link.linkClaimChild,
                    linkTransform: link.linkTransform,
                    linkCopyOnChange: link.linkCopyOnChange,
                    userCreated: link.userCreated ?: user,
                    userUpdated: user
                ).save(flush: true, failOnError: true)
            }
        }

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