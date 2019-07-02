package com.elovirta.kuhnuri;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {

    private void run(final String[] args) throws Exception {
        System.out.println("Run DITA-OT: " + String.join(" ", args));

        final URI in = download(new URI(System.getProperty("input")));
        final Path out = getTempDir();

        final Properties props = new Properties();
        props.setProperty("args.input", in.toString());
        props.setProperty("output.dir", out.toString());

        new org.apache.tools.ant.Main().startAnt(args, props, null);

        upload(out, new URI(System.getProperty("output")));
    }

    private void upload(final Path out, final URI input) {
    }

    // Download or pass as is to OT
    private URI download(final URI input) throws Exception {
        switch (input.getScheme()) {
            case "s3":
            case "jar":
                return downloadFile(input, getTempDir()).toUri();
            default: {
                return input;
            }
        }
    }

    private void unzip(final Path jar, final Path tempDir) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar, StandardOpenOption.READ))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                Files.copy(in, tempDir.resolve(entry.getName()));
                in.closeEntry();
            }
        }
    }

    private void zip(final Path tempDir, final Path jar) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar, StandardOpenOption.WRITE))) {
            Files.walk(tempDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        final Path name = tempDir.relativize(path);
                        final ZipEntry entry = new ZipEntry(name.toString());
                        try {
                            out.putNextEntry(entry);
                            Files.copy(path, out);
                            out.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private Path downloadFile(final URI input, final Path tempDir) throws Exception {
        switch (input.getScheme()) {
            case "s3":
                return downloadFromS3(input, tempDir);
            case "jar":
                final JarUri jarUri = JarUri.of(input);
                final Path jar = downloadFile(jarUri.url, tempDir);
                unzip(jar, tempDir);
                Files.delete(jar);
                return tempDir.resolve(jarUri.entry);
            default:
                throw new IllegalArgumentException(input.toString());
        }
    }

    private static class JarUri {
        public final URI url;
        public final String entry;

        private JarUri(URI url, String entry) {
            this.url = url;
            this.entry = entry;
        }

        public static JarUri of(final URI in) {
            if (!in.getScheme().equals("jar")) {
                throw new IllegalArgumentException(in.toString());
            }
            final String s = in.toString();
            final int i = s.indexOf("!/");
            return new JarUri(URI.create(s.substring(4, i)), s.substring(i + 2));
        }

    }


    private Path downloadFromHttp(final URI in, final Path tempDir) throws IOException, InterruptedException {
        final String fileName = getName(in);
        final Path file = tempDir.resolve(fileName);
        System.out.println("Download " + in + " to " + file);
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(in)
                .build();
        HttpResponse<Path> response = client.send(request, BodyHandlers.ofFile(file));
        return response.body();
    }

    private Path downloadFromS3(final URI in, final Path tempDir) throws IOException, InterruptedException {
        final String fileName = getName(in);
        final Path file = tempDir.resolve(fileName);
        System.out.println("Download S3 " + in + " to " + file);
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(in)
                .build();
        HttpResponse<Path> response = client.send(request, BodyHandlers.ofFile(file));
        return response.body();
    }

    private String getName(final URI in) {
        final String path = in.getPath();
        final int i = path.lastIndexOf('/');
        return i != -1 ? path.substring(i + 1) : path;
    }

    private Path getTempDir() throws IOException {
        return Files.createTempDirectory("tmp");
    }

    public static void main(final String[] args) {
        try {
            (new Main()).run(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
