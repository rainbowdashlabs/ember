/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * One of a station's chats as its managers see it in their list.
 *
 * @param conversation the chat
 * @param audienceName the name of its group or tag, or null for a station-wide chat and once it is gone
 * @param members      how many take part in it right now
 */
public record StationChatSummary(
        Conversation conversation, @Nullable String audienceName, int members) {
    /**
     * Reads a row carrying {@link Conversation#COLUMNS}, the audience's name and the member count.
     *
     * @return the mapping
     */
    public static RowMapping<StationChatSummary> map() {
        return row -> new StationChatSummary(
                Conversation.map().map(row), row.getString("audience_name"), row.getInt("member_count"));
    }
}
