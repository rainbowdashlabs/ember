/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.FormField;
import dev.chojo.ember.feature.generator.entity.FormFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfInspection;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.PdfPage;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The migration that lets a check be ticked by hand turns every check of a PDF template, drawn on the
 * page or a check box of the PDF itself, into a question whose suggestion is the check's old text, and
 * points the check at the answer. Where nobody answers, the suggestion decides, so a template ticks
 * exactly what it ticked before.
 *
 * <p>The database of this test is already migrated, so the old shape is written by hand and the patch
 * run over it once more, which is exactly what it met on a database that had templates.
 */
class CheckQuestionMigrationTest extends RepositoryTestBase {
    private static final String PATCH = "database/postgresql/1/patch_82.sql";
    private static final PdfTemplateRepository pdfs = new PdfTemplateRepository();
    private static final DocumentTemplateRepository templates = new DocumentTemplateRepository();
    private static final TemplateQuestionRepository questions = new TemplateQuestionRepository();

    @Test
    void checksBecomeQuestionsSuggestingWhatTheyTickedBefore() throws Exception {
        var station = stationRepo.create("Kreuz Migration");
        int author =
                accountRepo.create("check-migration@test.com", "Kim", "Kreuz").id();
        stationMemberRepo.create(station.id(), author);
        int templateId = pdfTemplate(station.id(), author);
        var inspection = new PdfInspection(
                List.of(new PdfPage(0, 0, 595, 842, 0)),
                List.of(
                        new FormField(
                                "Feld1", FormFieldKind.CHECK, new FieldRect(1, 10, 10, 10, 10), "Schwimmer", null),
                        new FormField("Name", FormFieldKind.TEXT, new FieldRect(1, 30, 10, 90, 10), null, null)));
        pdfs.addOriginal(templateId, "alt.pdf", 10, "aa", inspection, author);
        pdfs.writeLayout(
                templateId,
                new PdfLayout(
                        List.of(check("{{profile.4}}"), text("{{member.fullName}}"), check("{{profile.5}}")),
                        List.of(new FormBinding("Feld1", "{{profile.6}}"), new FormBinding("Name", "{{today}}"))));

        migrate();
        migrate();

        assertEquals(
                List.of(
                        new TemplateQuestion("kreuz_1", "Kreuz 1", TemplateQuestionKind.CHECK, "{{profile.4}}", false),
                        new TemplateQuestion("kreuz_2", "Kreuz 2", TemplateQuestionKind.CHECK, "{{profile.5}}", false),
                        new TemplateQuestion(
                                "feld_1", "Schwimmer", TemplateQuestionKind.CHECK, "{{profile.6}}", false)),
                questions.findByTemplate(templateId));
        var layout = pdfs.findLayout(templateId);
        assertEquals(
                List.of("{{ask.kreuz_1}}", "{{member.fullName}}", "{{ask.kreuz_2}}"),
                layout.fields().stream().map(PdfField::text).toList());
        assertEquals(
                List.of(new FormBinding("Feld1", "{{ask.feld_1}}"), new FormBinding("Name", "{{today}}")),
                layout.bindings());
    }

    private static int pdfTemplate(int stationId, int author) {
        var draft = new DocumentTemplateDraft(
                "Alte Kreuze",
                "t",
                "f",
                List.of(),
                false,
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                new PdfContent(null, PdfLayout.empty()));
        return templates.create(new Owner.Station(stationId), draft, author).id();
    }

    private static PdfField check(String text) {
        return new PdfField(
                PdfFieldKind.CHECK, new FieldRect(1, 10, 10, 10, 10), text, 10, TextAlign.LEFT, false, null);
    }

    private static PdfField text(String text) {
        return new PdfField(PdfFieldKind.TEXT, new FieldRect(1, 30, 10, 90, 10), text, 10, TextAlign.LEFT, false, null);
    }

    private static void migrate() throws IOException, SQLException {
        String patch;
        try (var in = Objects.requireNonNull(
                CheckQuestionMigrationTest.class.getClassLoader().getResourceAsStream(PATCH))) {
            patch = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(patch.replace("ember_schema", schemaName));
        }
    }
}
