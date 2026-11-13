/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One of the newest messages of a conversation that the daily mail quotes, with how many wait there
 * in all.
 *
 * @param message the message
 * @param waiting how many messages of that conversation go into the mail
 */
public record DigestMessage(Message message, int waiting) {
    /**
     * Reads a row carrying {@link Message#COLUMNS} and the count of its conversation.
     *
     * @return the mapping
     */
    public static RowMapping<DigestMessage> map() {
        return row -> new DigestMessage(Message.map().map(row), row.getInt("waiting"));
    }
}
