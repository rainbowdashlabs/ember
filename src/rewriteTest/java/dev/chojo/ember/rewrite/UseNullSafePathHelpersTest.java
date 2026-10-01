/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcTestJava;

/**
 * The path recipe swaps the two null-prone readings of a path for the helpers, and only those.
 */
class UseNullSafePathHelpersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseNullSafePathHelpers(null))
                .parser(JavaParser.fromJavaVersion().dependsOn("""
                                package dev.chojo.ember.util;
                                public final class FilePaths {
                                    public static String nameOf(java.nio.file.Path path) { return ""; }
                                    public static void createParentDirectories(java.nio.file.Path file) throws java.io.IOException {}
                                }
                                """));
    }

    @Test
    void aFileNameIsReadThroughTheHelper() {
        rewriteRun(java("""
                package dev.example;

                import java.nio.file.Path;

                class Listing {
                    String name(Path entry) {
                        return entry.getFileName().toString();
                    }
                }
                """, """
                package dev.example;

                import dev.chojo.ember.util.FilePaths;

                import java.nio.file.Path;

                class Listing {
                    String name(Path entry) {
                        return FilePaths.nameOf(entry);
                    }
                }
                """));
    }

    @Test
    void theParentDirectoriesAreCreatedThroughTheHelper() {
        rewriteRun(java("""
                package dev.example;

                import java.io.IOException;
                import java.nio.file.Files;
                import java.nio.file.Path;

                class Writer {
                    void write(Path file) throws IOException {
                        Files.createDirectories(file.getParent());
                        Files.writeString(file, "text");
                    }
                }
                """, """
                package dev.example;

                import dev.chojo.ember.util.FilePaths;

                import java.io.IOException;
                import java.nio.file.Files;
                import java.nio.file.Path;

                class Writer {
                    void write(Path file) throws IOException {
                        FilePaths.createParentDirectories(file);
                        Files.writeString(file, "text");
                    }
                }
                """));
    }

    @Test
    void aCreatedDirectoryThatIsUsedIsLeftAlone() {
        rewriteRun(java("""
                package dev.example;

                import java.io.IOException;
                import java.nio.file.Files;
                import java.nio.file.Path;

                class Writer {
                    Path prepare(Path file) throws IOException {
                        return Files.createDirectories(file.getParent());
                    }
                }
                """));
    }

    @Test
    void testSourcesAreLeftAlone() {
        rewriteRun(srcTestJava(java("""
                package dev.example;

                import java.nio.file.Path;

                class ListingTest {
                    String name(Path entry) {
                        return entry.getFileName().toString();
                    }
                }
                """)));
    }

    @Test
    void otherPackagesAreLeftAlone() {
        rewriteRun(spec -> spec.recipe(new UseNullSafePathHelpers("dev.other")), java("""
                        package dev.example;

                        import java.nio.file.Path;

                        class Listing {
                            String name(Path entry) {
                                return entry.getFileName().toString();
                            }
                        }
                        """));
    }
}
