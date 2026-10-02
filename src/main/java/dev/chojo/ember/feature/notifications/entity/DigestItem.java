/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * One notification waiting for the mail, with everything the digest needs to know about it.
 *
 * @param notification the notification
 * @param group        the station or cluster whose mail it goes out with
 * @param recipientId  the station member or cluster member it is for
 * @param accountId    the account behind that member, {@code null} for a member without one
 * @param mailWanted   whether its reader wants it by mail: their mail switch and, for a station
 *                     member, the mail setting of its type
 */
public record DigestItem(
        Notification notification, DigestGroup.Key group, int recipientId, Integer accountId, boolean mailWanted) {}
