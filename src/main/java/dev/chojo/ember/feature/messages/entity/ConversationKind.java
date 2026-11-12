/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** What kind of conversation it is, which decides who may join it and who decides about it. */
public enum ConversationKind {
    /** Exactly two accounts, at most one conversation per pair. */
    DIRECT,
    /** Several accounts with an owner and an optional name. */
    GROUP
}
