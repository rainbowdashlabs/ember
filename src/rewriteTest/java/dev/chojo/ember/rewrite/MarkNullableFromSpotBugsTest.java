/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.openrewrite.java.Assertions.java;

/**
 * The SpotBugs-driven recipe marks exactly the returns and parameters a report shows a null
 * arriving at, and leaves the rest to a person.
 */
class MarkNullableFromSpotBugsTest implements RewriteTest {

    @TempDir
    Path directory;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.typeValidationOptions(TypeValidation.none());
    }

    private String report(String... bugs) throws IOException {
        Path file = directory.resolve("main.xml");
        Files.writeString(file, "<BugCollection>" + String.join("", bugs) + "</BugCollection>");
        return file.toString();
    }

    private static String nullReturned(String type, String method, String signature) {
        return """
                <BugInstance type="NP_NONNULL_RETURN_VIOLATION">
                  <Class classname="%1$s"/>
                  <Method classname="%1$s" name="%2$s" signature="%3$s"/>
                </BugInstance>
                """.formatted(type, method, signature);
    }

    private static String nullPassed(String type, String method, String signature, int position) {
        return """
                <BugInstance type="NP_NONNULL_PARAM_VIOLATION">
                  <Class classname="dev.example.Caller"/>
                  <Method classname="dev.example.Caller" name="call" signature="()V"/>
                  <Method classname="%1$s" name="%2$s" signature="%3$s" role="METHOD_CALLED"/>
                  <Int value="%4$d" role="INT_NULL_ARG"/>
                </BugInstance>
                """.formatted(type, method.replace("<", "&lt;").replace(">", "&gt;"), signature, position);
    }

    @Test
    void aMethodThatReturnsNullIsMarked() throws IOException {
        String report = report(nullReturned("dev.example.Stock", "find", "(Ljava/lang/String;)Ljava/lang/String;"));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, null)), java("""
                        package dev.example;

                        class Stock {
                            public String find(String name) {
                                return null;
                            }
                        }
                        """, """
                        package dev.example;

                        import org.jspecify.annotations.Nullable;

                        class Stock {
                            public @Nullable String find(String name) {
                                return null;
                            }
                        }
                        """));
    }

    @Test
    void theParameterACallerPassesNullForIsMarked() throws IOException {
        String report = report(nullPassed("dev.example.Stock", "move", "(Ljava/lang/String;Ljava/lang/Integer;)V", 2));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, null)), java("""
                        package dev.example;

                        class Stock {
                            void move(String name, Integer shelf) {
                            }
                        }
                        """, """
                        package dev.example;

                        import org.jspecify.annotations.Nullable;

                        class Stock {
                            void move(String name, @Nullable Integer shelf) {
                            }
                        }
                        """));
    }

    @Test
    void aRecordComponentIsMarkedThroughTheCanonicalConstructor() throws IOException {
        String report = report(nullPassed("dev.example.Item", "<init>", "(Ljava/lang/String;Ljava/util/List;)V", 2));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, null)), java("""
                        package dev.example;

                        import java.util.List;

                        record Item(String name, List<String> tags) {
                        }
                        """, """
                        package dev.example;

                        import org.jspecify.annotations.Nullable;

                        import java.util.List;

                        record Item(String name, @Nullable List<String> tags) {
                        }
                        """));
    }

    @Test
    void anArrayIsMarkedBeforeItsBrackets() throws IOException {
        String report = report(nullPassed("dev.example.Stock", "count", "([Ljava/lang/String;)I", 1));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, null)), java("""
                        package dev.example;

                        class Stock {
                            int count(String[] names) {
                                return 0;
                            }
                        }
                        """, """
                        package dev.example;

                        import org.jspecify.annotations.Nullable;

                        class Stock {
                            int count(String @Nullable [] names) {
                                return 0;
                            }
                        }
                        """));
    }

    @Test
    void aQualifiedTypeIsLeftForHand() throws IOException {
        String report = report(nullPassed("dev.example.Stock", "store", "(Ljava/util/List;)V", 1));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, null)), java("""
                        package dev.example;

                        class Stock {
                            void store(java.util.List<String> names) {
                            }
                        }
                        """));
    }

    @Test
    void aPackageOutsideTheFilterIsLeftAlone() throws IOException {
        String report = report(nullReturned("dev.example.Stock", "find", "()Ljava/lang/String;"));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, "dev.other")), java("""
                        package dev.example;

                        class Stock {
                            String find() {
                                return null;
                            }
                        }
                        """));
    }

    @Test
    void aMarkedReturnIsNotMarkedTwice() throws IOException {
        String report = report(nullReturned("dev.example.Stock", "find", "()Ljava/lang/String;"));
        rewriteRun(spec -> spec.recipe(new MarkNullableFromSpotBugs(report, null)), java("""
                        package dev.example;

                        import org.jspecify.annotations.Nullable;

                        class Stock {
                            @Nullable String find() {
                                return null;
                            }
                        }
                        """));
    }
}
