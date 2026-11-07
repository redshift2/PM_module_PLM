package plm

import crew.User
import grails.gorm.transactions.Transactional

@Transactional
class PlmPartLifecycleService {

    PlmFreeCadSecurityService plmFreeCadSecurityService
    PlmEventBridgeService plmEventBridgeService
    def grailsApplication

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
        plmEventBridgeService.report('PART_CHECK_OUT', part, user?.username,
            [cadVersion: "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"])
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
        plmEventBridgeService.report('PART_RELEASE', part, user?.username,
            [cadVersion: "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"])
        // Queue only after the release commits. A conversion failure must never
        // roll back a successful PLM release.
        final Long releasedId = part.id
        final String releasedVersion = "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"
        final String releasedSha = part.plmContentShaOne
        final String actor = user?.username ?: 'plm'
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override void afterCommit() {
                    try {
                        grailsApplication.mainContext.getBean('plmCadConversionService')
                            .enqueueReleasedPart(releasedId, releasedVersion, releasedSha, actor)
                    } catch (Exception ex) {
                        log.error("PLM conversion enqueue failed for part ${releasedId} version ${releasedVersion}", ex)
                    }
                }
            }
        )
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

        // cloneDirectObjectData() derives creationOrder from computedVersion,
        // but computedVersion is the Hibernate row version, NOT the history index.
        // Allocate a history index unique within this part's version chain.
        List<PlmFreeCadPart> snapshots = PlmFreeCadPart.findAllByNextVersion(part)
        long nextOrder = 1L
        for (PlmFreeCadPart snapshot : snapshots) {
            if (snapshot.creationOrder != null && snapshot.creationOrder < Long.MAX_VALUE) {
                nextOrder = Math.max(nextOrder, snapshot.creationOrder + 1L)
            }
        }
        oldPart.creationOrder = nextOrder

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