/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TypstCompiler {
    private static final String TYPST_BIN = System.getenv().getOrDefault("TYPST_BIN", "typst");

    private TypstCompiler() {}

    public static byte[] compile(String source) throws IOException, InterruptedException {
        return compile(source, Map.of());
    }

    public static byte[] compile(String source, Map<String, byte[]> resources)
            throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("typst-compile-");
        try {
            Path typFile = tempDir.resolve("document.typ");
            Path pdfFile = tempDir.resolve("document.pdf");
            Files.writeString(typFile, source);
            for (var entry : resources.entrySet()) {
                Path resFile = tempDir.resolve(entry.getKey());
                FilePaths.createParentDirectories(resFile);
                Files.write(resFile, entry.getValue());
            }
            return runTypst(tempDir, typFile, pdfFile);
        } finally {
            cleanup(tempDir);
        }
    }

    public static byte[] compileTemplate(Map<String, Object> data, String templateName, @Nullable StationLogo logo)
            throws IOException, InterruptedException {
        return compileTemplate(data, templateName, logo, Map.of());
    }

    /**
     * Renders a template, additionally writing {@code resources} next to it. A template reads them
     * with {@code read()} or {@code eval(read(...))}, which is how content too structured for
     * {@code data.json} - a Typst markup fragment, say - reaches the document.
     */
    public static byte[] compileTemplate(
            Map<String, Object> data, String templateName, @Nullable StationLogo logo, Map<String, String> resources)
            throws IOException, InterruptedException {
        return compileTemplate(data, templateName, logo, resources, Map.of());
    }

    /**
     * The same, additionally writing {@code files} next to the template byte for byte, for what is
     * not text: a picture the document shows with {@code image()}, say.
     */
    public static byte[] compileTemplate(
            Map<String, Object> data,
            String templateName,
            @Nullable StationLogo logo,
            Map<String, String> resources,
            Map<String, byte[]> files)
            throws IOException, InterruptedException {
        return compileTemplate(data, templateName, logo, resources, files, Output.PDF);
    }

    /**
     * The same, producing the given kind of PDF. {@link Output#PDF_A_3B} is for a document that is
     * kept as a record and may be signed later: Typst refuses to produce it where the document breaks
     * the standard, so a template meant for it fails loudly rather than producing something that only
     * looks archival.
     */
    public static byte[] compileTemplate(
            Map<String, Object> data,
            String templateName,
            @Nullable StationLogo logo,
            Map<String, String> resources,
            Map<String, byte[]> files,
            Output output)
            throws IOException, InterruptedException {
        return compileTemplate(data, templateName, logo, resources, files, output, Map.of());
    }

    /**
     * The same, additionally offering Typst the given font files by name. They are written into a
     * directory of their own that Typst searches before the system's fonts ({@code --font-path}), and it
     * goes with the rest of the temporary files when the document is done. A template asks for them by
     * the family names the files carry.
     */
    public static byte[] compileTemplate(
            Map<String, Object> data,
            String templateName,
            @Nullable StationLogo logo,
            Map<String, String> resources,
            Map<String, byte[]> files,
            Output output,
            Map<String, byte[]> fonts)
            throws IOException, InterruptedException {
        return compileTemplate(data, templateName, logo, resources, files, output, fonts, List.of());
    }

    /**
     * The same, additionally searching the given directories for fonts, after the files handed over and
     * before the system's fonts. They are read where they are, which suits fonts every document of the
     * instance may print in better than writing them out for each one.
     */
    public static byte[] compileTemplate(
            Map<String, Object> data,
            String templateName,
            @Nullable StationLogo logo,
            Map<String, String> resources,
            Map<String, byte[]> files,
            Output output,
            Map<String, byte[]> fonts,
            List<Path> fontDirectories)
            throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("typst-template-");
        try {
            Path templateSource = Path.of("templates", "typst", templateName);
            Path templateFile = tempDir.resolve(templateName);
            FilePaths.createParentDirectories(templateFile);
            Path templateDir = Objects.requireNonNull(
                    templateFile.getParent(), "the template is resolved inside the temporary directory");

            if (logo != null) {
                String ext = logoExtension(logo.contentType());
                Files.write(templateDir.resolve("logo." + ext), logo.data());
                data.put("hasLogo", true);
                data.put("logoFile", "logo." + ext);
            }

            Files.writeString(templateDir.resolve("data.json"), Json.MAPPER.writeValueAsString(data));
            for (var entry : resources.entrySet()) {
                Files.writeString(templateDir.resolve(entry.getKey()), entry.getValue());
            }
            for (var entry : files.entrySet()) {
                Files.write(templateDir.resolve(entry.getKey()), entry.getValue());
            }
            Files.copy(templateSource, templateFile);
            Path outputFile = tempDir.resolve("output.pdf");
            return runTypst(tempDir, templateFile, outputFile, output, fontPath(tempDir, fonts, fontDirectories));
        } finally {
            cleanup(tempDir);
        }
    }

    private static String logoExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/svg+xml" -> "svg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    /**
     * The directories Typst searches for fonts: the files handed over first, written beside the
     * document, then the directories named.
     */
    private static List<Path> fontPath(Path tempDir, Map<String, byte[]> fonts, List<Path> fontDirectories)
            throws IOException {
        var fontPath = new ArrayList<Path>();
        Path fontDir = writeFonts(tempDir, fonts);
        if (fontDir != null) fontPath.add(fontDir);
        fontDirectories.forEach(directory -> fontPath.add(directory.toAbsolutePath()));
        return fontPath;
    }

    /**
     * Writes font files into a directory of their own beside the document, each under its bare file
     * name.
     *
     * @return the directory, or null where there are no fonts
     */
    private static @Nullable Path writeFonts(Path tempDir, Map<String, byte[]> fonts) throws IOException {
        if (fonts.isEmpty()) return null;
        Path fontDir = Files.createDirectory(tempDir.resolve("fonts"));
        for (var entry : fonts.entrySet()) {
            Files.write(fontDir.resolve(FilePaths.nameOf(Path.of(entry.getKey()))), entry.getValue());
        }
        return fontDir;
    }

    /**
     * Draws a one-page document as a PNG picture, its background left transparent where the page has
     * no fill. The source reads {@code resources} written next to it; the font files are written into
     * a directory of their own and searched, then {@code fontDirectories}, before the system's fonts.
     *
     * @param source          the document
     * @param resources       text files written next to it, by name
     * @param fonts           font files, by name
     * @param fontDirectories further directories of fonts
     * @param ppi             how many pixels an inch of the page is drawn in
     * @return the picture
     */
    public static byte[] compilePng(
            String source,
            Map<String, String> resources,
            Map<String, byte[]> fonts,
            List<Path> fontDirectories,
            int ppi)
            throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("typst-png-");
        try {
            Path typFile = tempDir.resolve("document.typ");
            Files.writeString(typFile, source);
            for (var entry : resources.entrySet()) {
                Files.writeString(tempDir.resolve(entry.getKey()), entry.getValue());
            }
            return runTypst(
                    tempDir,
                    typFile,
                    tempDir.resolve("document.png"),
                    List.of("--format", "png", "--ppi", String.valueOf(ppi)),
                    fontPath(tempDir, fonts, fontDirectories));
        } finally {
            cleanup(tempDir);
        }
    }

    private static byte[] runTypst(Path workDir, Path inputFile, Path outputFile)
            throws IOException, InterruptedException {
        return runTypst(workDir, inputFile, outputFile, Output.PDF, List.of());
    }

    private static byte[] runTypst(Path workDir, Path inputFile, Path outputFile, Output output, List<Path> fontPath)
            throws IOException, InterruptedException {
        var options = output == Output.PDF_A_3B ? List.of("--pdf-standard", "a-3b") : List.<String>of();
        return runTypst(workDir, inputFile, outputFile, options, fontPath);
    }

    private static byte[] runTypst(
            Path workDir, Path inputFile, Path outputFile, List<String> options, List<Path> fontPath)
            throws IOException, InterruptedException {
        var command = new ArrayList<>(List.of(TYPST_BIN, "compile"));
        command.addAll(options);
        if (!fontPath.isEmpty()) {
            String joined = fontPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
            command.addAll(List.of("--font-path", joined));
        }
        command.addAll(List.of(inputFile.toString(), outputFile.toString()));
        var process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        String printed = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("typst compile failed (exit " + exitCode + "): " + printed);
        }
        return Files.readAllBytes(outputFile);
    }

    private static void cleanup(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public record StationLogo(byte[] data, String contentType) {}

    /** The kind of PDF a template is compiled to. */
    public enum Output {
        /** A plain PDF, as every export produces. */
        PDF,
        /** PDF/A-3b, for a document kept as a record and signed later. */
        PDF_A_3B
    }
}
