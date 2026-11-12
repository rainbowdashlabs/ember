/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What whoever saves an appointment chose to tell the people it concerns.
 *
 * <p>Telling them is a choice, off unless asked for. A start that moved is told to everybody holding
 * a place whatever is chosen here.
 *
 * @param tell         whether the people it concerns are told that it changed
 * @param changedParts the parts the editor changed; the time and the date are added from the save
 *                     itself, so a client cannot leave a move out
 * @param note         what the editor writes to go with it, plain text of at most 500 characters,
 *                     or null
 */
public record ChangeNotice(
        boolean tell,
        @Nullable List<EventChangePart> changedParts,
        @Nullable String note) {

    /** Nobody is told anything beyond a moved start. */
    public static final ChangeNotice SILENT = new ChangeNotice(false, List.of(), null);

    /** The most a note may hold, counted after trimming. */
    public static final int NOTE_LIMIT = 500;

    /** The parts the editor named, none where none were sent. */
    public List<EventChangePart> parts() {
        return changedParts == null ? List.of() : changedParts;
    }

    /** The note trimmed, or null where none was written. */
    public @Nullable String trimmedNote() {
        if (note == null || note.isBlank()) return null;
        return note.strip();
    }
}
