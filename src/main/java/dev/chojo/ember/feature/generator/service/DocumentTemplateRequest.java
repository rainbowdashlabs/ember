/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.TemplateSigning;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A document template as the editor sends it, to create one or to change one.
 *
 * <p>What is left out takes the value a new template starts with: a letter, a title and a file name
 * built from the template's name and the day, no tags, a document kept past the membership exactly
 * where the template is legal, a wait of 30 days between two self service documents, every member as
 * the audience, an empty letter on the default page, no fields.
 *
 * <p>The kind is chosen when the template is created and stays; a change reads only the part of the
 * request its kind has: the letter of a letter, the fields of a PDF template.
 *
 * @param kind            what the template is made of, read when it is created
 * @param name            what the template is called
 * @param titlePattern    the title a generated document is filed under, with placeholders
 * @param fileNamePattern the file name a generated document is filed under, with placeholders
 * @param tags            the document tags a generated document is filed with
 * @param hidden          whether a document a manager generates is hidden from the member
 * @param keepOnArchive   whether a generated document outlasts the membership
 * @param legal           whether the template makes a legal document
 * @param forAppointments whether appointments may require it as a document to bring, which makes it legal
 * @param selfService     whether members may generate it for themselves and their children
 * @param cooldownDays    the days between two self service documents for one member
 * @param audience        who may generate it through self service
 * @param language        the language the documents are written in, the station's where left out
 * @param issuerId        the member of the station who issues the documents, or null for nobody; a
 *                        template of an association names none
 * @param issuerFunction  what the issuer does at the station, or null where nothing is said
 * @param header          the rows across the top of every page of a letter
 * @param footer          the rows across the bottom of every page of a letter
 * @param body            the rows of a letter, placeholders written as {@code {{key}}} in its texts
 * @param page            the margins and the size of the body text of a letter
 * @param fields          the fields drawn on the pages of a PDF template
 * @param formBindings    what the form fields of a PDF template's PDF are filled with
 * @param signing         how the documents are kept and sent once they are signed; left out, a new
 *                        template starts as {@link TemplateSigning#startingWith} says and a change keeps
 *                        what the template had
 */
public record DocumentTemplateRequest(
        @Nullable DocumentTemplateKind kind,
        @Nullable String name,
        @Nullable String titlePattern,
        @Nullable String fileNamePattern,
        @Nullable List<String> tags,
        boolean hidden,
        @Nullable Boolean keepOnArchive,
        boolean legal,
        boolean forAppointments,
        boolean selfService,
        @Nullable Integer cooldownDays,
        @Nullable RestrictionAudience audience,
        @Nullable DocumentLanguage language,
        @Nullable Integer issuerId,
        @Nullable String issuerFunction,
        @Nullable List<BlockRowRequest> header,
        @Nullable List<BlockRowRequest> footer,
        @Nullable List<BlockRowRequest> body,
        @Nullable LetterPage page,
        @Nullable List<PdfField> fields,
        @Nullable List<FormBinding> formBindings,
        @Nullable TemplateSigning signing) {

    /** A request that leaves out how signed documents are kept and sent. */
    public DocumentTemplateRequest(
            @Nullable DocumentTemplateKind kind,
            @Nullable String name,
            @Nullable String titlePattern,
            @Nullable String fileNamePattern,
            @Nullable List<String> tags,
            boolean hidden,
            @Nullable Boolean keepOnArchive,
            boolean legal,
            boolean forAppointments,
            boolean selfService,
            @Nullable Integer cooldownDays,
            @Nullable RestrictionAudience audience,
            @Nullable DocumentLanguage language,
            @Nullable Integer issuerId,
            @Nullable String issuerFunction,
            @Nullable List<BlockRowRequest> header,
            @Nullable List<BlockRowRequest> footer,
            @Nullable List<BlockRowRequest> body,
            @Nullable LetterPage page,
            @Nullable List<PdfField> fields,
            @Nullable List<FormBinding> formBindings) {
        this(
                kind,
                name,
                titlePattern,
                fileNamePattern,
                tags,
                hidden,
                keepOnArchive,
                legal,
                forAppointments,
                selfService,
                cooldownDays,
                audience,
                language,
                issuerId,
                issuerFunction,
                header,
                footer,
                body,
                page,
                fields,
                formBindings,
                null);
    }
}
