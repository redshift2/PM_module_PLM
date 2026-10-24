package plm

import attachment.Term
import crew.User
import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import org.springframework.beans.factory.annotation.Value
import grails.gorm.transactions.Transactional
import attachment.DocumentCategory

@GrailsCompileStatic
@Secured(["ROLE_PLM_USER", "ROLE_ADMIN"])
class PlmJsonController {
    // =========================================================
    // SERVER FEATURE CONFIGURATION
    // =========================================================

    static final boolean FEATURE_TAGS             = true
    static final boolean FEATURE_PARTS_BY_TAG     = true
    static final boolean FEATURE_WORKSPACE_PARTS  = true
    static final boolean FEATURE_SET_PART_TAGS    = true
    static final boolean FEATURE_SEARCH_PARTS     = true
    static final boolean FEATURE_CHECK_OUT_PART   = true
    static final boolean FEATURE_CANCEL_CHECK_OUT = true
    static final boolean FEATURE_RELEASE_PART     = true
    static final boolean FEATURE_CREATE_REVISION  = true
    static final boolean FEATURE_PART_HISTORY     = false

    // =========================================================
    // SERVICES
    // =========================================================


    PlmFreeCadSecurityService plmFreeCadSecurityService
    SpringSecurityService springSecurityService
    PlmPartLifecycleService plmPartLifecycleService

    @Value('${grails.controllers.upload.maxFileSize}')
    Long maximumFileUploadSize

    @Value('${grails.controllers.upload.maxRequestSize}')
    Long maximumRequestSize

    private List<Map<String, Object>> prepareParts(
            List<PlmFreeCadPart> parts,
            User user) {

        parts.grep { PlmFreeCadPart part ->
            plmFreeCadSecurityService.canDownloadFile(part, user)
        }.collect { PlmFreeCadPart part ->

            [
                    id            : part.id,
                    label         : part.label,
                    originalName  : part.originalName,

                    computedVersion: part.computedVersion,

                    majorVersion  : part.majorVersion,
                    minorVersion  : part.minorVersion,
                    version       : part.displayVersion,

                    lifecycleState: part.lifecycleState?.name(),

                    checkedOutBy  : part.checkedOutBy?.username,

                    canCheckOut   : plmFreeCadSecurityService
                            .canCheckOutPart(part, user),

                    canCheckIn    : plmFreeCadSecurityService
                            .canUploadPart(part, user),

                    canRelease    : plmFreeCadSecurityService
                            .canReleasePart(part, user),

                    canCreateRevision: plmFreeCadSecurityService
                            .canCreateRevision(part, user)

            ] as Map<String, Object>
        }
    }

    private String getServerBuildDate() {
        Properties properties = new Properties()

        InputStream input = this.class.classLoader
                .getResourceAsStream("build-info.properties")

        if (input) {
            try {
                properties.load(input)
            } finally {
                input.close()
            }
        }
        String buildDate = properties.getProperty("server.build.date", "unknown")

        if (buildDate != "unknown") {
            return buildDate.split(" ")[0]
        }

        return buildDate
    }



    // ---------------------------------------------------------
    // Endpoint: /plmJson/serverInfo
    //
    // Returns server information and supported features.
    // ---------------------------------------------------------

    def serverInfo() {

         // -----------------------------------------------------
        // Build list of enabled features
        // -----------------------------------------------------

        List<String> features = []

        if (FEATURE_TAGS) {
            features.add("tags")
        }

        if (FEATURE_PARTS_BY_TAG) {
            features.add("partsByTag")
        }

        if (FEATURE_WORKSPACE_PARTS) {
            features.add("workspaceParts")
        }

        if (FEATURE_SET_PART_TAGS) {
            features.add("setPartTags")
        }

        if (FEATURE_SEARCH_PARTS) {
            features.add("searchParts")
        }
    
        if (FEATURE_CHECK_OUT_PART) {
            features.add("checkOutPart")
        }

        if (FEATURE_CANCEL_CHECK_OUT) {
            features.add("cancelCheckOut")
        }

        if (FEATURE_RELEASE_PART) {
            features.add("releasePart")
        }

        if (FEATURE_CREATE_REVISION) {
            features.add("createRevision")
        }

        if (FEATURE_PART_HISTORY) {
            features.add("partHistory")
        }

        Map<String, Object> result = [
                serverBuild             : getServerBuildDate(),
                messagingProtocolVersion: "6",
                maximumFileUploadSize   : maximumFileUploadSize,
                maxRequestSize          : maximumRequestSize,
                features                : features
        ] as Map<String, Object>
        response.contentType = 'application/json'

        render result as JSON
    }


    //expose api for tags, endpoint /plmJson/tags
    def tags() {
        List<Term> termList = Term.list(
                sort: 'name',
                order: 'asc'
        ) as List<Term>

        List<Map<String, Object>> result = termList.collect { Term tag ->
            [
                    id    : tag.id,
                    name  : tag.name,
                    parent: tag.parent?.name
            ] as Map<String, Object>
        }

        response.contentType = 'application/json'
        render result as JSON
    }

    //Create end point  /plmJson/partsByTag?tagId=
    def partsByTag(Long tagId) {
        if (!tagId) {
            response.status = 400
            render([error: 'tagId is required'] as JSON)
            return
        }

        Term tag = Term.get(tagId)

        if (!tag) {
            response.status = 404
            render([
                    error: 'Tag not found',
                    tagId: tagId
            ] as JSON)
            return
        }

        List<PlmFreeCadPart> parts = PlmFreeCadPart.executeQuery(
                '''
            select distinct p
            from PlmFreeCadPart p
            where p.active = true
              and p.nextVersion is null
              and p.pathOnHost like '%FCStd'
              and exists (
                  select 1
                  from DocumentCategory dc
                  join dc.tags t
                  where dc.id = p.documentCategory.id
                    and t.id = :tagId
              )
            order by p.label
            ''',
                [tagId: tagId]
        ) as List<PlmFreeCadPart>

        response.contentType = 'application/json'
        render prepareParts(parts, springSecurityService.currentUser as User) as JSON
    }

    //create endpoint for getting status of PLM for parts, exposes /plmJson/workspaceParts
@Secured(["ROLE_PLM_USER", "ROLE_ADMIN"])
def workspaceParts() {

    Map<String, Object> requestJson =
            request.JSON as Map<String, Object>

    List<Map<String, Object>> workspaceParts =
            (requestJson.get("parts") ?: []) as List<Map<String, Object>>

    User user =
            springSecurityService.currentUser as User

    List<Map<String, Object>> resultParts = []

    workspaceParts.each { Map<String, Object> workspacePart ->

        String name =
                workspacePart.get("name") as String

        String relativePath =
                workspacePart.get("relativePath") as String

        if (!name) {
            return
        }

  
        String nameWithoutExtension = name.replaceFirst(
                '(?i)\\.fcstd$',
                ''
        )
      List<PlmFreeCadPart> matchingParts =
                PlmFreeCadPart.executeQuery(
                        '''
                        select p
                        from PlmFreeCadPart p
                        where (
                            lower(p.originalName) = lower(:originalName)
                            or lower(p.originalName) = lower(:nameWithoutExtension)
                        )
                          and p.active = true
                          and p.nextVersion is null
                          and lower(p.pathOnHost) like '%fcstd'
                        order by p.id desc
                        ''',
                        [
                                originalName       : name,
                                nameWithoutExtension: nameWithoutExtension
                        ],
                        [max: 1]
                ) as List<PlmFreeCadPart>

        PlmFreeCadPart plmPart =
                matchingParts ? matchingParts[0] : null

        Map<String, Object> resultPart = [
                name        : name,
                relativePath: relativePath
        ] as Map<String, Object>
        if (plmPart) {

            resultPart.put(
                    "existsInPlm",
                    true
            )

            resultPart.put(
                    "plmPartId",
                    plmPart.id
            )

            resultPart.put(
                    "latestVersion",
                    plmPart.computedVersion
            )
            resultPart.put(
                    "version",
                    plmPart.displayVersion
            )

            resultPart.put(
                    "majorVersion",
                    plmPart.majorVersion
            )

            resultPart.put(
                    "minorVersion",
                    plmPart.minorVersion
            )

            resultPart.put(
                    "lifecycleState",
                    plmPart.lifecycleState?.name()
            )

            resultPart.put(
                    "checkedOutBy",
                    plmPart.checkedOutBy?.username
            )

            resultPart.put(
                    "canCheckOut",
                    plmFreeCadSecurityService.canCheckOutPart(
                            plmPart, user)
            )

            resultPart.put(
                    "canCheckIn",
                    plmFreeCadSecurityService.canUploadPart(
                            plmPart, user)
            )

            resultPart.put(
                    "canRelease",
                    plmFreeCadSecurityService.canReleasePart(
                            plmPart, user)
            )

            resultPart.put(
                    "canCreateRevision",
                    plmFreeCadSecurityService.canCreateRevision(
                            plmPart, user)
            )

        resultPart.put(
                "plmStatus",
                plmPart.status?.toString()
        )
        // -----------------------------------------------------
        // Retrieve tags assigned to this PLM part
        // -----------------------------------------------------

        List<String> tagNames = []

        if (plmPart.documentCategory != null) {

            tagNames = Term.executeQuery(
                    '''
                    select distinct t.name
                    from DocumentCategory dc
                    join dc.tags t
                    where dc.id = :categoryId
                    order by t.name
                    ''',
                    [
                            categoryId: plmPart.documentCategory.id
                    ]
            ) as List<String>
        }

        resultPart.put(
                "tags",
                tagNames
        )
        } else {

            /*
             * No matching active/latest PLM part was found.
             */

            resultPart.put(
                    "existsInPlm",
                    false
            )

            resultPart.put(
                    "plmStatus",
                    "NOT_IN_PLM"
            )

            resultPart.put(
                    "tags",
                    [] as List<String>
            )
            resultPart.put("latestVersion", null)
            resultPart.put("majorVersion", null)
            resultPart.put("minorVersion", null)
            resultPart.put("version", null)
            resultPart.put("lifecycleState", "NOT_IN_PLM")
            resultPart.put("checkedOutBy", null)

            resultPart.put("canCheckOut", false)
            resultPart.put("canCheckIn", false)
            resultPart.put("canRelease", false)
            resultPart.put("canCreateRevision", false)
        }

        resultParts.add(resultPart)
    }

    response.contentType = 'application/json'

    render([
            parts: resultParts
    ] as JSON)
}
    // ---------------------------------------------------------
    // Assign tags to a PLM part
    //
    // POST /plmJson/setPartTags
    //
    // JSON:
    // {
    //     "partId": 160,
    //     "tagIds": [10, 13, 37]
    // }
    // ---------------------------------------------------------

    @Secured(["ROLE_PLM_USER", "ROLE_ADMIN"])
    @Transactional
    def setPartTags() {

        Map<String, Object> requestJson =
                request.JSON as Map<String, Object>

        Long partId = requestJson.get("partId") as Long

        List<Object> tagIds =
                requestJson.get("tagIds") as List<Object>

        if (partId == null || tagIds == null) {

            response.status = 400

            render([
                    success: false,
                    error  : "partId and tagIds are required"
            ] as JSON)

            return
        }

        // -----------------------------------------------------
        // Find PLM part
        // -----------------------------------------------------

        PlmFreeCadPart part = PlmFreeCadPart.get(partId)

        if (part == null) {

            response.status = 404

            render([
                    success: false,
                    error  : "PLM part not found",
                    partId : partId
            ] as JSON)

            return
        }

        // -----------------------------------------------------
        // Resolve requested tags
        // -----------------------------------------------------

        List<Long> requestedIds = tagIds.collect {
            Object id -> id as Long
        }.unique()

        List<Term> tags = []

        for (Long tagId : requestedIds) {

            Term tag = Term.get(tagId)

            if (tag == null) {

                response.status = 400

                render([
                        success: false,
                        error  : "Invalid tag ID",
                        tagId  : tagId
                ] as JSON)

                return
            }

            tags.add(tag)
        }

        // -----------------------------------------------------
        // Get or create DocumentCategory
        // -----------------------------------------------------

        DocumentCategory category = part.documentCategory

        if (category == null) {

            category = new DocumentCategory()

            category.save(
                    flush: true,
                    failOnError: true
            )

            part.documentCategory = category
        }

        // -----------------------------------------------------
        // Replace existing tags
        // -----------------------------------------------------

        category.tags?.clear()

        for (Term tag : tags) {

            category.addToTags(tag)
        }

        // -----------------------------------------------------
        // Save changes
        // -----------------------------------------------------

        category.save(
                flush: true,
                failOnError: true
        )

        part.save(
                flush: true,
                failOnError: true
        )

        // -----------------------------------------------------
        // Response
        // -----------------------------------------------------

        response.contentType = "application/json"

        render([
                success: true,
                partId : part.id,
                tagIds : requestedIds
        ] as JSON)
    }
    //create endpoint for searching for parts, exposes /plmJson/searchParts?originalName=
    @Secured(["ROLE_PLM_USER", "ROLE_ADMIN"])
  // Search for parts, exposes /plmJson/searchParts?originalName=
    def searchParts(String originalName) {

        if (!originalName?.trim()) {
            response.status = 400
            render([
                    error: 'originalName is required'
            ] as JSON)
            return
        }

        String searchText = originalName.trim()

        List<PlmFreeCadPart> parts = PlmFreeCadPart.executeQuery(
                '''
                select distinct p
                from PlmFreeCadPart p
                where lower(p.originalName) like lower(:searchText)
                  and p.active = true
                  and p.nextVersion is null
                  and p.pathOnHost like '%FCStd'
                order by p.originalName
                ''',
                [
                        searchText: '%' + searchText + '%'
                ]
        ) as List<PlmFreeCadPart>

        User user = springSecurityService.currentUser as User

        List<Map<String, Object>> results = parts.findAll { PlmFreeCadPart part ->
            plmFreeCadSecurityService.canDownloadFile(part, user)
        }.collect { PlmFreeCadPart part ->

            List<Long> tagIds = []

            if (part.documentCategory) {
                tagIds = Term.executeQuery(
                        '''
                        select distinct t.id
                        from DocumentCategory dc
                        join dc.tags t
                        where dc.id = :categoryId
                        order by t.id
                        ''',
                        [categoryId: part.documentCategory.id]
                ) as List<Long>
            }

            [
                    id                : part.id,
                    computedVersion   : part.computedVersion,
                    active            : part.active,
                    nextVersionId     : part.nextVersion?.id,
                    originalName      : part.originalName,
                    label             : part.label,
                    fileId            : part.fileId,
                    plmContentShaOne  : part.plmContentShaOne,
                    plmFileUserCreated: part.plmFileUserCreated,
                    plmFileUserUpdated: part.plmFileUserUpdated,
                    comment           : part.comment,
                    commentVersion    : part.commentVersion,
                    status            : part.status?.name(),
                    lockedBy          : part.lockedBy ? [
                            id      : part.lockedBy.id,
                            username: part.lockedBy.username
                    ] : null,
                    majorVersion: part.majorVersion,
                    minorVersion: part.minorVersion,
                    version: part.displayVersion,
                    lifecycleState: part.lifecycleState?.name(),
                    checkedOutBy: part.checkedOutBy?.username,
                    canCheckOut: plmFreeCadSecurityService
                            .canCheckOutPart(part, user),
                    canCheckIn: plmFreeCadSecurityService
                            .canUploadPart(part, user),
                    canRelease: plmFreeCadSecurityService
                            .canReleasePart(part, user),
                    canCreateRevision: plmFreeCadSecurityService
                            .canCreateRevision(part, user),
                    tagId             : tagIds

            ] as Map<String, Object>
        }

        response.contentType = 'application/json'
        render results as JSON
    }
    // ---------------------------------------------------------
    // POST /plmJson/checkOutPart
    // ---------------------------------------------------------

    def checkOutPart() {

        if (!FEATURE_CHECK_OUT_PART) {
            response.status = 404
            render([error: "Feature disabled"] as JSON)
            return
        }

        Map<String, Object> data =
                request.JSON as Map<String, Object>

        Long partId = data.get("partId") as Long

        if (partId == null) {
            response.status = 400
            render([error: "partId is required"] as JSON)
            return
        }

        User user =
                springSecurityService.currentUser as User

        try {

            PlmFreeCadPart part =
                    plmPartLifecycleService.checkOutPart(partId, user)

            render([
                    success       : true,
                    partId        : part.id,
                    version       : part.displayVersion,
                    lifecycleState: part.lifecycleState.name(),
                    checkedOutBy  : part.checkedOutBy?.username
            ] as JSON)

        } catch (IllegalStateException e) {

            response.status = 409
            render([
                    success: false,
                    error  : e.message
            ] as JSON)
        }
    }


    // ---------------------------------------------------------
    // POST /plmJson/cancelCheckOut
    // ---------------------------------------------------------

    def cancelCheckOut() {

        if (!FEATURE_CANCEL_CHECK_OUT) {
            response.status = 404
            render([error: "Feature disabled"] as JSON)
            return
        }

        Map<String, Object> data =
                request.JSON as Map<String, Object>

        Long partId = data.get("partId") as Long

        if (partId == null) {
            response.status = 400
            render([error: "partId is required"] as JSON)
            return
        }

        User user =
                springSecurityService.currentUser as User

        try {

            PlmFreeCadPart part =
                    plmPartLifecycleService.cancelCheckOut(
                            partId, user)

            render([
                    success       : true,
                    partId        : part.id,
                    version       : part.displayVersion,
                    lifecycleState: part.lifecycleState.name()
            ] as JSON)

        } catch (IllegalStateException e) {

            response.status = 409
            render([
                    success: false,
                    error  : e.message
            ] as JSON)
        }
    }


    // ---------------------------------------------------------
    // POST /plmJson/releasePart
    // ---------------------------------------------------------

    def releasePart() {

        if (!FEATURE_RELEASE_PART) {
            response.status = 404
            render([error: "Feature disabled"] as JSON)
            return
        }

        Map<String, Object> data =
                request.JSON as Map<String, Object>

        Long partId = data.get("partId") as Long

        if (partId == null) {
            response.status = 400
            render([error: "partId is required"] as JSON)
            return
        }

        User user =
                springSecurityService.currentUser as User

        try {

            PlmFreeCadPart part =
                    plmPartLifecycleService.releasePart(
                            partId, user)

            render([
                    success       : true,
                    partId        : part.id,
                    version       : part.displayVersion,
                    lifecycleState: part.lifecycleState.name(),
                    releasedBy    : part.releasedBy?.username
            ] as JSON)

        } catch (IllegalStateException e) {

            response.status = 409
            render([
                    success: false,
                    error  : e.message
            ] as JSON)
        }
    }
    // ---------------------------------------------------------
    // POST /plmJson/createRevision
    //
    // Create a new major WIP revision from a RELEASED part.
    //
    // Example:
    // 1.2 RELEASED -> 2.0 WIP
    // ---------------------------------------------------------

    def createRevision() {

        if (!FEATURE_CREATE_REVISION) {
            response.status = 404
            render([error: "Feature disabled"] as JSON)
            return
        }

        if (request.method != "POST") {
            response.status = 405
            render([error: "POST required"] as JSON)
            return
        }

        Map<String, Object> data =
                request.JSON as Map<String, Object>

        Long partId = data.get("partId") as Long

        if (partId == null) {
            response.status = 400
            render([error: "partId is required"] as JSON)
            return
        }

        User user =
                springSecurityService.currentUser as User

        try {

            PlmFreeCadPart part =
                    plmPartLifecycleService.createRevision(
                            partId, user)

            response.contentType = "application/json"

            render([
                    success          : true,
                    partId           : part.id,
                    computedVersion  : part.computedVersion,
                    majorVersion     : part.majorVersion,
                    minorVersion     : part.minorVersion,
                    version          : part.displayVersion,
                    lifecycleState   : part.lifecycleState.name(),
                    checkedOutBy     : part.checkedOutBy?.username,
                    canCheckOut      : plmFreeCadSecurityService
                            .canCheckOutPart(part, user)
            ] as JSON)

        } catch (IllegalStateException e) {

            response.status = 409

            render([
                    success: false,
                    error  : e.message
            ] as JSON)
        }
    }
}
