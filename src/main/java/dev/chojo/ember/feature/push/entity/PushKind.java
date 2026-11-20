/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.entity;

/** What a queued push is about, which decides how it is written once it is due. */
public enum PushKind {
    /** A test the account sent to one of its devices. */
    TEST,
    /** A notification of the account's inbox. */
    NOTIFICATION,
    /** A message in one of the account's conversations. */
    MESSAGE
}
