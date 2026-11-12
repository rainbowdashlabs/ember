/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One emoji one person reacted to a message with.
 *
 * @param messageId the message
 * @param emoji     the emoji
 * @param person    who reacted
 */
public record Reaction(int messageId, String emoji, Person person) {

    /** The columns {@link #map()} reads, for a reaction table aliased {@code r} joined to an account {@code a}. */
    public static final String COLUMNS = "r.message_id, r.emoji, %s".formatted(Person.columns("a", "reactor_"));

    /**
     * Reads a reaction row.
     *
     * @return the mapping
     */
    public static RowMapping<Reaction> map() {
        return row -> new Reaction(row.getInt("message_id"), row.getString("emoji"), Person.require(row, "reactor_"));
    }
}
