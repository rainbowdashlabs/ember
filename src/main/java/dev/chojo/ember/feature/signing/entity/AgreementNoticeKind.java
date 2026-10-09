/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * What a member's home installation tells the station holding a shared appointment about a document that
 * appointment asks the member to sign.
 */
public enum AgreementNoticeKind {
    /** The home installation took the document on and asks the member, or their guardians, to sign it there. */
    ASKED,
    /** A sealed state of the signed document, which comes with the notice. */
    SIGNED,
    /**
     * A signed agreement was withdrawn at home; the sealed state that records the withdrawal comes with the
     * notice. The station holding the appointment keeps it beside the copies before and flags the agreement.
     */
    WITHDRAWN
}
