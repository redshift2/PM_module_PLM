package plm

import attachement.AttachmentUiService
import attachment.DocumentCategory
import attachment.Term
import crew.AttachmentController
import crew.User
import grails.compiler.GrailsCompileStatic
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.web.api.WebAttributes
import jakarta.annotation.PostConstruct
import org.codehaus.groovy.runtime.MethodClosure as MC
import taack.ast.type.FieldInfo
import taack.domain.TaackAttachmentService
import taack.domain.TaackFilter
import taack.domain.TaackFilterService
import taack.ui.TaackUiConfiguration
import taack.ui.dsl.*
import taack.ui.dsl.block.BlockSpec
import taack.ui.dsl.common.ActionIcon
import taack.ui.dsl.common.IconStyle
import taack.ui.dsl.common.Style
import taack.ui.dsl.filter.expression.FilterExpression
import taack.ui.dsl.filter.expression.Operator
import taack.ui.dump.Parameter
import taack.wysiwyg.Asciidoc

import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

import static taack.render.TaackUiService.tr

@GrailsCompileStatic
class PlmFreeCadUiService implements WebAttributes, GrailsConfigurationAware {

    static final List<String> errorsInit = []

    String convertPath
    String dotPath

    @Override
    void setConfiguration(Config config) {
        dotPath = resolveExecutable(config.getProperty('exe.dot.path') ?: 'dot')
        convertPath = resolveExecutable(config.getProperty('exe.convertPath') ?: 'convert')
    }

    /** A value containing a path separator is used as is; a bare command name is searched on the process PATH. */
    private static String resolveExecutable(String configured) {
        if (configured.contains(File.separator)) return configured
        List<String> pathDirs = (System.getenv('PATH') ?: '').tokenize(File.pathSeparator)
        File found = pathDirs.collect { String dir -> new File(dir, configured) }.find { File f -> f.canExecute() } as File
        found ? found.path : configured
    }

    private void requireExecutable(String path, String configKey, String installHint) {
        if (new File(path).canExecute()) return
        String message = "'$path' is not executable. $installHint, or set $configKey to its full path"
        log.error message
        errorsInit.add message
    }

    TaackFilterService taackFilterService
    AttachmentUiService attachmentUiService
    TaackAttachmentService taackAttachmentService
    PlmFreeCadSecurityService plmFreeCadSecurityService

    final private String intranetRoot = TaackUiConfiguration.root

    String getStorePath() {
        intranetRoot + "/plmFreecad/model"
    }

    String getGlbPath() {
        intranetRoot + "/plmFreecad/glb"
    }

    String getPreviewPath() {
        intranetRoot + "/plmFreecad/preview"
    }

    String getZipPath() {
        intranetRoot + "/plmFreecad/zip"
    }

    String getTmpPath() {
        intranetRoot + "/plmFreecad/tmp"
    }

    private final File noPreview = new File(previewPath + '/' + "no-preview.webp")

    @PostConstruct
    void init() {
        new File(storePath).mkdirs()
        new File(glbPath).mkdirs()
        new File(previewPath).mkdirs()
        new File(zipPath).mkdirs()
        new File(tmpPath).mkdirs()
        File noPreview = new File(previewPath + '/' + "no-preview.webp")
        if (!noPreview.exists())
            new FileOutputStream(noPreview) << this.class.getResourceAsStream("/plm/no-preview.webp").readAllBytes()

        requireExecutable convertPath, 'exe.convertPath', 'Install ImageMagick (apt install imagemagick / brew install imagemagick)'
        requireExecutable dotPath, 'exe.dot.path', 'Install graphviz (apt install graphviz / brew install graphviz)'
    }

    UiFilterSpecifier buildPartFilter() {
        def p = new PlmFreeCadPart(active: true, nextVersion: null, status: null)
        def l = new PlmFreeCadLink()
        def d = new DocumentCategory()
        def u = new User()
        def t = new Term()
        new UiFilterSpecifier().ui PlmFreeCadPart, {
            section tr('default.user.label'), {
                filterField p.lockedBy_, u.username_
                filterField p.userCreated_, u.username_
                filterField p.userUpdated_, u.username_
            }
            section tr('dates.label'), {
                filterField p.dateCreated_
                filterField p.lastUpdated_
            }
            section tr('files.label'), {
                filterField p.originalName_
                filterField p.label_
                filterField p.status_
            }
            section tr('usedIn.label'), {
                filterField p.plmLinks_, l.part_, p.originalName_
            }
            section tr('plmFile.label'), {
                filterField p.plmFileUserCreated_
                filterField p.plmFileDateCreated_
                filterField p.plmFileUserUpdated_
                filterField p.plmFileLastUpdated_
            }
            section tr('tags.label'), {
                filterField p.documentCategory_, d.tags_, t.name_
            }
        }
    }

    UiTableSpecifier buildLinkTableFromPart(PlmFreeCadPart part) {
        def l = new PlmFreeCadLink()
        def p = new PlmFreeCadPart()
        def d = new DocumentCategory()
        def u = new User()
        new UiTableSpecifier().ui {
            header {
                label tr('preview.label')
                column {
                    sortableFieldHeader l.dateCreated_
                    sortableFieldHeader l.userCreated_, u.username_
                }
                column {
                    sortableFieldHeader l.lastUpdated_
                    sortableFieldHeader l.userUpdated_, u.username_
                }
                column {
                    sortableFieldHeader l.linkClaimChild_
                    sortableFieldHeader l.linkTransform_
                }
                column {
                    sortableFieldHeader l.linkCopyOnChange_
                    sortableFieldHeader l.part_, p.label_
                }
                label l.part_, p.documentCategory_, d.tags_
            }

            iterate(taackFilterService.getBuilder(PlmFreeCadLink)
                    .setSortOrder(TaackFilter.Order.DESC, l.dateCreated_)
                    .addRestrictedIds(part.plmLinks*.id as Long[])
                    .build()) { PlmFreeCadLink o ->
                rowFieldRaw """<div style="text-align: center;"><img style="max-height: 64px; max-width: 64px;" src="/plm/previewPart/${o.part.id}?partVersion=${o.partLinkVersion}&timestamp=${o.part.mTimeNs}"></div>"""
                rowColumn {
                    rowField o.dateCreated_
                    rowField o.userCreated.username
                }
                rowColumn {
                    rowField o.lastUpdated_
                    rowField o.userUpdated.username
                }
                rowColumn {
                    rowField o.linkClaimChild?.toString()
                    rowField o.linkTransform?.toString()
                }
                rowColumn {
                    rowField o.linkCopyOnChange?.toString()
                    rowAction o.part.label + '-v' + o.partLinkVersion + ' #' + o.linkedObject, PlmController.&showPart as MC, o.part.id
                }
                rowField o.part.documentCategory?.tags*.name?.join(', ')
            }
        }
    }

    UiTableSpecifier buildLinkTableFromPartHierarchical(PlmFreeCadPart part) {
        def l = new PlmFreeCadLink()
        def p = new PlmFreeCadPart()
        def d = new DocumentCategory()
        def u = new User()
        new UiTableSpecifier().ui {
            header {
                label tr('preview.label')
                column {
                    label l.dateCreated_
                    label l.userCreated_, u.username_
                }
                column {
                    label l.lastUpdated_
                    label l.userUpdated_, u.username_
                }
                column {
                    label l.linkClaimChild_
                    label l.linkTransform_
                }
                column {
                    label l.linkCopyOnChange_
                    label l.part_, p.label_
                }
                label l.part_, p.documentCategory_, d.tags_
            }

            int count = 0
            Closure rec
            rec = { List<PlmFreeCadLink> mus, int level ->
                rowIndent({
                    level++
                    for (PlmFreeCadLink o : mus) {
                        count++
                        boolean muHasChildren = !o.part.plmLinks?.empty
                        rowTree muHasChildren, {
                            rowColumn {
                                rowFieldRaw """<div style="text-align: center;"><img style="max-height: 64px; max-width: 64px;" src="/plm/previewPart/${o.part.id}?partVersion=${o.partLinkVersion}&timestamp=${o.part.mTimeNs}"></div>"""
                            }
                            rowColumn {
                                rowField o.dateCreated_
                                rowField o.userCreated.username
                            }
                            rowColumn {
                                rowField o.lastUpdated_
                                rowField o.userUpdated.username
                            }
                            rowColumn {
                                rowField o.linkClaimChild?.toString()
                                rowField o.linkTransform?.toString()
                            }
                            rowColumn {
                                rowField o.linkCopyOnChange?.toString()
                                rowAction o.part.label + '-v' + o.partLinkVersion + ' #' + o.linkedObject, PlmController.&showPart as MC, o.part.id
                            }
                            rowField o.part.documentCategory?.tags*.name?.join(', ')
                        }
                        if (muHasChildren) {
                            rec(o.part.plmLinks?.sort { it.id }, level)
                        }
                    }
                })
            }

            rec(part.plmLinks?.sort { it.id }, 0)
        }
    }

    UiFormSpecifier buildPartMetadataForm(PlmFreeCadPart part) {
        new UiFormSpecifier().ui part, {
            field part.description1_
            field part.description2_
            field part.description3_
            field part.cost_
            field part.weight_
            field part.material_
            field part.supplier_
            field part.surfaceTreatment_
            field part.lom_
            field part.dcn_
            formAction PlmController.&savePartMetadata as MC
        }
    }

    UiFormSpecifier buildPartForm(PlmFreeCadPart part) {
        new UiFormSpecifier().ui part, {
            field part.lifecycleState_
            field part.writeAccess_

            ajaxField part.documentAccess_,
                    AttachmentController.&selectDocumentAccess as MC,
                    part.documentAccess_

            formAction PlmController.&savePart as MC
        }
    }

    UiFormSpecifier buildPartFormCategory(PlmFreeCadPart part) {
        new UiFormSpecifier().ui part, {
            ajaxField part.documentCategory_, AttachmentController.&selectDocumentCategory as MC, part.documentCategory_
            formAction PlmController.&savePartCategory as MC
        }
    }

    UiTableSpecifier buildPartTable(Collection<PlmFreeCadPart> freeCadParts = null, UiFilterSpecifier additionalFileter = null) {
        def p = new PlmFreeCadPart(active: true, nextVersion: null)
        def u = new User()
        new UiTableSpecifier().ui {
            header {
                label tr('preview.label')
                column {
                    sortableFieldHeader p.dateCreated_
                    sortableFieldHeader p.userCreated_, u.username_
                }
                column {
                    sortableFieldHeader p.lastUpdated_
                    sortableFieldHeader p.userUpdated_, u.username_
                }
                column {
                    sortableFieldHeader p.lockedBy_, u.username_
                    label 'CAD Version'
                }
                column {
                    sortableFieldHeader p.label_
                    label 'Lifecycle State'
                }
                label 'Description 1'
                label 'Description 2'
                label 'Description 3'
                label 'LOM'
                label 'DCN'
                label tr('tags.label')
            }
            def f = new UiFilterSpecifier().sec PlmFreeCadPart, {
                filterFieldExpressionBool(new FilterExpression(null as Object, Operator.EQ, p.nextVersion_))
            }

            f.join(additionalFileter)

            TaackFilter.FilterBuilder tfb = taackFilterService.getBuilder(PlmFreeCadPart)
                    .setMaxNumberOfLine(20)
                    .setSortOrder(TaackFilter.Order.DESC, p.dateCreated_)
                    .addFilter(f)

            if (freeCadParts != null) {
                tfb.addRestrictedIds(freeCadParts*.id as Long[])
            }

            iterate(tfb.build()) { PlmFreeCadPart obj ->
                rowFieldRaw """${this.plmFreeCadSecurityService.canDownloadFile(obj) ? """<div style="text-align: center;"><a href="/plm/showPart?id=${obj.id}" title="Open part details" style="display:inline-block;cursor:pointer;"><img style="max-height: 64px; max-width: 64px;" src="/plm/previewPart/${obj.id ?: 0}?partVersion=${obj.computedVersion ?: 0}&amp;timestamp=${obj.mTimeNs}" alt="Part preview"></a></div>""" : "<span />"}"""
                rowColumn {
                    rowField obj.dateCreated_
                    rowField obj.userCreated.username
                }
                rowColumn {
                    rowField obj.lastUpdated_
                    rowField obj.userUpdated?.username
                }
                rowColumn {
                    rowField obj.lockedBy?.username
                    rowField "${obj.majorVersion ?: 1}.${obj.minorVersion ?: 0}"
                }
                rowColumn {
                    PlmFreeCadPart forkedFrom = obj.forkFrom()
                    if (forkedFrom)
                        rowAction tr('based.on.label'), PlmActionIcon.PARENT * IconStyle.SCALE_DOWN * IconStyle.RIGHT, PlmController.&showPart as MC, forkedFrom.id
                    rowAction obj.label, PlmController.&showPart as MC, obj.id
                    rowField obj.lifecycleState?.toString() ?: 'WIP'
                }
                rowField obj.description1 ?: '—'
                rowField obj.description2 ?: '—'
                rowField obj.description3 ?: '—'
                rowField obj.lom?.toString() ?: '—'
                rowField obj.dcn ?: '—'
                rowField obj.documentCategory?.tags*.name?.join(', ')
            }
        }
    }

    private static String diffTr(FieldInfo fieldInfoFrom, FieldInfo fieldInfoTo) {
        String from = tr('none.label')
        if (fieldInfoFrom && fieldInfoFrom.value) from = fieldInfoFrom.value.toString()
        String to = tr('none.label')
        if (fieldInfoTo && fieldInfoTo.value) to = fieldInfoTo.value.toString()

        if (from != to) {
            String i18n = tr('content.became.from.to.label', fieldInfoFrom ? tr(fieldInfoFrom) : fieldInfoTo ? tr(fieldInfoTo) : ' unknown ', from.take(30), to.take(30))
            "<li>$i18n</li>"
        } else ''
    }

    private static String escapeSummaryHtml(String value) {
        (value ?: '').replace('&', '&amp;').replace('<', '&lt;')
                .replace('>', '&gt;').replace('"', '&quot;').replace("'", '&#39;')
    }

    // Two aligned label/value groups per row. Escape all database text.
    private static String metadataPairRow(String leftLabel, Object leftValue,
                                          String rightLabel, Object rightValue) {
        String left = leftValue == null || leftValue.toString().trim().isEmpty()
                ? '—' : escapeSummaryHtml(leftValue.toString())
        String right = rightValue == null || rightValue.toString().trim().isEmpty()
                ? '—' : escapeSummaryHtml(rightValue.toString())
        String rightLabelHtml = rightLabel == null ? '' : escapeSummaryHtml(rightLabel)
        String rightValueHtml = rightLabel == null ? '' : right
        return """<tr>
            <td class="plm-meta-label">${escapeSummaryHtml(leftLabel)}</td>
            <td class="plm-meta-value">${left}</td>
            <td class="plm-meta-gap"></td>
            <td class="plm-meta-label">${rightLabelHtml}</td>
            <td class="plm-meta-value">${rightValueHtml}</td>
        </tr>"""
    }

    private static String inlineMetaValue(String name, Object value, boolean multiline = false) {
        String raw = value == null ? '' : value.toString()
        String display = raw.trim() ? escapeSummaryHtml(raw) : '—'
        String safe = escapeSummaryHtml(raw)
        String control = multiline
                ? """<textarea class="plm-edit-field" name="${name}" rows="2">${safe}</textarea>"""
                : """<input class="plm-edit-field" name="${name}" value="${safe}" type="text">"""
        if (name == 'lom') {
            control = """<select class="plm-edit-field" name="lom">
                <option value="">—</option>
                ${['A', 'B', 'C', 'P'].collect { String v -> "<option value='${v}' ${raw == v ? 'selected' : ''}>${v}</option>" }.join('')}
            </select>"""
        }
        return """<span class="plm-read-value">${display}</span>${control}"""
    }

    private static String inlineMetaRow(String label, String name, Object value,
                                        String rightLabel, Object rightValue, boolean multiline = false) {
        String right = rightLabel == null ? '' : (rightValue == null || !rightValue.toString().trim() ? '—' : escapeSummaryHtml(rightValue.toString()))
        return """<tr><td class="plm-meta-label">${escapeSummaryHtml(label)}</td>
          <td class="plm-meta-value">${inlineMetaValue(name, value, multiline)}</td>
          <td class="plm-meta-gap"></td>
          <td class="plm-meta-label">${escapeSummaryHtml(rightLabel ?: '')}</td>
          <td class="plm-meta-value">${right}</td></tr>"""
    }

    private static String metadataDate(Date date) {
        date == null ? '—' : new java.text.SimpleDateFormat('MM/dd/yyyy').format(date)
    }

    UiBlockSpecifier buildFreeCadPartBlockShow(PlmFreeCadPart part, Long partVersion, boolean isMail = false, boolean isHistory = false) {
        MC diffTr = PlmFreeCadUiService.&diffTr as MC
        if (partVersion != null) {
            part = part.getHistory()[partVersion]
        }

        log.info "Showing ${part}"

        // Full-width title ABOVE both the preview and the tabbed content.
        String partTitleHtml = """<div class="plm-part-name" style="font-size:clamp(24px,2.3vw,34px);font-weight:700;line-height:1.3;overflow-wrap:anywhere;padding:12px 16px 8px;">${PlmFreeCadUiService.escapeSummaryHtml(part.originalName ?: part.label ?: 'Unnamed Part')}</div>"""
        def showPartTitle = new UiShowSpecifier().ui {
            field partTitleHtml
        }

        // Persistent preview and summary, shown alongside the six content tabs.
        def showPreview = new UiShowSpecifier().ui {
            field """<div style="text-align:center;padding:12px;"><img style="width:100%;max-width:340px;height:auto;object-fit:contain;" src="/plm/previewPart/${part.id ?: 0}?partVersion=${part.computedVersion ?: 0}&timestamp=${part.mTimeNs}"></div>"""
        }

        // A single HTML table prevents Taack's plain `field` values from
        // running together on one line in the narrow summary column.
        String summaryVersion = "${part.majorVersion ?: 1}.${part.minorVersion != null ? part.minorVersion : 0}"
        String summaryStatus = part.lifecycleState?.toString() ?: 'WIP'
        String summaryOwner = part.checkedOutBy?.username ?: ''
        List<String> summaryTags = (part.documentCategory?.tags*.name ?: []) as List<String>
        String tagRows = summaryTags ? summaryTags.collect { String tag ->
            "<div style=\"padding:2px 0;\">${escapeSummaryHtml(tag)}</div>"
        }.join('') : '—'
        String summaryHtml = """
            <table style="width:100%;border-collapse:collapse;table-layout:fixed;">
              <tbody>
                <tr><th style="text-align:left;vertical-align:top;padding:6px 8px;width:40%;">Version:</th><td style="padding:6px 8px;">${escapeSummaryHtml(summaryVersion)}</td></tr>
                <tr><th style="text-align:left;vertical-align:top;padding:6px 8px;">LOM:</th><td style="padding:6px 8px;">${escapeSummaryHtml(part.lom?.toString() ?: "—")}</td></tr>
                <tr><th style="text-align:left;vertical-align:top;padding:6px 8px;">Status:</th><td style="padding:6px 8px;">${escapeSummaryHtml(summaryStatus)}</td></tr>
                <tr><th style="text-align:left;vertical-align:top;padding:6px 8px;">Owner:</th><td style="padding:6px 8px;">${escapeSummaryHtml(summaryOwner)}</td></tr>
                <tr><th style="text-align:left;vertical-align:top;padding:6px 8px;">Tags:</th><td style="padding:6px 8px;">${tagRows}</td></tr>
              </tbody>
            </table>
        """
        // Release is available only for the current active WIP part.
        boolean canShowRelease =
                !isMail &&
                !isHistory &&
                partVersion == null &&
                part.active == true &&
                part.nextVersion == null &&
                (part.lifecycleState ?: PlmPartLifecycleState.WIP) ==
                        PlmPartLifecycleState.WIP

        def showSummary = new UiShowSpecifier().ui {

            section 'Part Summary', {

                field summaryHtml

                if (!part.active) {
                    field Style.EMPHASIS + Style.RED, tr('not.active.label')
                }

                // Existing category editing button.
                showAction(
                    ActionIcon.EDIT * IconStyle.SCALE_DOWN * IconStyle.LEFT,
                    PlmController.&editPartCategory as MC,
                    part.id
                )

                // Release button.
                if (canShowRelease) {
                    showAction(
                        'Release Part',
                        PlmController.&releasePart as MC,
                        part.id
                    )
                }
            }
        }

        // Two side-by-side label/value groups with a narrow spacer column.
        // All rows use the same table and remain visible when values are empty.
        // Metadata edit mode stays inside the existing tab; no modal is opened.
        String metadataSaveUrl = new Parameter().urlMapped(PlmController.&savePartMetadata as MC, [id: part.id])
        String rows = [
            PlmFreeCadUiService.metadataPairRow('Label', part.label, 'Date Created', PlmFreeCadUiService.metadataDate(part.dateCreated)),
            PlmFreeCadUiService.inlineMetaRow('Description 1', 'description1', part.description1, 'User Created', part.userCreated?.username, true),
            PlmFreeCadUiService.inlineMetaRow('Description 2', 'description2', part.description2, 'Last Updated', PlmFreeCadUiService.metadataDate(part.lastUpdated), true),
            PlmFreeCadUiService.inlineMetaRow('Description 3', 'description3', part.description3, 'User Updated', part.userUpdated?.username, true),
            PlmFreeCadUiService.inlineMetaRow('Cost', 'cost', part.cost, 'Embedded Com.', part.comment),
            PlmFreeCadUiService.inlineMetaRow('Weight', 'weight', part.weight, 'File Date Updated (UTC)', PlmFreeCadUiService.metadataDate(part.plmFileLastUpdated)),
            PlmFreeCadUiService.inlineMetaRow('Material', 'material', part.material, 'File User Updated', part.plmFileUserUpdated),
            PlmFreeCadUiService.inlineMetaRow('Supplier', 'supplier', part.supplier, 'File Date Created (UTC)', PlmFreeCadUiService.metadataDate(part.plmFileDateCreated)),
            PlmFreeCadUiService.inlineMetaRow('Surface Treatment', 'surfaceTreatment', part.surfaceTreatment, 'File User Created', part.plmFileUserCreated),
            PlmFreeCadUiService.inlineMetaRow('LOM', 'lom', part.lom, 'Content Type', part.plmContentType),
            PlmFreeCadUiService.inlineMetaRow('DCN', 'dcn', part.dcn, 'Locked By', part.lockedBy?.username)
        ].join('')
        String metadataHtml = """<style>
          .plm-metadata-table { width:100%;border-collapse:collapse;table-layout:fixed; }
          .plm-metadata-table tbody tr:nth-child(odd) { background:rgba(255,255,255,.045); }
          .plm-metadata-table tbody tr:nth-child(even) { background:rgba(255,255,255,.012); }
          .plm-metadata-table tbody tr:hover { background:rgba(100,160,220,.12); }
          .plm-metadata-table td { padding:8px 10px;vertical-align:top;font-weight:normal; }
          .plm-metadata-table .plm-meta-label { text-align:right; }
          .plm-metadata-table .plm-meta-value { text-align:left;overflow-wrap:anywhere; }
          .plm-metadata-table .plm-meta-gap { padding:0; }
          .plm-edit-field { display:none;box-sizing:border-box;width:100%;background:#202830;color:#f1f5f9;border:1px solid #64748b;border-radius:4px;padding:6px 8px; }
          .plm-editing .plm-edit-field { display:block; }
          .plm-editing .plm-read-value { display:none; }
          .plm-meta-buttons { display:flex;justify-content:flex-end;gap:8px;padding:8px 12px; }
          .plm-meta-buttons button { border:1px solid #64748b;background:#334155;color:white;border-radius:4px;padding:6px 14px;cursor:pointer; }
          .plm-meta-buttons .plm-save { background:#1768b6; }
          </style>
          <div class="plm-inline-meta" data-save="${PlmFreeCadUiService.escapeSummaryHtml(metadataSaveUrl)}">
            <div class="plm-meta-buttons">
              <button type="button" class="plm-edit" onclick="this.closest('.plm-inline-meta').classList.add('plm-editing')">✎ Edit</button>
              <button type="button" class="plm-save" style="display:none" onclick="plmSaveMetadata(this)">Save</button>
              <button type="button" class="plm-cancel" style="display:none" onclick="plmCancelMetadata(this)">Cancel</button>
            </div>
            <div class="plm-meta-error" role="alert" style="color:#ff9999;padding:0 12px;"></div>
            <table class="plm-metadata-table"><colgroup>
              <col style="width:15%"><col style="width:31%"><col style="width:4%"><col style="width:24%"><col style="width:26%">
            </colgroup><tbody>${rows}</tbody></table>
          </div>
          <script>
          (function(){
            if(window.plmInlineMetadataInstalled)return;
            window.plmInlineMetadataInstalled=true;
            window.plmCancelMetadata=function(button){
              var box=button.closest('.plm-inline-meta');
              box.querySelectorAll('.plm-edit-field').forEach(function(input){if(input.tagName==='SELECT'){var initial=input.querySelector('option[selected]');input.value=initial?initial.value:'';}else{input.value=input.defaultValue || '';}});
              box.classList.remove('plm-editing');
              box.querySelector('.plm-meta-error').textContent='';
              plmToggleMetadataButtons(box);
            };
            window.plmToggleMetadataButtons=function(box){
              var editing=box.classList.contains('plm-editing');
              box.querySelector('.plm-edit').style.display=editing?'none':'';
              box.querySelector('.plm-save').style.display=editing?'':'none';
              box.querySelector('.plm-cancel').style.display=editing?'':'none';
            };
            window.plmSaveMetadata=function(button){
              var box=button.closest('.plm-inline-meta'),data=new URLSearchParams();
              box.querySelectorAll('.plm-edit-field').forEach(function(input){data.append(input.name,input.value);});
              var csrf=document.querySelector('meta[name="_csrf"]');
              if(csrf)data.append('_csrf',csrf.content);
              button.disabled=true;
              box.querySelector('.plm-meta-error').textContent='';
              fetch(box.dataset.save,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','X-Requested-With':'XMLHttpRequest'},body:data.toString()})
                .then(function(response){if(!response.ok)return response.text().then(function(body){throw new Error(body.replace(/<[^>]*>/g,' ').slice(0,250)||('HTTP '+response.status));});window.location.reload();})
                .catch(function(error){box.querySelector('.plm-meta-error').textContent=error.message;button.disabled=false;});
            };
            document.addEventListener('click',function(event){
              if(event.target.matches('.plm-inline-meta .plm-edit'))plmToggleMetadataButtons(event.target.closest('.plm-inline-meta'));
            });
          })();
          </script>"""

        def showMetadata = new UiShowSpecifier().ui {
            section 'Part Metadata', {
                field metadataHtml
            }
        }

        UiBlockSpecifier b = new UiBlockSpecifier().ui {
            // Outside the row so the title spans both columns.
            show showPartTitle
            row {
                col BlockSpec.Width.QUARTER, {
                    show showPreview, {
                        label(tr('preview.label'))
                        menuIcon ActionIcon.DOWNLOAD, PlmController.&downloadBinPart as MC, [id: part.id, partVersion: part.computedVersion ?: 0]
                    }
                    show showSummary
                }
                col BlockSpec.Width.THREE_QUARTER, {
                    tabs {
                        tab('Part Metadata') {
                            show showMetadata, {
                                // Metadata edit controls are inside the table, not a modal.
                            }
                        }
                        if (!isMail && !isHistory) {
                            tab('Where Used') {
                                List<PlmFreeCadLink> parentLinks = PlmFreeCadLink.findAllByPart(part)
                                if (!parentLinks.empty) {
                                    List<PlmFreeCadPart> containerParts = parentLinks*.parentPart.findAll { PlmFreeCadPart parent -> parent.active }
                                    if (containerParts) table buildPartTable(containerParts)
                                }
                            }
                            tab('BOM') {
                                if (!part.linkedParts.empty) {
                                    table buildLinkTableFromPartHierarchical(part)
                                }
                            }
                            tab('Attachments') {
                                table attachmentUiService.buildObjectAttachmentsDropTable(part, part.commentVersionAttachmentList, PlmController.&onDrop as MC), {
                                    menuIcon ActionIcon.ADD, PlmController.&addAttachment as MC, part.id
                                }
                            }
                        }
                        if (!isHistory) {
                            tab('Comments') {
                                show new UiShowSpecifier().ui {
                                    String asciidoc = this.genAsciidoc(part)
                                    inlineHtml(asciidoc, 'asciidocMain')
                                }, {
                                    menuIcon ActionIcon.EDIT, PlmController.&addComment as MC, part.id
                                }
                            }
                        }
                        if (!isMail && !isHistory) {
                    tab('CAD History') {
                        table new UiTableSpecifier().ui({
                            def h = part.history
                            PlmFreeCadPart p = null
                            if (h) {
                                long partVersionOcc = 0
                                for (def i : h) {
                                    row {
                                        rowColumn 2, {
                                            rowField "<b>${i.historyUserCreated.username}</b> on ${i.historyDateCreated}"
                                        }
                                    }
                                    row {
                                        if (!p) {
                                            rowColumn {
                                                rowField tr('initial.version.label')
                                            }
                                            rowColumn {
                                                rowAction ActionIcon.SHOW * IconStyle.SCALE_DOWN, PlmController.&showPart as MC, part.id, [partVersion: partVersionOcc, isHistory: true]
                                                rowFieldRaw """<div style="text-align: center;"><img style="max-width: 125px;" src="/plm/previewPart/${part.id ?: 0}?partVersion=${partVersionOcc}&timestamp=${part.mTimeNs}"></div>"""
                                            }
                                        }
                                    }
                                    if (p) {
                                        row {
                                            StringBuffer diff = new StringBuffer()
                                            diff << "<ul>"
                                            diff << diffTr(p.plmContentShaOne_, i.plmContentShaOne_)
                                            diff << diffTr(p.commentVersion_, i.commentVersion_)
                                            diff << diffTr(p.lockedBy_, i.lockedBy_)
                                            diff << diffTr(p.status_, i.status_)
                                            diff << diffTr(p.label_, i.label_)
                                            diff << diffTr(p.originalName_, i.originalName_)
                                            diff << diffTr(p.plmContentType_, i.plmContentType_)
                                            diff << diffTr(p.plmFileLastUpdated_, i.plmFileLastUpdated_)
                                            diff << diffTr(p.plmFileDateCreated_, i.plmFileDateCreated_)
                                            diff << diffTr(p.plmFileUserCreated_, i.plmFileUserCreated_)
                                            diff << diffTr(p.plmFileUserUpdated_, i.plmFileUserUpdated_)
                                            diff << diffTr(p.comment_, i.comment_)
                                            diff << diffTr(p.documentCategory?.tags_, i.documentCategory?.tags_)
                                            diff << "</ul>"
                                            rowColumn {
                                                if (i.commentVersion && p.commentVersion != i.commentVersion) {
                                                    rowAction ActionIcon.SHOW * IconStyle.SCALE_DOWN, PlmController.&previewAsciidoc as MC, i.id
                                                }
                                                rowFieldRaw diff.toString()
                                            }
                                            partVersionOcc++
                                            if (p.plmFilePath != i.plmFilePath) {
                                                rowColumn {
                                                    rowAction 'Access Version', ActionIcon.SHOW * IconStyle.SCALE_DOWN, PlmController.&showPart as MC, part.id, [partVersion: partVersionOcc, isHistory: true]
                                                    rowFieldRaw """<div style="text-align: center;"><img style="max-width: 125px;" src="/plm/previewPart/${part.id ?: 0}?partVersion=${partVersionOcc}&timestamp=${part.mTimeNs}"></div>"""
                                                }
                                            } else {
                                                rowColumn {

                                                }
                                            }
                                        }
                                    }
                                    p = i
                                }
                            }
                        })
                    }
                        }
                    }
                }
            }
        }
        if (isHistory) {
            new UiBlockSpecifier().ui {
                modal(b.closure)
            }
        } else {
            b
        }
    }

    String genAsciidoc(PlmFreeCadPart part) {
        String content = part.commentVersion
        if (content) {
            File f = new File(tmpPath + '/' + content.md5())
            String urlFileRoot = new Parameter().urlMapped(PlmController.&downloadBinCommentVersionFiles as MC, [id: part.id])
            if (!f.exists()) {
                f << Asciidoc.getContentHtml(content, urlFileRoot, false)
            }
            f.text
        } else ''
    }

    private static String partFilePath(PlmFreeCadPart part, PlmFreeCadPart linkPart) {
        "${linkPart.pathOnHost - part.pathOnHost.substring(0, part.pathOnHost.lastIndexOf('/'))}"
    }

    File zipPart(PlmFreeCadPart part, Long version = null) {
        log.info "part(${part.id}, ${version})"
        if (version != null) {
            part = part.getHistory()[version]
        }
        def ret = new File("${zipPath}/${part.id}.zip")
        if (ret.exists()) ret.delete()
        FileOutputStream fos = new FileOutputStream(ret)
        ZipOutputStream zipOut = new ZipOutputStream(fos)
        Set<String> setPartFilePath = []
        log.info "part${part.id}.allLinkedParts: ${part.allLinkedParts*.plmFilePath}"
        for (PlmFreeCadPart it in part.allLinkedParts) {
            FileInputStream fis = new FileInputStream(new File("${storePath}/${it.plmFilePath}"))
            String fileEntryName = partFilePath(part, it)
            if (setPartFilePath.contains(fileEntryName)) {
                log.warn "Entry present in zip file: $fileEntryName"
                continue
            }
            setPartFilePath.add fileEntryName
            ZipEntry zipEntry = new ZipEntry(fileEntryName)
            zipEntry.setTime((long) (part.mTimeNs / 1000000))
            try {
                zipOut.putNextEntry(zipEntry)

                byte[] bytes = new byte[1024]
                int length
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length)
                }
            } catch (ZipException ze) {
                log.error "${ze.message}"
            }
            fis.close()
        }
        zipOut.close()
        fos.close()
        ret
    }

    File preview(PlmFreeCadPart part, Long version = null) {
        if (version != null) {
            part = part.getHistory()[version]
        }
        String filePath = previewPath + '/' + part.plmContentShaOne + '.png'
        String filePathWebp = previewPath + '/' + part.plmContentShaOne + '.webp'
        if (new File(filePath).exists())
            return new File(filePath)
        else if (new File(filePathWebp).exists()) {
            return new File(filePathWebp)
        } else {
            File preview = taackAttachmentService.attachmentPreview(new File(storePath + '/' + part.plmFilePath), new File(filePathWebp))
            if (preview.exists()) return preview
            else return noPreview
        }
    }
}