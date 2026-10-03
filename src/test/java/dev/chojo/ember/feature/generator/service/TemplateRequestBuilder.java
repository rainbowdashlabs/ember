/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.route.BlockCellRequest;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A template as the editor sends it, built up in a test from what the test is about, everything else
 * left out.
 */
final class TemplateRequestBuilder {
    private @Nullable DocumentTemplateKind kind;
    private final @Nullable String name;
    private @Nullable String titlePattern;
    private @Nullable String fileNamePattern;
    private @Nullable List<String> tags;
    private boolean hidden;
    private @Nullable Boolean keepOnArchive;
    private boolean legal;
    private boolean selfService;
    private @Nullable Integer cooldownDays;
    private @Nullable RestrictionAudience audience;
    private @Nullable DocumentLanguage language;
    private @Nullable List<BlockRowRequest> header;
    private @Nullable List<BlockRowRequest> footer;
    private @Nullable List<BlockRowRequest> body;
    private @Nullable LetterPage page;
    private @Nullable List<PdfField> fields;
    private @Nullable List<FormBinding> formBindings;

    private TemplateRequestBuilder(@Nullable String name) {
        this.name = name;
    }

    /** A letter of the given name, holding nothing yet. */
    static TemplateRequestBuilder letter(@Nullable String name) {
        return new TemplateRequestBuilder(name);
    }

    /** A letter of the given name whose body is one text per row. */
    static TemplateRequestBuilder letter(@Nullable String name, String... texts) {
        return letter(name).body(rowsOf(texts));
    }

    /** A PDF template of the given name, holding no fields yet. */
    static TemplateRequestBuilder pdf(String name) {
        var builder = new TemplateRequestBuilder(name);
        builder.kind = DocumentTemplateKind.PDF;
        return builder;
    }

    /** Rows of one text block each. */
    static List<BlockRowRequest> rowsOf(String... texts) {
        var rows = new ArrayList<BlockRowRequest>();
        for (String text : texts) rows.add(row(text(text)));
        return rows;
    }

    /** One row of blocks side by side, sharing the width evenly. */
    static BlockRowRequest row(BlockCellRequest... cells) {
        var placed = new ArrayList<BlockCellRequest>();
        for (int index = 0; index < cells.length; index++) {
            var cell = cells[index];
            placed.add(new BlockCellRequest(
                    index,
                    100.0 / cells.length,
                    cell.contentType(),
                    cell.content(),
                    cell.config(),
                    cell.restriction()));
        }
        return new BlockRowRequest(0, placed);
    }

    /** A text block. */
    static BlockCellRequest text(String text) {
        return block(CellContentType.MARKDOWN, text, null);
    }

    /** A text block printed only for the given audience. */
    static BlockCellRequest text(String text, RestrictionAudience audience) {
        return block(CellContentType.MARKDOWN, text, audience);
    }

    /** A picture block showing a picture of the media library, or the logo. */
    static BlockCellRequest image(String content) {
        return block(CellContentType.IMAGE, content, null);
    }

    /** A block of any kind with no settings. */
    static BlockCellRequest block(CellContentType type, String content, @Nullable RestrictionAudience audience) {
        return new BlockCellRequest(0, 100.0, type.name(), content, JsonNodeFactory.instance.objectNode(), audience);
    }

    TemplateRequestBuilder title(@Nullable String pattern) {
        this.titlePattern = pattern;
        return this;
    }

    TemplateRequestBuilder fileName(String pattern) {
        this.fileNamePattern = pattern;
        return this;
    }

    TemplateRequestBuilder tags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    TemplateRequestBuilder hidden(boolean hidden) {
        this.hidden = hidden;
        return this;
    }

    TemplateRequestBuilder keepOnArchive(boolean keep) {
        this.keepOnArchive = keep;
        return this;
    }

    TemplateRequestBuilder legal() {
        this.legal = true;
        return this;
    }

    TemplateRequestBuilder selfService(boolean selfService) {
        this.selfService = selfService;
        return this;
    }

    TemplateRequestBuilder cooldown(int days) {
        this.cooldownDays = days;
        return this;
    }

    TemplateRequestBuilder audience(RestrictionAudience audience) {
        this.audience = audience;
        return this;
    }

    TemplateRequestBuilder language(DocumentLanguage language) {
        this.language = language;
        return this;
    }

    TemplateRequestBuilder header(List<BlockRowRequest> rows) {
        this.header = rows;
        return this;
    }

    TemplateRequestBuilder footer(List<BlockRowRequest> rows) {
        this.footer = rows;
        return this;
    }

    TemplateRequestBuilder body(List<BlockRowRequest> rows) {
        this.body = rows;
        return this;
    }

    TemplateRequestBuilder page(LetterPage page) {
        this.page = page;
        return this;
    }

    TemplateRequestBuilder fields(List<PdfField> fields) {
        this.fields = fields;
        return this;
    }

    TemplateRequestBuilder formBindings(List<FormBinding> bindings) {
        this.formBindings = bindings;
        return this;
    }

    DocumentTemplateRequest build() {
        return new DocumentTemplateRequest(
                kind,
                name,
                titlePattern,
                fileNamePattern,
                tags,
                hidden,
                keepOnArchive,
                legal,
                selfService,
                cooldownDays,
                audience,
                language,
                header,
                footer,
                body,
                page,
                fields,
                formBindings);
    }
}
