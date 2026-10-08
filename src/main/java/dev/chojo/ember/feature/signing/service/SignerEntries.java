/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Checks the values a signer typed into fields of their own before they are bound into the act. Every
 * field sent is named once and filled in; a field that is not to be filled in is simply not sent. Values
 * are kept exactly as typed, since they are what the signature covers.
 */
final class SignerEntries {
    /** How many fields a signer can fill in at one act. */
    static final int MOST_ENTRIES = 20;

    /** How long a field's name may be. */
    static final int LONGEST_FIELD = 64;

    /** How long a value may be. */
    static final int LONGEST_VALUE = 500;

    private SignerEntries() {}

    /**
     * @param drafts what the browser sent, or null where the signer filled in nothing
     * @return the entries, in the order they were sent
     */
    static List<SignerEntry> checked(@Nullable List<SignerEntryDraft> drafts) {
        // TODO check the entries against the fields the document asks the signer to fill in, once templates name them
        if (drafts == null) return List.of();
        if (drafts.size() > MOST_ENTRIES) throw DocumentRefusal.SIGNING_TOO_MANY_ENTRIES.raise();
        var entries = new ArrayList<SignerEntry>(drafts.size());
        var named = new HashSet<String>();
        for (SignerEntryDraft draft : drafts) {
            String field = draft.field();
            String value = draft.value();
            if (field == null || field.isBlank()) throw DocumentRefusal.SIGNING_ENTRY_UNNAMED.raise();
            if (field.length() > LONGEST_FIELD) throw DocumentRefusal.SIGNING_ENTRY_TOO_LONG.raise();
            if (value == null || value.isBlank()) {
                throw DocumentRefusal.SIGNING_ENTRY_EMPTY.raise(RefusalDetail.text(field));
            }
            if (value.length() > LONGEST_VALUE) {
                throw DocumentRefusal.SIGNING_ENTRY_TOO_LONG.raise(RefusalDetail.text(field));
            }
            if (!named.add(field)) throw DocumentRefusal.SIGNING_ENTRY_TWICE.raise(RefusalDetail.text(field));
            entries.add(new SignerEntry(field, value));
        }
        return entries;
    }
}
