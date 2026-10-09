/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a signer types is held to the fields the frozen document asks them to fill in: only those fields,
 * each within its length, every required one there, and the values in the document's order whatever order
 * they were sent in.
 */
class SignerEntriesTest {
    private static final FillInField PHONE = new FillInField("fill-guardian1-0", "guardian1", "Telefon", true, 20);
    private static final FillInField ALLERGIES =
            new FillInField("fill-guardian1-3", "guardian1", "Allergien", false, 500);
    private static final List<FillInField> ASKED = List.of(PHONE, ALLERGIES);

    private static SignerEntryDraft draft(String field, String value) {
        return new SignerEntryDraft(field, value);
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    @Test
    void valuesComeBackInTheDocumentsOrderExactlyAsTyped() {
        var checked = SignerEntries.checked(
                List.of(draft(ALLERGIES.name(), "  Nüsse "), draft(PHONE.name(), "0171 2345678")), ASKED);

        assertEquals(
                List.of(new SignerEntry(PHONE.name(), "0171 2345678"), new SignerEntry(ALLERGIES.name(), "  Nüsse ")),
                checked);
    }

    @Test
    void anOptionalFieldMayBeLeftOut() {
        assertEquals(
                List.of(new SignerEntry(PHONE.name(), "112")),
                SignerEntries.checked(List.of(draft(PHONE.name(), "112")), ASKED));
        assertEquals(List.of(), SignerEntries.checked(null, List.of()));
    }

    @Test
    void aRequiredFieldLeftOutIsRefused() {
        refused(DocumentRefusal.SIGNING_ENTRY_REQUIRED, () -> SignerEntries.checked(null, ASKED));
        refused(
                DocumentRefusal.SIGNING_ENTRY_REQUIRED,
                () -> SignerEntries.checked(List.of(draft(ALLERGIES.name(), "keine")), ASKED));
    }

    @Test
    void aFieldTheDocumentDoesNotAskThisSignerForIsRefused() {
        refused(
                DocumentRefusal.SIGNING_ENTRY_NOT_ASKED,
                () -> SignerEntries.checked(
                        List.of(draft(PHONE.name(), "112"), draft("fill-guardian2-0", "110")), ASKED));
        refused(
                DocumentRefusal.SIGNING_ENTRY_NOT_ASKED,
                () -> SignerEntries.checked(List.of(draft("Telefon", "112")), List.of()));
    }

    @Test
    void aValueLongerThanItsFieldIsRefused() {
        refused(
                DocumentRefusal.SIGNING_ENTRY_LONGER_THAN_FIELD,
                () -> SignerEntries.checked(List.of(draft(PHONE.name(), "1".repeat(21))), ASKED));
        assertEquals(
                1,
                SignerEntries.checked(List.of(draft(PHONE.name(), "1".repeat(20))), ASKED)
                        .size());
    }

    @Test
    void malformedValuesAreRefusedBeforeTheyAreMatched() {
        refused(
                DocumentRefusal.SIGNING_ENTRY_EMPTY,
                () -> SignerEntries.checked(List.of(draft(PHONE.name(), " ")), ASKED));
        refused(
                DocumentRefusal.SIGNING_ENTRY_TWICE,
                () -> SignerEntries.checked(List.of(draft(PHONE.name(), "1"), draft(PHONE.name(), "2")), ASKED));
        refused(DocumentRefusal.SIGNING_ENTRY_UNNAMED, () -> SignerEntries.checked(List.of(draft(" ", "1")), ASKED));
    }
}
