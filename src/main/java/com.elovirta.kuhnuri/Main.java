package com.elovirta.kuhnuri;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {

    private final HttpClient client = HttpClient.newHttpClient();
    private final AmazonS3 s3client = AmazonS3ClientBuilder
            .standard()
//                .withCredentials(new DefaultAWSCredentialsProviderChain())
            .withRegion(Regions.fromName("eu-west-1"))
            .build();

    private void run(final String[] args) throws Exception {
        System.out.println(String.format("Run DITA-OT: %s", String.join(" ", args)));

        final URI in = download(new URI(System.getenv("input")));
        final Path out = getTempDir("out");

        final Properties props = new Properties();
        props.setProperty("args.input", in.toString());
        props.setProperty("output.dir", out.toString());

        new org.apache.tools.ant.Main().startAnt(args, props, null);

        upload(out, new URI(System.getenv("output")));
    }

    private void upload(final Path outDirOrFile, final URI output) throws IOException, InterruptedException {
        switch (output.getScheme()) {
            case "s3":
                uploadToS3(outDirOrFile, output);
            case "jar":
                final JarUri jarUri = JarUri.of(output);
                final Path jar = Files.createTempFile("out", ".jar");
                zip(outDirOrFile, jar);
                upload(jar, jarUri.url);
                Files.delete(jar);
            case "http":
            case "https":
                uploadToHttp(outDirOrFile, output);
            default:
                throw new IllegalArgumentException(output.toString());
        }
    }

    // Download or pass as is to OT
    private URI download(final URI input) throws Exception {
        switch (input.getScheme()) {
            case "s3":
            case "jar":
                return downloadFile(input, getTempDir("in")).toUri();
            default: {
                return input;
            }
        }
    }

    private void unzip(final Path jar, final Path tempDir) throws IOException {
        System.out.println(String.format("Unzip %s to %s", jar, tempDir));
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar, StandardOpenOption.READ))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                Files.copy(in, tempDir.resolve(entry.getName()));
                in.closeEntry();
            }
        }
    }

    private void zip(final Path tempDir, final Path jar) throws IOException {
        System.out.println(String.format("Zip %s to %s", tempDir, jar));
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

    private Path downloadFromHttp(final URI in, final Path tempDir) throws IOException, InterruptedException {
        final String fileName = getName(in);
        final Path file = tempDir.resolve(fileName);
        System.out.println(String.format("Download %s to %s", in, file));
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(in)
                .build();
        HttpResponse<Path> response = client.send(request, BodyHandlers.ofFile(file));
        return response.body();
    }

    private Path downloadFromS3(final URI in, final Path tempDir) throws InterruptedException {
        final AmazonS3URI s3Uri = new AmazonS3URI(in);
        final String fileName = getName(in);
        final Path file = tempDir.resolve(fileName);
        final TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3client).build();
        final Transfer transfer = tx.download(s3Uri.getBucket(), s3Uri.getKey(), file.toFile());
        System.out.println(String.format("Download %s to %s", in, file));
        transfer.waitForCompletion();
        tx.shutdownNow();
        return file;
    }

    private void uploadToHttp(final Path dirOrFile, final URI output) throws IOException {
        final Stream<Path> file = Files.isDirectory(dirOrFile)
                ? Files.walk(dirOrFile).filter(path -> !Files.isDirectory(path))
                : Stream.of(dirOrFile);
        file.parallel().forEach(path -> {
            try {
                System.out.println(String.format("Upload %s to %s", path, output));
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(output)
                        .POST(BodyPublishers.ofFile(path))
                        .build();
                client.sendAsync(request, BodyHandlers.discarding()).join();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void uploadToS3(final Path dirOrFile, final URI output) throws InterruptedException {
        System.out.println(String.format("Upload %s to %s", dirOrFile, output));
        final AmazonS3URI s3Uri = new AmazonS3URI(output);
        final TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3client).build();
        final Transfer transfer = Files.isDirectory(dirOrFile)
                ? tx.uploadDirectory(s3Uri.getBucket(), s3Uri.getKey(), dirOrFile.toFile(), true)
                : tx.upload(s3Uri.getBucket(), s3Uri.getKey(), dirOrFile.toFile());
        transfer.waitForCompletion();
        tx.shutdownNow();
    }


    private String getName(final URI in) {
        final String path = in.getPath();
        final int i = path.lastIndexOf('/');
        return i != -1 ? path.substring(i + 1) : path;
    }

    private Path getTempDir(final String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    public static void main(final String[] args) {
        try {
            (new Main()).run(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
