/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * A station or a cluster whose people are mailed their gathered notifications together.
 *
 * <p>Both kinds go through the same loop: when they asked to be written to, on which clock, in which
 * language. A cluster reads its times on the clock of its home station and writes in its language,
 * because that is where its people are; it sends through the instance's own chain, since a cluster
 * spanning several stations has no address of its own that a reader would recognise.
 *
 * @param key        which station or cluster
 * @param name       what the mail calls it
 * @param stationUid the station the links in the mail open, {@code null} for a cluster
 * @param zone       the clock its send times are read on
 * @param locale     the locale its mail is written in, as the station stored it
 * @param sendTimes  the times of day it asked for, empty where the operator's number decides
 * @param lastSent   when it was last written to, {@code null} where it never has been
 */
public record DigestGroup(
        Key key,
        String name,
        UUID stationUid,
        ZoneId zone,
        String locale,
        List<LocalTime> sendTimes,
        Instant lastSent) {

    /** Whether the people of a station or of a cluster are meant. */
    public enum Kind {
        STATION,
        CLUSTER
    }

    /**
     * One group, named by its kind and its id.
     *
     * @param kind station or cluster
     * @param id   the station's or the cluster's id
     */
    public record Key(Kind kind, int id) {}

    /**
     * Whether its mail should go out now.
     *
     * @param oldestWaiting when the oldest notification waiting for it arrived
     * @param floor         the shortest gap the operator allows between two mails
     * @param now           the moment being judged
     * @return true where one of its moments has come
     */
    public boolean isDue(Instant oldestWaiting, Duration floor, Instant now) {
        return NotificationSchedule.isDue(sendTimes, lastSent, oldestWaiting, zone, floor, now);
    }
}
