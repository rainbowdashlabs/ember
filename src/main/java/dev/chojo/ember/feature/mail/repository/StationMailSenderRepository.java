/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.mail.entity.StationMailSender;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The name a station's mail goes out under and the address replies to it go to.
 */
@Singleton
public class StationMailSenderRepository {

    /**
     * The station's sender, or empty when there is no such station.
     */
    public Optional<StationMailSender> find(int stationId) {
        return query("SELECT name, mail_reply_to FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> new StationMailSender(
                        row.getString("name"), Objects.requireNonNullElse(row.getString("mail_reply_to"), "")))
                .first();
    }

    /**
     * Sets where replies to the station's mail go.
     *
     * @param replyTo the address, or null for the sender address itself
     */
    public void updateReplyTo(int stationId, @Nullable String replyTo) {
        query("UPDATE station SET mail_reply_to = :reply_to WHERE id = :id;")
                .single(call().bind("id", stationId).bind("reply_to", replyTo))
                .update();
    }
}
