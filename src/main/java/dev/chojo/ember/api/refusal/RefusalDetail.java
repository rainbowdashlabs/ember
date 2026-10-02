/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.util.SizeParser;
import org.jspecify.annotations.Nullable;

/**
 * The particular thing a refusal was about, as a value rather than a sentence.
 *
 * <p>The sentence of a refusal is written once per code, and a screen reading another language shows
 * its own sentence for that code instead. Whatever the refusal adds about this one case therefore
 * travels apart from the sentence, typed by what it is, so a screen can write a number as a number
 * and a size in the units it uses everywhere else, in its own language around it.
 *
 * <p>The kinds are few on purpose. A detail is a name or a value somebody typed, a count (in a unit
 * where the sentence does not name one), or the room left in a pool against its size; a refusal that
 * needs to say more than that says it in its sentence.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RefusalDetail.TextDetail.class, name = "TEXT"),
    @JsonSubTypes.Type(value = RefusalDetail.CountDetail.class, name = "COUNT"),
    @JsonSubTypes.Type(value = RefusalDetail.RoomDetail.class, name = "ROOM")
})
public sealed interface RefusalDetail {

    /**
     * A name or a value as somebody wrote it, shown as it is.
     *
     * @param text what was written
     * @return the detail
     */
    static RefusalDetail text(String text) {
        return new TextDetail(text);
    }

    /**
     * How many of something stand in the way.
     *
     * @param count the number
     * @return the detail
     */
    static RefusalDetail count(long count) {
        return new CountDetail(count, null);
    }

    /**
     * How many of a unit a limit stands at, such as the days a sheet may run.
     *
     * @param count the number
     * @param unit  what is counted
     * @return the detail
     */
    static RefusalDetail count(long count, CountUnit unit) {
        return new CountDetail(count, unit);
    }

    /**
     * How much room is left of a pool, against how big the pool is.
     *
     * @param freeBytes  what is still free, never below zero
     * @param totalBytes how big the pool is
     * @return the detail
     */
    static RefusalDetail room(long freeBytes, long totalBytes) {
        return new RoomDetail(Math.max(0, freeBytes), totalBytes);
    }

    /**
     * The detail in English, as it is appended to the refusal's own sentence for the log and for a
     * reader whose screen has no sentence of its own for the code.
     *
     * @return the detail as a few English words
     */
    String inEnglish();

    /**
     * A name or a value as somebody wrote it.
     *
     * @param text what was written
     */
    record TextDetail(String text) implements RefusalDetail {
        @Override
        public String inEnglish() {
            return text;
        }
    }

    /**
     * How many of something stand in the way, or where a limit stands.
     *
     * @param count the number
     * @param unit  what is counted, or {@code null} where the sentence already says what
     */
    record CountDetail(long count, @Nullable CountUnit unit) implements RefusalDetail {
        @Override
        public String inEnglish() {
            if (unit == null) return Long.toString(count);
            return unit.english.formatted(count);
        }
    }

    /**
     * The room left of a pool against how big the pool is.
     *
     * @param freeBytes  what is still free
     * @param totalBytes how big the pool is
     */
    record RoomDetail(long freeBytes, long totalBytes) implements RefusalDetail {
        @Override
        public String inEnglish() {
            return "%s free of %s".formatted(SizeParser.formatBytes(freeBytes), SizeParser.formatBytes(totalBytes));
        }
    }

    /** What a count is counted in, where the refusal's sentence does not say. */
    enum CountUnit {
        /** Calendar days. */
        DAYS("%d days"),
        /** Hours. */
        HOURS("%d hours"),
        /** Years, such as of somebody's age. */
        YEARS("%d years"),
        /** The line of a list somebody sent, counted from one. */
        LINE("line %d");

        private final String english;

        CountUnit(String english) {
            this.english = english;
        }
    }
}
