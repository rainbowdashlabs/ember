/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.util.List;

/**
 * Everything written about a template when it is created or changed, already checked.
 *
 * @param name            what it is called
 * @param titlePattern    the title a generated document is filed under
 * @param fileNamePattern the file name a generated document is filed under
 * @param tags            the document tags a generated document is filed with
 * @param hidden          whether a document a manager generates is hidden from the member
 * @param keepOnArchive   whether a generated document outlasts the membership
 * @param legal           whether it makes a legal document, which a template for appointments always does
 * @param forAppointments whether appointments may require it as a document to bring
 * @param selfService     whether members may generate it for themselves
 * @param cooldownDays    the days between two self service generations for one member
 * @param restrictionMode how the parts of the self service audience combine
 * @param language        the language its documents are written in
 * @param content         the letter, or the fields laid over the PDF
 */
public record DocumentTemplateDraft(
        String name,
        String titlePattern,
        String fileNamePattern,
        List<String> tags,
        boolean hidden,
        boolean keepOnArchive,
        boolean legal,
        boolean forAppointments,
        boolean selfService,
        int cooldownDays,
        RestrictionMode restrictionMode,
        DocumentLanguage language,
        TemplateContent content) {

    /** @return the kind of template the draft makes */
    public DocumentTemplateKind kind() {
        return content.kind();
    }
}
