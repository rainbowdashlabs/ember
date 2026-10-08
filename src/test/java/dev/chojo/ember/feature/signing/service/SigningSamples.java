/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps sealed documents the signing tests made, for a person to open in a PDF reader that shows
 * signatures, such as Adobe Acrobat Reader, which no test can do.
 *
 * <p>Nothing is written unless the environment variable {@value #DIRECTORY_VARIABLE} names a directory,
 * which {@code ./toolchain.sh be-signing-samples} sets to {@code build/signing-samples}. A test run without
 * it leaves no file behind.
 */
public final class SigningSamples {
    /** The environment variable naming the directory the samples go to. */
    public static final String DIRECTORY_VARIABLE = "EMBER_SIGNING_SAMPLES";

    private SigningSamples() {}

    /**
     * Writes a sample, replacing one of the same name, when samples are asked for.
     *
     * @param name the file name, ending in {@code .pdf}
     * @param pdf  the document
     */
    public static void write(String name, byte[] pdf) {
        String directory = System.getenv(DIRECTORY_VARIABLE);
        if (directory == null || directory.isBlank()) return;
        try {
            Path target = Path.of(directory);
            Files.createDirectories(target);
            Files.write(target.resolve(name), pdf);
        } catch (IOException e) {
            throw new UncheckedIOException("The signing sample " + name + " could not be written", e);
        }
    }
}
