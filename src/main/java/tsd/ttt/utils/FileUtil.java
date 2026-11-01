package tsd.ttt.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private FileUtil() {
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        if (source == null || target == null || !Files.exists(source)) {
            return;
        }

        List<Path> paths;
        try (Stream<Path> stream = Files.walk(source)) {
            paths = stream.toList();
        }

        for (Path path : paths) {
            Path relative = source.relativize(path);
            Path destination = target.resolve(relative);
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination);
            } else {
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void deleteDirectory(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        List<Path> paths;
        try (Stream<Path> stream = Files.walk(path)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }

        for (Path candidate : paths) {
            Files.deleteIfExists(candidate);
        }
    }

    public static void zipDirectory(Path source, Path zipPath) throws IOException {
        Path target = zipPath.toAbsolutePath();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Output archive path has no parent directory: " + zipPath);
        }
        Files.createDirectories(parent);

        Path staging = Files.createTempFile(parent, target.getFileName().toString(), ".part");
        try {
            List<Path> files;
            try (Stream<Path> stream = Files.walk(source)) {
                files = stream.filter(path -> !Files.isDirectory(path)).sorted().toList();
            }

            try (OutputStream fileOut = Files.newOutputStream(staging);
                 ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
                for (Path path : files) {
                    Path relative = source.relativize(path);
                    zipOut.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    try (InputStream input = Files.newInputStream(path)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, read);
                        }
                    }
                    zipOut.closeEntry();
                }
            }

            moveIntoPlace(staging, target);
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(staging);
            throw ex;
        }
    }

    private static void moveIntoPlace(Path staging, Path target) throws IOException {
        try {
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}