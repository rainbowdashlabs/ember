/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The profile answers the member inventory list and the movement sheet print beside the gear.
 *
 * <p>Both printed the stored answer with its quotation marks taken off, so a yes read {@code true}
 * and a date the way the database writes it.
 */
class ProfileColumnsTest {
    private static final int MEMBER = 5;

    private static ProfileField fieldOf(int id, String name, ProfileFieldType type) {
        return new ProfileField(id, 1, name, type, ProfileFieldConfig.empty(), false, false, null, false);
    }

    private static final List<ProfileField> FIELDS = List.of(
            fieldOf(1, "Führerschein", ProfileFieldType.BOOLEAN),
            fieldOf(2, "Atemschutz", ProfileFieldType.BOOLEAN),
            fieldOf(3, "Geburtstag", ProfileFieldType.BIRTH_DATE),
            fieldOf(4, "Schuhgröße", ProfileFieldType.NUMBER),
            fieldOf(5, "Funkrufname", ProfileFieldType.TEXT));

    private static List<String> cells(String language, ProfileFieldValue... answers) {
        return new ProfileColumns(FIELDS, language).cellsOf(List.of(answers));
    }

    @Test
    void eachAnswerIsPrintedAsEveryExportPrintsIt() {
        var printed = cells(
                "de",
                new ProfileFieldValue(MEMBER, 1, "\"1\""),
                new ProfileFieldValue(MEMBER, 2, "false"),
                new ProfileFieldValue(MEMBER, 3, "\"2000-01-31\""),
                new ProfileFieldValue(MEMBER, 4, "43"));

        assertEquals(List.of("Ja", "Nein", "31.01.2000", "43", ""), printed);
    }

    @Test
    void aYesFollowsTheStationsLanguage() {
        assertEquals(
                "Yes", cells("en", new ProfileFieldValue(MEMBER, 1, "true")).getFirst());
    }

    @Test
    void theHeadersAreTheQuestionsInTheirOrder() {
        assertEquals(
                List.of("Führerschein", "Atemschutz", "Geburtstag", "Schuhgröße", "Funkrufname"),
                new ProfileColumns(FIELDS, "de").names());
    }
}
