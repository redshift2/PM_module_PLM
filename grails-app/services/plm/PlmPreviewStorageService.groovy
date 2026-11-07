package plm

import org.springframework.beans.factory.annotation.Value

/** Revision-specific GLB storage. Does not mutate released CAD data. */
class PlmPreviewStorageService {
    @Value('${plm.preview.storage:${PLM_PREVIEW_STORAGE:/var/lib/parts-manager/plm-previews}}')
    String storageDirectory

    File modelFile(PlmFreeCadPart part) {
        // Only server-generated numeric identifiers are used in the path.
        String version = "${part.majorVersion == null ? 1 : part.majorVersion}.${part.minorVersion == null ? 0 : part.minorVersion}"
        new File(new File(new File(storageDirectory, part.id.toString()), version), 'model.glb')
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
