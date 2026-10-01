/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.srcTestJava;

/**
 * The null-marking recipe adds the file where a package has none, brings a plain one up to the
 * wanted annotations, and leaves alone what it was not asked to touch.
 */
class AddNullMarkedPackageInfoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.typeValidationOptions(TypeValidation.none());
    }

    @Test
    void aPackageWithoutTheFileGetsOne() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo(null, null)),
                srcMainJava(
                        java(
                                "package dev.example.stock;\n\nclass Item {}\n",
                                source -> source.path("dev/example/stock/Item.java")),
                        java(
                                null,
                                AddNullMarkedPackageInfo.text("dev.example.stock", false),
                                source -> source.path("dev/example/stock/package-info.java"))));
    }

    @Test
    void theSpotBugsDefaultsComeWhenAsked() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo(null, true)),
                srcMainJava(
                        java(
                                "package dev.example.stock;\n\nclass Item {}\n",
                                source -> source.path("dev/example/stock/Item.java")),
                        java(
                                null,
                                AddNullMarkedPackageInfo.text("dev.example.stock", true),
                                source -> source.path("dev/example/stock/package-info.java"))));
    }

    @Test
    void aPlainFileIsBroughtUpToTheSpotBugsDefaults() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo(null, true)),
                srcMainJava(java(
                        AddNullMarkedPackageInfo.text("dev.example.stock", false),
                        AddNullMarkedPackageInfo.text("dev.example.stock", true),
                        source -> source.path("dev/example/stock/package-info.java"))));
    }

    @Test
    void aFileThatAlreadyHasTheAnnotationsStaysAsItIs() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo(null, false)),
                srcMainJava(java(
                        AddNullMarkedPackageInfo.text("dev.example.stock", true),
                        source -> source.path("dev/example/stock/package-info.java"))));
    }

    @Test
    void onlyTheNamedPackagesAreReached() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo("dev.example.stock", null)),
                srcMainJava(
                        java(
                                "package dev.example.stock.service;\n\nclass Ledger {}\n",
                                source -> source.path("dev/example/stock/service/Ledger.java")),
                        java(
                                null,
                                AddNullMarkedPackageInfo.text("dev.example.stock.service", false),
                                source -> source.path("dev/example/stock/service/package-info.java")),
                        java(
                                "package dev.example.stockroom;\n\nclass Shelf {}\n",
                                source -> source.path("dev/example/stockroom/Shelf.java"))));
    }

    @Test
    void aFileWithAPackageCommentIsLeftAlone() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo(null, null)),
                srcMainJava(java(
                        "/** The stock. */\npackage dev.example.stock;\n",
                        source -> source.path("dev/example/stock/package-info.java"))));
    }

    @Test
    void testSourcesAreNotTouched() {
        rewriteRun(
                spec -> spec.recipe(new AddNullMarkedPackageInfo(null, null)),
                srcTestJava(java(
                        "package dev.example.stock;\n\nclass ItemTest {}\n",
                        source -> source.path("dev/example/stock/ItemTest.java"))));
    }
}
