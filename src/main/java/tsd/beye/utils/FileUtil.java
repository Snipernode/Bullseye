package tsd.beye.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private FileUtil() {
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }

        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw ex;
        }
    }

    public static void zipDirectory(Path source, Path zipPath) throws IOException {
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(source)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    Path relative = source.relativize(path);
                    try (InputStream input = Files.newInputStream(path)) {
                        ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/'));
                        zipOut.putNextEntry(entry);
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, read);
                        }
                        zipOut.closeEntry();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        }
    }
}
