/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Finding forms: the ones a member can answer now, for themselves or for the members in their
 * care, and the ones an editor can put on a page.
 */
@Singleton
public class FormDirectoryService {
    private final FormService forms;

    @Inject
    public FormDirectoryService(FormService forms) {
        this.forms = forms;
    }

    private boolean answerableNow(Form form) {
        return form.status() == Form.FormStatus.OPEN && forms.isAcceptingResponses(form);
    }

    /**
     * The open forms a member can answer, followed by the ones only a member in their care can.
     *
     * @param stationId the station
     * @param memberId  the member asking
     * @param manager   whether they manage forms, which lets them see every form of the station
     * @param wardIds   the members in their care
     */
    public List<FormListEntry> available(int stationId, int memberId, boolean manager, Collection<Integer> wardIds) {
        var own = forms.findByStationForMember(stationId, memberId, manager).stream()
                .filter(this::answerableNow)
                .toList();
        Set<Integer> forWards = wardIds.stream()
                .flatMap(ward -> forms.findByStationOnBehalfOf(stationId, ward).stream())
                .filter(this::answerableNow)
                .map(Form::id)
                .collect(Collectors.toSet());
        var seen = own.stream().map(Form::id).collect(Collectors.toCollection(HashSet::new));
        var combined = new ArrayList<>(own);
        if (!forWards.isEmpty()) {
            forms.findByStation(stationId).stream()
                    .filter(f -> forWards.contains(f.id()) && !seen.contains(f.id()))
                    .filter(this::answerableNow)
                    .forEach(combined::add);
        }
        return combined.stream().map(f -> entryOf(f, memberId)).toList();
    }

    private FormListEntry entryOf(Form form, int memberId) {
        return new FormListEntry(
                form.id(),
                form.stationId(),
                form.title(),
                form.description(),
                form.status(),
                form.startAt(),
                form.endAt(),
                forms.countResponses(form.id()),
                forms.hasResponded(form.id(), memberId),
                form.restricted());
    }

    /**
     * The forms of one purpose an editor can put on a page, newest first.
     *
     * <p>Only openly addressed forms are offered: a page fetches the form it carries by its own
     * address, so a form reached by its link alone would show as missing on the page. Asked for one
     * form by its address, the answer is that form or nothing, never one of another station.
     *
     * @param uid   the address of one form, or null to search
     * @param query a fragment of the title, or null for any
     * @param limit how many at most, held between 1 and 20
     */
    public List<FormSearchResult> pickable(int stationId, FormPurpose purpose, String uid, String query, int limit) {
        if (uid != null && !uid.isBlank()) {
            return byAddress(uid)
                    .filter(f -> f.stationId() == stationId)
                    .filter(f -> f.purpose() == purpose)
                    .filter(f -> f.visibility().openlyAddressed())
                    .map(f -> List.of(resultOf(f)))
                    .orElseGet(List::of);
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return forms.findByStationAndPurpose(stationId, purpose).stream()
                .filter(f -> f.visibility().openlyAddressed())
                .filter(f ->
                        needle.isEmpty() || f.title().toLowerCase(Locale.ROOT).contains(needle))
                .limit(Math.clamp(limit, 1, 20))
                .map(FormDirectoryService::resultOf)
                .toList();
    }

    private Optional<Form> byAddress(String uid) {
        try {
            return forms.findByPublicUid(UUID.fromString(uid));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static FormSearchResult resultOf(Form form) {
        return new FormSearchResult(form.publicUid(), form.title(), form.purpose(), form.status());
    }

    /**
     * Lightweight picker result shape, for the poll and call-to-action cells of the page editor.
     */
    public record FormSearchResult(UUID publicUid, String title, FormPurpose purpose, Form.FormStatus status) {}

    /**
     * A form a member can answer, with whether they already have.
     *
     * @param restricted whether the form is put to only part of the station, which the list marks with a lock
     */
    public record FormListEntry(
            int id,
            int stationId,
            String title,
            String description,
            Form.FormStatus status,
            @Nullable Instant startAt,
            @Nullable Instant endAt,
            int responseCount,
            boolean hasResponded,
            boolean restricted) {}
}
