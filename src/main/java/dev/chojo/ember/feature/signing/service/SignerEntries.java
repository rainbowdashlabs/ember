/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks the values a signer typed into the fields the document asks them to fill in, before they are
 * bound into the act.
 *
 * <p>Every field sent is named once, filled in, and one of the fields the frozen document asks the signer
 * of this signature field to fill in ({@link FillInField}), within that field's maximum length; every field
 * marked required is sent. An optional field left empty is simply not sent. Values are kept exactly as
 * typed, since they are what the signature covers, and they are put in the order the document lists its
 * fields, so the challenge does not depend on the order the browser sent them in.
 */
final class SignerEntries {
    /** How many fields a signer can fill in at one act. */
    static final int MOST_ENTRIES = FillInField.MOST_PER_SIGNER;

    /** How long a field's name may be. */
    static final int LONGEST_FIELD = 64;

    /** How long a value may be. */
    static final int LONGEST_VALUE = FillInField.LONGEST_VALUE;

    private SignerEntries() {}

    /**
     * @param drafts what the browser sent, or null where the signer filled in nothing
     * @param asked  the fields the document asks this signer to fill in
     * @return the entries, in the order the document lists their fields
     */
    static List<SignerEntry> checked(@Nullable List<SignerEntryDraft> drafts, List<FillInField> asked) {
        var byName = asked.stream()
                .collect(Collectors.toMap(FillInField::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        var entries = wellFormed(drafts);
        for (SignerEntry entry : entries) {
            FillInField field = byName.get(entry.field());
            if (field == null) throw DocumentRefusal.SIGNING_ENTRY_NOT_ASKED.raise(RefusalDetail.text(entry.field()));
            if (entry.value().length() > field.maxLength()) {
                throw DocumentRefusal.SIGNING_ENTRY_LONGER_THAN_FIELD.raise(RefusalDetail.text(field.label()));
            }
        }
        var sent = entries.stream().map(SignerEntry::field).collect(Collectors.toSet());
        for (FillInField field : byName.values()) {
            if (field.required() && !sent.contains(field.name())) {
                throw DocumentRefusal.SIGNING_ENTRY_REQUIRED.raise(RefusalDetail.text(field.label()));
            }
        }
        var order = new ArrayList<>(byName.keySet());
        return entries.stream()
                .sorted(Comparator.comparingInt(entry -> order.indexOf(entry.field())))
                .toList();
    }

    private static List<SignerEntry> wellFormed(@Nullable List<SignerEntryDraft> drafts) {
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
