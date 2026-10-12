/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.account.entity.Account;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything one address receives from one station or association in one sweep.
 *
 * @param address the address the mail goes to, as its owner wrote it
 * @param owner   the account the address belongs to where it reads any of the items itself,
 *                {@code null} where the address receives only on behalf of others
 * @param entries the notifications, each once, oldest first
 */
public record DigestMail(String address, @Nullable Account owner, List<Entry> entries) {

    /**
     * One notification in the mail.
     *
     * @param notification the notification
     * @param forMembers   the names of the members it reaches this address for, empty where the
     *                     owner of the address receives it themselves
     */
    public record Entry(Notification notification, List<String> forMembers) {}
}
