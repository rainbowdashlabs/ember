/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionKind;
import dev.chojo.ember.feature.question.QuestionValues;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * How one appointment of an event group differs from a member field of the group: a member entered
 * for this appointment only, or a member entered for the whole group who is not there on this one.
 *
 * @param groupFieldId the member field of the event group
 * @param eventId      the appointment
 * @param memberId     the member
 * @param kind         whether they are entered here only or skip this appointment
 */
public record EventGroupFieldEntry(int groupFieldId, int eventId, int memberId, Kind kind) {

    /** What an entry shows about the appointment. */
    public enum Kind {
        /** In the field on this appointment only. */
        ENTERED,
        /** Entered for the whole event group, but not on this appointment. */
        SKIPPED
    }

    /**
     * Who a member field names on one appointment: everybody entered for the whole event group who
     * does not skip it, then everybody entered for this appointment only. A field naming one member
     * names the one entered for this appointment where there is one, otherwise the group's.
     *
     * @param type       the kind of member field
     * @param groupValue the members entered for the whole event group, as stored
     * @param entries    the entries of this field on this appointment
     * @return the value the appointment shows, empty where it names nobody
     */
    public static String shownValue(FieldType type, String groupValue, List<EventGroupFieldEntry> entries) {
        var skipped = entries.stream()
                .filter(entry -> entry.kind() == Kind.SKIPPED)
                .map(EventGroupFieldEntry::memberId)
                .toList();
        var entered = entries.stream()
                .filter(entry -> entry.kind() == Kind.ENTERED)
                .map(EventGroupFieldEntry::memberId)
                .toList();
        var named = new LinkedHashSet<Integer>(QuestionValues.memberIds(groupValue));
        named.removeAll(skipped);
        if (type.kind().filter(QuestionKind::namesSeveralMembers).isEmpty()) {
            if (!entered.isEmpty()) return QuestionValues.formatMember(entered.getFirst());
            return named.isEmpty() ? "" : QuestionValues.formatMember(named.getFirst());
        }
        named.addAll(entered);
        return named.isEmpty() ? "" : QuestionValues.formatMembers(new ArrayList<>(named));
    }
}
