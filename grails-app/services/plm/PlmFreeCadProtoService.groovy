package plm

import attachment.DocumentAccess
import attachment.DocumentCategory
import attachment.config.DocumentCategoryEnum
import crew.User
import grails.compiler.GrailsCompileStatic
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import plm.freecad.FreecadPlm
import plm.freecad.FreecadPlm.PlmFile
import taack.ui.TaackUiConfiguration

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@GrailsCompileStatic
class PlmFreeCadProtoService {

    SpringSecurityService springSecurityService
    PlmFreeCadSecurityService plmFreeCadSecurityService

    private final String intranetRoot = TaackUiConfiguration.root

    String getStorePath() { intranetRoot + '/plmFreecad/model' }
    String getPreviewPath() { intranetRoot + '/plmFreecad/preview' }

    // Existing two-request protocol: uploadProto -> uploadZip.
    // NOTE: this field is shared if this Grails service is a singleton.
    // See deployment note below about concurrent uploads.
    FreecadPlm.Bucket incomingBucket = null

    FreecadPlm.Bucket processProto(ZipFile zipFile) {
        FreecadPlm.Bucket.Builder outbound = FreecadPlm.Bucket.newBuilder()
        outbound.setStatus(FreecadPlm.ServerStatus.NOK_PROTO)

        ZipEntry protoEntry = zipFile.getEntry('proto.bin')
        if (protoEntry == null) {
            throw new IllegalStateException('Missing proto.bin in protobuf ZIP.')
        }

        FreecadPlm.Bucket parsed
        InputStream input = zipFile.getInputStream(protoEntry)
        try {
            parsed = FreecadPlm.Bucket.parseFrom(input)
        } finally {
            input.close()
        }

        // This step only negotiates which content hashes already exist.
        // It must not modify part metadata or lifecycle state.
        for (PlmFile plmFile : parsed.plmFilesMap.values()) {
            String sha1 = plmFile.sha1Hex
            if (sha1 && PlmFreeCadPart.findByPlmContentShaOne(sha1) != null) {
                outbound.addServerSha1Files(sha1)
            }
        }

        incomingBucket = parsed
        outbound.setStatus(FreecadPlm.ServerStatus.OK_PROTO)
        return outbound.build()
    }

    private static String sha1Of(byte[] bytes) {
        return MessageDigest.getInstance('SHA-1').digest(bytes).encodeHex().toString()
    }

    private static byte[] readEntry(ZipFile zipFile, ZipEntry entry) {
        InputStream input = zipFile.getInputStream(entry)
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream()
            byte[] buffer = new byte[8192]
            int count
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            input.close()
        }
    }

    private void validateUpload(PlmFreeCadPart part, User user) {
        if (user == null) {
            throw new IllegalStateException('No authenticated PLM user.')
        }
        if (part == null) return // New CAD part.
        if (part.active != true || part.nextVersion != null) {
            throw new IllegalStateException('Only the current active part can be uploaded.')
        }
        if (part.lifecycleState == PlmPartLifecycleState.RELEASED) {
            throw new IllegalStateException(
                "Part ${part.originalName} is RELEASED. Create a new revision before modifying its CAD content."
            )
        }
        if (!plmFreeCadSecurityService.canUploadPart(part, user)) {
            throw new IllegalStateException(
                "Part ${part.originalName} must be checked out by the current user before uploading modified CAD content."
            )
        }
    }

    private static PlmFreeCadLink snapshotLink(PlmFreeCadLink source,
                                                PlmFreeCadPart historicalParent,
                                                User user) {
        return new PlmFreeCadLink(
            parentPart: historicalParent,
            part: source.part,
            partLinkVersion: source.partLinkVersion,
            linkedObject: source.linkedObject,
            linkClaimChild: source.linkClaimChild,
            linkTransform: source.linkTransform,
            linkCopyOnChange: source.linkCopyOnChange,
            userCreated: source.userCreated ?: user,
            userUpdated: user
        )
    }

    private static void writeContentOnce(Path target, byte[] bytes) {
        Files.createDirectories(target.parent)
        if (Files.exists(target)) {
            // Never append to content-addressed files. Verify existing data.
            if (sha1Of(Files.readAllBytes(target)) != sha1Of(bytes)) {
                throw new IllegalStateException("Stored CAD content hash mismatch: ${target.fileName}")
            }
            return
        }
        Path temporary = Files.createTempFile(target.parent, 'plm-', '.tmp')
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Transactional
    FreecadPlm.Bucket processZip(ZipFile zipFile) {
        FreecadPlm.Bucket bucket = incomingBucket
        if (bucket == null) {
            throw new IllegalStateException('Upload metadata is missing; call uploadProto first.')
        }
        User user = springSecurityService.currentUser as User
        if (user == null) throw new IllegalStateException('No authenticated PLM user.')

        FreecadPlm.Bucket.Builder outbound = FreecadPlm.Bucket.newBuilder()
        outbound.setStatus(FreecadPlm.ServerStatus.NOK_FILES)

        // Preflight ALL parts before changing database records.
        Map<String, PlmFreeCadPart> existingByName = [:]
        Map<String, Boolean> changedByName = [:]
        Map<String, byte[]> contentByName = [:]
        Map<String, String> shaByName = [:]
        Map<String, String> extensionByName = [:]

        for (Map.Entry<String, PlmFile> item : bucket.plmFilesMap.entrySet()) {
            PlmFile plmFile = item.value
            if (!plmFile.id || plmFile.id.isBlank()) {
                throw new IllegalStateException("Missing fileId for ${plmFile.name}.")
            }
            if (!plmFile.fileName || plmFile.fileName.contains('"')) {
                throw new IllegalStateException("Invalid filename for ${plmFile.name}.")
            }
            String extension = plmFile.fileName.contains('.') ?
                plmFile.fileName.substring(plmFile.fileName.lastIndexOf('.') + 1) : 'bin'
            if (!(extension ==~ /[A-Za-z0-9]+/)) {
                throw new IllegalStateException("Invalid extension for ${plmFile.fileName}.")
            }

            PlmFreeCadPart part = PlmFreeCadPart.findByFileIdAndNextVersionIsNull(plmFile.id)
            if (part != null && part.active != true) {
                throw new IllegalStateException("Inactive part cannot be uploaded: ${plmFile.id}")
            }
            if (part?.status == PlmFreeCadPartStatus.LOCKED) {
                throw new IllegalStateException("Legacy LOCKED part: ${plmFile.id}")
            }

            String declaredSha = plmFile.sha1Hex
            byte[] bytes = null
            if (!plmFile.fileContent.isEmpty()) {
                bytes = plmFile.fileContent.toByteArray()
            } else if (declaredSha) {
                ZipEntry entry = zipFile.getEntry(declaredSha)
                if (entry != null) bytes = readEntry(zipFile, entry)
            }

            String sha = bytes != null ? sha1Of(bytes) : declaredSha
            if (!sha || !(sha ==~ /[a-fA-F0-9]{40}/)) {
                throw new IllegalStateException("Invalid or missing SHA-1 for ${plmFile.name}.")
            }
            if (declaredSha && !sha.equalsIgnoreCase(declaredSha)) {
                throw new IllegalStateException("SHA-1 mismatch for ${plmFile.name}.")
            }
            sha = sha.toLowerCase(Locale.ROOT)

            boolean changed = part == null || part.plmContentShaOne == null ||
                !sha.equalsIgnoreCase(part.plmContentShaOne)
            if (changed) validateUpload(part, user)

            Path stored = Paths.get(storePath, sha + '.' + extension)
            if (bytes == null && !Files.exists(stored)) {
                // Content may already be stored with another extension.
                PlmFreeCadPart matching = PlmFreeCadPart.findByPlmContentShaOne(sha)
                if (matching?.plmFilePath) {
                    Path existingPath = Paths.get(storePath, matching.plmFilePath)
                    if (Files.exists(existingPath)) {
                        bytes = Files.readAllBytes(existingPath)
                    }
                }
            }
            if (bytes == null && !Files.exists(stored)) {
                throw new IllegalStateException("Missing CAD content for ${plmFile.name} (${sha}).")
            }
            if (bytes != null && sha1Of(bytes) != sha) {
                throw new IllegalStateException("Stored/source content mismatch for ${plmFile.name}.")
            }
            if (bytes == null && sha1Of(Files.readAllBytes(stored)) != sha) {
                throw new IllegalStateException("Stored content is corrupt for ${plmFile.name}.")
            }

            existingByName.put(item.key, part)
            changedByName.put(item.key, changed)
            contentByName.put(item.key, bytes)
            shaByName.put(item.key, sha)
            extensionByName.put(item.key, extension)
        }

        Map<String, PlmFreeCadPart> objNameToPart = [:]
        Map<String, List<PlmFreeCadPart>> partLinkedPartNameToParts = [:]
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
        dateFormat.setTimeZone(TimeZone.getTimeZone('UTC'))

        for (Map.Entry<String, PlmFile> item : bucket.plmFilesMap.entrySet()) {
            PlmFile plmFile = item.value
            PlmFreeCadPart part = existingByName.get(item.key)
            boolean changed = changedByName.get(item.key) == Boolean.TRUE
            String sha = shaByName.get(item.key)
            String extension = extensionByName.get(item.key)
            byte[] bytes = contentByName.get(item.key)

            if (changed) {
                if (part == null) {
                    part = new PlmFreeCadPart()
                    part.userCreated = user
                    part.documentCategory = new DocumentCategory(category: DocumentCategoryEnum.OTHER)
                    part.creationOrder = Long.MAX_VALUE
                    part.majorVersion = 1
                    part.minorVersion = 0
                    part.lifecycleState = PlmPartLifecycleState.WIP
                } else {
                    PlmFreeCadPart historical = part.cloneDirectObjectData()
                    if (historical == null) {
                        throw new IllegalStateException("Unable to snapshot ${plmFile.name}.")
                    }
                    // Allocate a unique history sequence within this active part's chain.
                    // cloneDirectObjectData() uses computedVersion, which can collide
                    // with history created during checkout of a released revision.
                    List<PlmFreeCadPart> snapshots = PlmFreeCadPart.findAllByNextVersion(part)
                    long nextOrder = 1L
                    for (PlmFreeCadPart snapshot : snapshots) {
                        if (snapshot.creationOrder != null && snapshot.creationOrder < Long.MAX_VALUE) {
                            nextOrder = Math.max(nextOrder, snapshot.creationOrder + 1L)
                        }
                    }
                    historical.creationOrder = nextOrder
                    historical.userUpdated = user
                    historical.save(flush: true, failOnError: true)
                    // Create independent historical links; never reparent live links.
                    if (part.plmLinks != null) {
                        for (PlmFreeCadLink link : part.plmLinks) {
                            snapshotLink(link, historical, user).save(flush: true, failOnError: true)
                        }
                    }
                    part.minorVersion = (part.minorVersion ?: 0) + 1
                    part.lifecycleState = PlmPartLifecycleState.WIP
                    part.checkedOutBy = null
                    part.checkedOutAt = null
                }

                Path target = Paths.get(storePath, sha + '.' + extension)
                if (bytes != null) writeContentOnce(target, bytes)
                part.plmFilePath = sha + '.' + extension
                part.plmContentShaOne = sha
                part.plmContentType = Files.probeContentType(target)
                part.plmFileLastUpdated = dateFormat.parse(plmFile.lastModifiedDate)
                part.plmFileDateCreated = dateFormat.parse(plmFile.createdDate)
                part.plmFileUserCreated = plmFile.createdBy
                part.plmFileUserUpdated = plmFile.lastModifiedBy
                part.cTimeNs = plmFile.getCTimeNs()
                part.mTimeNs = plmFile.getUTimeNs()
                part.documentAccess = DocumentAccess.findOrCreateByIsInternalAndIsRestrictedToMyBusinessUnitAndIsRestrictedToMySubsidiaryAndIsRestrictedToMyManagersAndIsRestrictedToEmbeddingObjects(false, false, false, false, true)
            }

            // Metadata changes are allowed on RELEASED parts and do not
            // change the CAD version or checkout state.
            part.pathOnHost = plmFile.fileName
            part.fileId = plmFile.id
            part.label = plmFile.label
            part.comment = plmFile.comment
            String basename = new File(plmFile.fileName).name
            part.originalName = basename.toLowerCase(Locale.ROOT).endsWith('.fcstd') ?
                basename.substring(0, basename.length() - 6) : basename
            part.userUpdated = user
            part.save(flush: true, failOnError: true)
            if (!plmFile.filePreview.isEmpty()) {
                Path previewTarget = Paths.get(previewPath, sha + '.png')
                Files.createDirectories(previewTarget.parent)
                if (!Files.exists(previewTarget)) {
                    Files.write(previewTarget, plmFile.filePreview.toByteArray())
                }
            }
            objNameToPart.put(plmFile.name, part)

            for (String externalName : plmFile.externalLinkList) {
                List<PlmFreeCadPart> parents = partLinkedPartNameToParts.get(externalName)
                if (parents == null) {
                    parents = []
                    partLinkedPartNameToParts.put(externalName, parents)
                }
                parents.add(part)
            }
        }

        for (Map.Entry<String, FreecadPlm.PlmLink> item : bucket.linksMap.entrySet()) {
            PlmFreeCadPart referenced = objNameToPart.get(item.key)
            if (referenced == null) {
                throw new IllegalStateException("Missing referenced part for link ${item.key}.")
            }
            List<PlmFreeCadPart> parents = partLinkedPartNameToParts.get(item.key)
            if (parents == null) continue
            for (PlmFreeCadPart parent : parents) {
                if (parent.id == referenced.id) {
                    throw new IllegalStateException("Cyclic self-link for ${item.key}.")
                }
                PlmFreeCadLink link = PlmFreeCadLink.findByPartAndParentPart(referenced, parent)
                boolean changedParent = changedByName.values().contains(true) &&
                    changedByName.get(parentNameFor(parent, objNameToPart)) == Boolean.TRUE
                if (link == null) {
                    // Adding an assembly link changes CAD structure.
                    if (parent.id != null && !changedParent) {
                        throw new IllegalStateException("New link requires checkout and changed CAD content for ${parent.originalName}.")
                    }
                    link = new PlmFreeCadLink(part: referenced,
                        partLinkVersion: referenced.computedVersion,
                        parentPart: parent, userCreated: user)
                } else if (!changedParent) {
                    // Unchanged CAD must not mutate link geometry or ownership.
                    continue
                }
                link.linkedObject = item.value.linkedObject
                link.userUpdated = user
                link.linkTransform = item.value.linkTransform
                link.linkClaimChild = item.value.linkClaimChild
                switch (item.value.linkCopyOnChange) {
                    case FreecadPlm.PlmLink.LinkCopyOnChangeEnum.Disabled:
                        link.linkCopyOnChange = PlmFreeCadLinkCopyOnChange.DISABLED
                        break
                    case FreecadPlm.PlmLink.LinkCopyOnChangeEnum.Enabled:
                        link.linkCopyOnChange = PlmFreeCadLinkCopyOnChange.ENABLED
                        break
                    case FreecadPlm.PlmLink.LinkCopyOnChangeEnum.Owned:
                        link.linkCopyOnChange = PlmFreeCadLinkCopyOnChange.OWNED
                        break
                    default:
                        throw new IllegalStateException("Unknown linkCopyOnChange for ${item.key}.")
                }
                link.save(flush: true, failOnError: true)
            }
        }

        incomingBucket = null
        outbound.setStatus(FreecadPlm.ServerStatus.OK_FILES)
        return outbound.build()
    }

    private static String parentNameFor(PlmFreeCadPart parent,
                                        Map<String, PlmFreeCadPart> objects) {
        for (Map.Entry<String, PlmFreeCadPart> item : objects.entrySet()) {
            if (item.value.is(parent)) return item.key
        }
        return ''
    }
}