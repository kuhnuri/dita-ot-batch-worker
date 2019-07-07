package com.elovirta.kuhnuri;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static void unzip(final Path jar, final Path tempDir) throws IOException {
        System.out.println(String.format("Unzip %s to %s", jar, tempDir));
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar, StandardOpenOption.READ))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (!entry.isDirectory()) {
                    final Path file = tempDir.resolve(entry.getName());
                    final Path parent = file.getParent();
                    if (!Files.exists(parent)) {
                        System.out.println(String.format("Create directory %s", parent));
                        Files.createDirectories(parent);
                    }
                    System.out.println(String.format("Copy %s to %s", entry.getName(), file));
                    Files.copy(in, file);
                }
                in.closeEntry();
            }
        }
    }

    public static void zip(final Path tempDir, final Path jar) throws IOException {
        System.out.println(String.format("Zip %s to %s", tempDir, jar));
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar, StandardOpenOption.WRITE))) {
            Files.walk(tempDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        final Path name = tempDir.relativize(path);
                        final ZipEntry entry = new ZipEntry(name.toString());
                        try {
                            out.putNextEntry(entry);
                            System.out.println(String.format("Copy %s to %s", tempDir, entry.getName()));
                            Files.copy(path, out);
                            out.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

}
