/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

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
                Files.createDirectories(resFile.getParent());
                Files.write(resFile, entry.getValue());
            }
            return runTypst(tempDir, typFile, pdfFile);
        } finally {
            cleanup(tempDir);
        }
    }

    public static byte[] compileTemplate(Map<String, Object> data, String templateName, StationLogo logo)
            throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("typst-template-");
        try {
            Path templateSource = Path.of("templates", "typst", templateName);
            Path templateFile = tempDir.resolve(templateName);
            Files.createDirectories(templateFile.getParent());
            Path templateDir = templateFile.getParent();

            if (logo != null) {
                String ext = logoExtension(logo.contentType());
                Files.write(templateDir.resolve("logo." + ext), logo.data());
                data.put("hasLogo", true);
                data.put("logoFile", "logo." + ext);
            }

            Files.writeString(templateDir.resolve("data.json"), Json.MAPPER.writeValueAsString(data));
            Files.copy(templateSource, templateFile);

            Path outputFile = tempDir.resolve("output.pdf");
            return runTypst(tempDir, templateFile, outputFile);
        } finally {
            cleanup(tempDir);
        }
    }

    public static String logoExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/svg+xml" -> "svg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private static byte[] runTypst(Path workDir, Path inputFile, Path outputFile)
            throws IOException, InterruptedException {
        var process = new ProcessBuilder(TYPST_BIN, "compile", inputFile.toString(), outputFile.toString())
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("typst compile failed (exit " + exitCode + "): " + output);
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
}
