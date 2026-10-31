package plm

import grails.compiler.GrailsCompileStatic
import jakarta.annotation.PostConstruct
import org.codehaus.groovy.runtime.MethodClosure
import taack.domain.TaackGormClass
import taack.domain.TaackGormClassRegisterService
import taack.render.TaackUiService
import taack.ui.dsl.UiBlockSpecifier
import taack.ui.dsl.UiShowSpecifier

@GrailsCompileStatic
class PlmSearchService {

    static lazyInit = false

    PlmFreeCadUiService plmFreeCadUiService
    PlmFreeCadSecurityService plmFreeCadSecurityService

    @PostConstruct
    void init() {

        // Retain Taack's registration for PLM part detail links.
        TaackGormClassRegisterService.register(
            new TaackGormClass(PlmFreeCadPart.class).builder
                .setShowMethod(
                    PlmController.&showPart as MethodClosure
                )
                .setShowLabel({ Long id ->
                    PlmFreeCadPart part = PlmFreeCadPart.read(id)

                    part == null
                        ? "PLM Part ${id}"
                        : "PlmFreeCadPart: ${part.label} (${id})"
                })
                .setTypeLabel({ Long id ->
                    TaackUiService.tr('plmPart.label')
                })
                .build()
        )
    }

    /**
     * Search active PLM parts using the database.
     *
     * No Solr dependency.
     */
    UiBlockSpecifier buildSearchBlock(String q) {

        String searchText = q == null ? '' : q.trim()

        if (!searchText) {
            return messageBlock(
                'Enter a part name, label, or description to search.'
            )
        }

        String pattern = '%' + searchText.toLowerCase() + '%'

        List<PlmFreeCadPart> matches =
            PlmFreeCadPart.executeQuery(
                '''
                SELECT DISTINCT p
                FROM PlmFreeCadPart p
                WHERE p.active = true
                  AND p.nextVersion IS NULL
                  AND p.status <> :obsolete
                  AND (
                       LOWER(p.originalName) LIKE :pattern
                    OR LOWER(p.label) LIKE :pattern
                    OR LOWER(p.description1) LIKE :pattern
                    OR LOWER(p.description2) LIKE :pattern
                    OR LOWER(p.description3) LIKE :pattern
                    OR LOWER(p.material) LIKE :pattern
                    OR LOWER(p.supplier) LIKE :pattern
                    OR LOWER(p.dcn) LIKE :pattern
                  )
                ORDER BY p.label ASC
                ''',
                [
                    obsolete: PlmFreeCadPartStatus.OBSOLETE,
                    pattern: pattern
                ],
                [max: 200]
            ) as List<PlmFreeCadPart>

        // Apply the existing PLM download/view permission check.
        List<PlmFreeCadPart> visibleParts =
            new ArrayList<PlmFreeCadPart>()

        for (PlmFreeCadPart part : matches) {
            if (plmFreeCadSecurityService.canDownloadFile(part)) {
                visibleParts.add(part)
            }
        }

        if (visibleParts.isEmpty()) {
            return messageBlock(
                'No matching PLM parts found.'
            )
        }

        return new UiBlockSpecifier().ui {
            table(
                plmFreeCadUiService.buildPartTable(visibleParts)
            )
        }
    }

    /**
     * Display an informational message using the Taack UI.
     */
    private UiBlockSpecifier messageBlock(String message) {

        return new UiBlockSpecifier().ui {
            show(new UiShowSpecifier().ui {
                inlineHtml(
                    '<div class="alert alert-info">' +
                    message +
                    '</div>',
                    'plmSearchMessage'
                )
            })
        }
    }
}