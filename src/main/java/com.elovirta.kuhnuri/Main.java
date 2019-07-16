package com.elovirta.kuhnuri;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Main class to download input, run DITA-OT, and upload output.
 */
public class Main extends org.apache.tools.ant.Main {

    static final String ENV_INPUT = "input";
    static final String ENV_OUTPUT = "output";

    private HttpClient client;
    private AmazonS3 s3client;
    private TransferManager transferManager;

    private void init() {
        client = HttpClient.newBuilder()
                .followRedirects(Redirect.NORMAL)
                .build();
        s3client = AmazonS3ClientBuilder
                .standard()
                .build();
        transferManager = TransferManagerBuilder
                .standard()
                .withS3Client(s3client)
                .build();
    }

    private void cleanup() {
        transferManager.shutdownNow();
    }

    /**
     * Download input, run DITA-OT, and upload output.
     */
    private void run(final String[] args) throws Exception {
        try {
            init();

            System.out.println(String.format("Run DITA-OT: %s", String.join(" ", args)));

            final URI in = download(new URI(System.getenv(ENV_INPUT)));
            final Path out = getTempDir("out");

            final Properties props = new Properties();
            props.setProperty("args.input", in.toString());
            props.setProperty("output.dir", out.toString());

            startAnt(args, props, null);

            upload(out, new URI(System.getenv(ENV_OUTPUT)));
        } finally {
            cleanup();
        }
    }

    @Override
    protected void exit(final int exitCode) {
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private void upload(final Path outDirOrFile, final URI output) throws IOException, InterruptedException {
        switch (output.getScheme()) {
            case "s3":
                uploadToS3(outDirOrFile, output);
                break;
            case "jar":
                final JarUri jarUri = JarUri.of(output);
                final Path jar = Files.createTempFile("out", ".jar");
                ZipUtils.zip(outDirOrFile, jar);
                upload(jar, jarUri.url);
                Files.delete(jar);
                break;
            case "http":
            case "https":
                uploadToHttp(outDirOrFile, output);
                break;
            default:
                throw new IllegalArgumentException(output.toString());
        }
    }

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

    private Path downloadFile(final URI input, final Path tempDir) throws Exception {
        switch (input.getScheme()) {
            case "s3":
                return downloadFromS3(input, tempDir);
            case "jar":
                final JarUri jarUri = JarUri.of(input);
                final Path jar = downloadFile(jarUri.url, tempDir);
                ZipUtils.unzip(jar, tempDir);
                Files.delete(jar);
                return tempDir.resolve(jarUri.entry);
            case "http":
            case "https":
                return downloadFromHttp(input, tempDir);
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
        final HttpResponse<Path> response = client.send(request, BodyHandlers.ofFile(file));
        return response.body();
    }

    private Path downloadFromS3(final URI in, final Path tempDir) throws InterruptedException {
        final AmazonS3URI s3Uri = new AmazonS3URI(in);
        final String fileName = getName(in);
        final Path file = tempDir.resolve(fileName);
        System.out.println(String.format("Download %s to %s", in, file));
        final Transfer transfer = transferManager.download(s3Uri.getBucket(), s3Uri.getKey(), file.toFile());
        transfer.waitForCompletion();
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
        final Transfer transfer = Files.isDirectory(dirOrFile)
                ? transferManager.uploadDirectory(s3Uri.getBucket(), s3Uri.getKey(), dirOrFile.toFile(), true)
                : transferManager.upload(s3Uri.getBucket(), s3Uri.getKey(), dirOrFile.toFile());
        transfer.waitForCompletion();
    }

    private String getName(final URI in) {
        final String path = in.getPath();
        final int i = path.lastIndexOf('/');
        return i != -1 ? path.substring(i + 1) : path;
    }

    private Path getTempDir(final String prefix) throws IOException {
        return Files.createTempDirectory(prefix + "_");
    }

    public static void main(final String[] args) {
        try {
            (new Main()).run(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
