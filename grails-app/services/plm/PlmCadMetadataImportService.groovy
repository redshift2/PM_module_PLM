package plm

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Path
import java.util.zip.ZipFile

/** Import validated CAD_TO_META outputs without modifying editable PLM fields. */
class PlmCadMetadataImportService {
    PlmCadWeightService plmCadWeightService
    PlmGeneratedAttachmentImportService plmGeneratedAttachmentImportService
    @Transactional
    void importResult(Object job, Path resultZip) {
        if (job?.jobType?.toString() != 'CAD_TO_META') return
        Map parameters = (Map) new JsonSlurper().parseText(job.parametersJson ?: '{}')
        ZipFile archive = new ZipFile(resultZip.toFile())
        try {
            Map metadata = readJson(archive, 'metadata.json', true)
            Map identity = metadata.job instanceof Map ? (Map) metadata.job : [:]
            String idText = (parameters.partId ?: identity.partId ?: '').toString()
            if (!(idText ==~ /[0-9]+/)) throw new IllegalArgumentException('Missing PLM part ID')
            Long partId = Long.valueOf(idText)
            PlmFreeCadPart part = PlmFreeCadPart.get(partId)
            if (!part) throw new IllegalArgumentException('Part not found: ' + partId)
            String version = "${part.majorVersion ?: 1}.${part.minorVersion ?: 0}"
            String jobVersion = (identity.cadVersion ?: parameters.cadVersion ?: '').toString()
            String jobSha = (identity.cadSha1 ?: parameters.cadSha1 ?: '').toString()
            if (jobVersion != version || !jobSha || !jobSha.equalsIgnoreCase(part.plmContentShaOne ?: ''))
                throw new IllegalStateException('Stale CAD_TO_META result: revision or SHA-1 mismatch')
            if (parameters.partId && parameters.partId.toString() != idText)
                throw new IllegalArgumentException('Job part ID mismatch')
            if (parameters.cadVersion && parameters.cadVersion.toString() != jobVersion)
                throw new IllegalArgumentException('Job CAD version mismatch')
            if (parameters.cadSha1 && !parameters.cadSha1.toString().equalsIgnoreCase(jobSha))
                throw new IllegalArgumentException('Job CAD SHA-1 mismatch')
            String kind = (metadata.kind ?: '').toString()
            if (!(kind in ['part','assembly'])) throw new IllegalArgumentException('Unknown CAD kind')
            Map bom = readJson(archive, 'bom.json', kind == 'assembly')
            Map assembly = readJson(archive, 'assembly.asmj', kind == 'assembly')
            if (kind == 'assembly' && (bom.scope != 'immediate-children' || !(bom.items instanceof List)))
                throw new IllegalArgumentException('Invalid one-level assembly BOM')
            part.cadMetadataJson = JsonOutput.toJson(metadata)
            if (kind == 'assembly') {
                resolveComponentIds(bom, assembly)
                log.info("CAD_TO_META part ${part.id}: BOM rows=${bom?.items?.size() ?: 0}, assembly nodes=${assembly?.nodes?.size() ?: 0}")
            }
            part.cadBomJson = bom == null ? null : JsonOutput.toJson(bom)
            part.cadAssemblyJson = assembly == null ? null : JsonOutput.toJson(assembly)
            part.cadDocumentKind = kind
            part.cadLengthUnit = (metadata.units instanceof Map ? metadata.units.geometryLength : null)?.toString()?.take(32)
            part.cadVolumeMm3 = null
            part.cadMaterialName = null
            if (kind == 'part' && metadata.part instanceof Map) {
                Map p = (Map) metadata.part
                if (p.volumeMm3 instanceof Number && p.volumeMm3.doubleValue() >= 0)
                    part.cadVolumeMm3 = new BigDecimal(p.volumeMm3.toString())
                String material = (p.material instanceof Map ? p.material.name : null)?.toString()?.trim()
                if (material && !material.equalsIgnoreCase('Default')) part.cadMaterialName = material.take(255)
            }
            if (kind == 'part') {
                String selectedMaterial = part.material?.trim() ?: part.cadMaterialName?.trim()
                BigDecimal calculatedWeight = plmCadWeightService.calculate(part.cadVolumeMm3, selectedMaterial)
                if (calculatedWeight != null) {
                    part.weight = calculatedWeight
                    log.info('CAD weight updated for part ' + part.id + ': ' + calculatedWeight + ' kg')
                } else {
                    log.warn('CAD weight not updated for part ' + part.id + ': volume=' + part.cadVolumeMm3 + ', material=' + selectedMaterial)
                }
            }
            part.save(failOnError:true, flush:true)
            // The METADATA route never invokes the generic ATTACHMENTS importer.
            // Persist the resolved assembly JSON (not the original worker ZIP entry).
            if (kind == 'assembly') {
                plmGeneratedAttachmentImportService.saveResolvedAssemblyJson(job, part, JsonOutput.toJson(assembly))
            }
        } finally { archive.close() }
    }

    /** Resolve components using only the PLM catalogue; worker output remains untouched.
     *  Ambiguous filenames are deliberately left unresolved.
     */
    private void resolveComponentIds(Map bom, Map assembly) {
        List<PlmFreeCadPart> latest = PlmFreeCadPart.findAllByActive(true).findAll {
            PlmFreeCadPart p -> p.nextVersion == null
        }
        Map<String, PlmFreeCadPart> byKey = [:]
        List items = bom?.items instanceof List ? (List) bom.items : []
        items.each { Object value ->
            if (!(value instanceof Map)) return
            Map row = (Map) value
            PlmFreeCadPart match = findComponent(row, latest)
            if (!match) {
                String source = normalizedSource(row.sourceFile)
                List candidates = source ? matchesForSource(source, latest) : []
                log.warn("CAD BOM unresolved sourceFile='${row.sourceFile}' normalized='${source}' matches=${candidates.size()} candidateIds=${candidates.collect { it.id }}")
                return
            }
            applyResolvedMetadata(row, match)
            String key = row.componentKey?.toString() ?: row.key?.toString()
            if (key) byKey[key] = match
            String source = normalizedSource(row.sourceFile)
            if (source) {
                // Do not use a source-file shortcut when multiple distinct matches exist.
                List<PlmFreeCadPart> matches = matchesForSource(source, latest)
                if (matches.size() == 1) byKey['source:' + source.toLowerCase(java.util.Locale.ROOT)] = match
            }
        }
        List nodes = assembly?.nodes instanceof List ? (List) assembly.nodes : []
        resolveNodes(nodes, latest, byKey)
    }

    private void resolveNodes(List nodes, List<PlmFreeCadPart> latest,
                                     Map<String, PlmFreeCadPart> byKey) {
        nodes.each { Object value ->
            if (!(value instanceof Map)) return
            Map node = (Map) value
            String key = node.componentKey?.toString() ?: node.key?.toString()
            PlmFreeCadPart match = key ? byKey[key] : null
            if (!match) {
                String source = normalizedSource(node.sourceFile)
                if (source) match = byKey['source:' + source.toLowerCase(java.util.Locale.ROOT)]
            }
            if (!match) match = findComponent(node, latest)
            if (match) {
                applyResolvedMetadata(node, match)
                log.info("CAD assembly resolved sourceFile='${node.sourceFile}' -> partId=${match.id}")
            } else if (node.sourceFile) {
                String source = normalizedSource(node.sourceFile)
                List candidates = matchesForSource(source, latest)
                log.warn("CAD assembly unresolved sourceFile='${node.sourceFile}' normalized='${source}' matches=${candidates.size()} candidateIds=${candidates.collect { it.id }}")
            }
            if (node.children instanceof List) resolveNodes((List) node.children, latest, byKey)
        }
    }

    private static PlmFreeCadPart findComponent(Map row, List<PlmFreeCadPart> latest) {
        String idText = (row.partId ?: row.id ?: '').toString()
        if (idText ==~ /[0-9]+/) {
            Long id = Long.valueOf(idText)
            return latest.find { PlmFreeCadPart p -> p.id == id }
        }
        String source = normalizedSource(row.sourceFile)
        if (!source) return null
        List<PlmFreeCadPart> matches = matchesForSource(source, latest)
        return matches.size() == 1 ? matches[0] : null
    }

    /** Compare normalized basenames and stems, without guessing among duplicates. */
    private static List<PlmFreeCadPart> matchesForSource(String source,
                                                          List<PlmFreeCadPart> latest) {
        String wanted = sourceStem(source)
        if (!wanted) return []
        latest.findAll { PlmFreeCadPart p ->
            String original = sourceStem(p.originalName)
            String label = sourceStem(p.label)
            (original && original.equalsIgnoreCase(wanted)) ||
                (label && label.equalsIgnoreCase(wanted))
        }
    }

    private static String sourceStem(Object value) {
        String text = value?.toString()?.trim()?.replace('\\', '/')
        if (!text) return null
        String base = text.tokenize('/')?.last()?.trim()
        return base?.replaceFirst(/(?i)(\.fcstd)+$/, '')?.trim()
    }

    private static String normalizedSource(Object value) {
        String text = value?.toString()?.trim()?.replace('\\', '/')
        return text ? text.tokenize('/')?.last()?.trim() : null
    }

    private static void applyResolvedMetadata(Map component, PlmFreeCadPart part) {
        String kind = part.cadDocumentKind?.toLowerCase(java.util.Locale.ROOT)
        // A missing CAD kind is not evidence that the component is a part.
        String componentType = kind in ['part', 'assembly'] ? kind : 'unknown'
        component.partId = part.id
        component.id = part.id
        component.componentType = componentType
        component.type = 'resolvedComponent'
        component.source = 'plm'
        // Do not modify sourceFile, componentKey, transform or placement fields.
    }

    private static Map readJson(ZipFile zip, String name, boolean required) {
        def entry = zip.getEntry(name)
        if (!entry) {
            if (required) throw new IllegalArgumentException('Missing ' + name)
            return null
        }
        if (entry.directory || entry.size > 10L * 1024 * 1024) throw new IllegalArgumentException('Invalid ' + name)
        byte[] bytes = zip.getInputStream(entry).withCloseable { it.bytes }
        if (bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException('Oversized ' + name)
        def parsed = new JsonSlurper().parseText(new String(bytes, 'UTF-8'))
        if (!(parsed instanceof Map)) throw new IllegalArgumentException('Invalid JSON object: ' + name)
        (Map) parsed
    }
}
