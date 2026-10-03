/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Letterhead;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A document template as the editor sends it, to create one or to change one.
 *
 * <p>What is left out takes the value a new template starts with: a title and a file name built from
 * the template's name and the day, no tags, a document kept past the membership exactly where the
 * template is legal, a wait of 30 days between two self service documents, every member as the
 * audience, an empty letter on the default page.
 *
 * @param name            what the template is called
 * @param titlePattern    the title a generated document is filed under, with placeholders
 * @param fileNamePattern the file name a generated document is filed under, with placeholders
 * @param tags            the document tags a generated document is filed with
 * @param hidden          whether a document a manager generates is hidden from the member
 * @param keepOnArchive   whether a generated document outlasts the membership
 * @param legal           whether the template makes a legal document
 * @param selfService     whether members may generate it for themselves and their children
 * @param cooldownDays    the days between two self service documents for one member
 * @param audience        who may generate it through self service
 * @param pronounSource   the choice field the pronouns follow, or null for the first name throughout
 * @param letterhead      the header and the footer
 * @param bodyMarkdown    the body, placeholders written as {@code {{key}}}
 * @param page            the margins and the size of the body text
 */
public record DocumentTemplateRequest(
        @Nullable String name,
        @Nullable String titlePattern,
        @Nullable String fileNamePattern,
        @Nullable List<String> tags,
        boolean hidden,
        @Nullable Boolean keepOnArchive,
        boolean legal,
        boolean selfService,
        @Nullable Integer cooldownDays,
        @Nullable RestrictionAudience audience,
        @Nullable PronounSource pronounSource,
        @Nullable Letterhead letterhead,
        @Nullable String bodyMarkdown,
        @Nullable LetterPage page) {}
