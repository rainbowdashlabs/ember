/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

/** How a station or an association came to ask a person to take their account in. */
public enum LinkOrigin {
    /** A station import found the account by the address of one of its members. */
    IMPORT,
    /** A member manager invited the address of an account that already exists. */
    INVITE,
    /** An association administrator added the address of an account that already exists. */
    ASSOCIATION_INVITE
}
