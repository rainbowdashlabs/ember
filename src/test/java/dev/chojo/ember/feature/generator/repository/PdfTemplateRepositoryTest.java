/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
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
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The uploaded versions of a template's PDF, which one it fills now, and the fields and form values
 * laid over it.
 */
class PdfTemplateRepositoryTest extends RepositoryTestBase {
    private static final PdfTemplateRepository pdfs = new PdfTemplateRepository();
    private static final DocumentTemplateRepository templates = new DocumentTemplateRepository();
    private static final PdfInspection INSPECTION = new PdfInspection(
            List.of(new PdfPage(0, 0, 595, 842, 90)),
            List.of(new FormField("name", FormFieldKind.TEXT, new FieldRect(1, 10, 20, 30, 40))));

    private static int templateId;
    private static int author;

    @BeforeAll
    static void setup() {
        var station = stationRepo.create("PDF Ablage Wache");
        author = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo.create("pdf-repo@test.com", "Pia", "Ablage").id())
                .id();
        var draft = new DocumentTemplateDraft(
                "Formular",
                "t",
                "f",
                List.of(),
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                new PdfContent(null, PdfLayout.empty()));
        var template = templates.create(station.id(), draft, author);
        assertEquals(DocumentTemplateKind.PDF, template.kind());
        templateId = template.id();
    }

    @Test
    void theNewestUploadIsTheOneTheTemplateFills() {
        assertTrue(pdfs.findCurrentOriginal(-1).isEmpty());

        var first = pdfs.addOriginal(templateId, "alt.pdf", 10, "aa", INSPECTION, author);
        var second = pdfs.addOriginal(templateId, "neu.pdf", 20, "bb", new PdfInspection(List.of(), List.of()), author);

        assertEquals(second, pdfs.findCurrentOriginal(templateId).orElseThrow());
        assertEquals(INSPECTION, first.inspection());
        assertEquals("alt.pdf", first.fileName());
        assertEquals(10, first.sizeBytes());
        assertEquals(templateId, second.templateId());
    }

    @Test
    void theLayoutIsReplacedAsAWholeAndKeepsItsOrder() {
        var signature = new PdfField(
                PdfFieldKind.SIGNATURE,
                new FieldRect(1, 100, 100, 150, 40),
                null,
                10,
                TextAlign.LEFT,
                false,
                SignatureRole.GUARDIAN_2);
        var text = new PdfField(
                PdfFieldKind.TEXT,
                new FieldRect(2, 10.5, 20.25, 30, 40),
                "{{today}}",
                9.5,
                TextAlign.RIGHT,
                true,
                null);
        pdfs.writeLayout(templateId, new PdfLayout(List.of(text), List.of(new FormBinding("old", "x"))));

        pdfs.writeLayout(
                templateId, new PdfLayout(List.of(signature, text), List.of(new FormBinding("name", "{{today}}"))));

        assertEquals(
                new PdfLayout(List.of(signature, text), List.of(new FormBinding("name", "{{today}}"))),
                pdfs.findLayout(templateId));
        assertEquals(PdfLayout.empty(), pdfs.findLayout(-1));
    }

    @Test
    void theVersionCountsUpForAChangeOutsideTheTemplateRow() {
        int before = templates.findById(templateId).orElseThrow().version();

        var counted = templates.countVersion(templateId, author).orElseThrow();

        assertEquals(before + 1, counted.version());
        assertTrue(templates.countVersion(-1, author).isEmpty());
    }
}
