package plm

import org.springframework.beans.factory.annotation.Value

/** Revision-specific GLB storage. Does not mutate released CAD data. */
class PlmPreviewStorageService {
    @Value('${plm.preview.storage:${PLM_PREVIEW_STORAGE:/var/lib/parts-manager/plm-previews}}')
    String storageDirectory

    /** Resolve an immutable preview path by original part ID and exact CAD version.
     *  Historical snapshot rows have different IDs from the active part.
     */
    File modelFileForVersion(Long originalPartId, Integer major, Integer minor) {
        if (originalPartId == null || originalPartId < 1L || major == null || major < 1 || minor == null || minor < 0)
            throw new IllegalArgumentException('Invalid part ID or CAD version')
        new File(new File(new File(storageDirectory, originalPartId.toString()), "${major}.${minor}"), 'model.glb')
    }

    File modelFile(PlmFreeCadPart part) {
        // Only server-generated numeric identifiers are used in the path.
        String version = "${part.majorVersion == null ? 1 : part.majorVersion}.${part.minorVersion == null ? 0 : part.minorVersion}"
        new File(new File(new File(storageDirectory, part.id.toString()), version), 'model.glb')
    }

    /** X3D previews are stored separately from GLB for the same CAD revision. */
    File x3dFile(PlmFreeCadPart part) {
        String version = "${part.majorVersion == null ? 1 : part.majorVersion}.${part.minorVersion == null ? 0 : part.minorVersion}"
        new File(new File(new File(storageDirectory, part.id.toString()), version), 'model.x3d')
    }

    void storeX3d(PlmFreeCadPart part, InputStream input, long size, String name) {
        if (!name?.toLowerCase(java.util.Locale.ROOT)?.endsWith('.x3d'))
            throw new IllegalArgumentException('Only .x3d files are accepted')
        if (size < 10L || size > 250L * 1024L * 1024L)
            throw new IllegalArgumentException('X3D must be between 10 bytes and 250 MB')
        File target = x3dFile(part)
        if (!target.parentFile.isDirectory() && !target.parentFile.mkdirs())
            throw new IllegalArgumentException('Cannot create preview storage directory')
        File temporary = File.createTempFile('preview-x3d-', '.tmp', target.parentFile)
        try {
            temporary.withOutputStream { out ->
                byte[] buf = new byte[65536]
                int n
                while ((n = input.read(buf)) != -1) out.write(buf, 0, n)
            }
            if (temporary.length() != size) throw new IllegalArgumentException('Incomplete X3D upload')
            // FreeCAD exports a standard X3D 3.3 PUBLIC DOCTYPE. Allow that
            // declaration, but never load the remote DTD or expand entities.
            String header = new String(java.nio.file.Files.readAllBytes(temporary.toPath()), 'UTF-8')
            String standardDoctype = /(?is)<!DOCTYPE\s+X3D\s+PUBLIC\s+["']ISO\/\/Web3D\/\/DTD X3D 3\.3\/\/EN["']\s+["']https?:\/\/www\.web3d\.org\/specifications\/x3d-3\.3\.dtd["']\s*>/
            String withoutStandardDoctype = header.replaceFirst(standardDoctype, '')
            if ((withoutStandardDoctype =~ /(?is)<!DOCTYPE|<!ENTITY/).find())
                throw new IllegalArgumentException('Unsupported DTD or XML entity declaration')
            try {
                javax.xml.parsers.SAXParserFactory factory = javax.xml.parsers.SAXParserFactory.newInstance()
                factory.setNamespaceAware(true)
                factory.setFeature('http://xml.org/sax/features/external-general-entities', false)
                factory.setFeature('http://xml.org/sax/features/external-parameter-entities', false)
                factory.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
                javax.xml.parsers.SAXParser parser = factory.newSAXParser()
                // Xerces versions bundled by Grails may not recognize JAXP
                // ACCESS_EXTERNAL_DTD/SCHEMA properties. SAX feature flags
                // above disable external entity and DTD retrieval instead.
                boolean[] rootIsX3d = [false] as boolean[]
                def handler = new org.xml.sax.helpers.DefaultHandler() {
                    boolean sawRoot = false
                    @Override
                    void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
                        if (!sawRoot) {
                            sawRoot = true
                            rootIsX3d[0] = (localName == 'X3D' || qName == 'X3D')
                        }
                    }
                }
                // Deny any attempt to resolve an external identifier, even if
                // a parser implementation ignores a feature flag.
                parser.XMLReader.setEntityResolver(new org.xml.sax.EntityResolver() {
                    @Override
                    org.xml.sax.InputSource resolveEntity(String publicId, String systemId) {
                        return new org.xml.sax.InputSource(new java.io.StringReader(''))
                    }
                })
                temporary.withInputStream { stream -> parser.parse(stream, handler) }
                if (!rootIsX3d[0]) throw new IllegalArgumentException('XML root element must be X3D')
            } catch (IllegalArgumentException ex) {
                throw ex
            } catch (Exception ex) {
                throw new IllegalArgumentException('Invalid XML X3D document: ' + ex.message, ex)
            }
            java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    void store(PlmFreeCadPart part, InputStream input, long size, String name) {
        if (!name?.toLowerCase(java.util.Locale.ROOT)?.endsWith('.glb'))
            throw new IllegalArgumentException('Only .glb files are accepted')
        if (size < 12L || size > 250L * 1024L * 1024L)
            throw new IllegalArgumentException('GLB must be between 12 bytes and 250 MB')
        File target = modelFile(part)
        if (!target.parentFile.isDirectory() && !target.parentFile.mkdirs())
            throw new IllegalArgumentException('Cannot create preview storage directory')
        File temporary = File.createTempFile('preview-', '.tmp', target.parentFile)
        try {
            temporary.withOutputStream { out ->
                byte[] buf = new byte[65536]
                int n
                while ((n = input.read(buf)) != -1) out.write(buf, 0, n)
            }
            if (temporary.length() != size) throw new IllegalArgumentException('Incomplete GLB upload')
            temporary.withInputStream { stream ->
                byte[] magic = new byte[4]
                if (stream.read(magic) != 4 || new String(magic, 'US-ASCII') != 'glTF')
                    throw new IllegalArgumentException('Invalid GLB header')
            }
            java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
