/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/**
 * Enumerates the types of one-time tokens used for account operations.
 */
public enum TokenType {
    /**
     * Token sent to verify a newly registered email address.
     */
    VERIFY_EMAIL,
    /**
     * Token sent to set an initial password for an invited account.
     */
    SET_PASSWORD,
    /**
     * Token sent when a user requests a password reset.
     */
    RESET_PASSWORD,
    /**
     * Token issued at login when an admin has forced a password change.
     */
    FORCE_PASSWORD_CHANGE,
    /**
     * Token issued at login when an account that administers the instance carries no address mail
     * can reach, and has to be given one before there can be a session.
     */
    FORCE_ADDRESS,
    /**
     * Legacy single-step email-change token retained for stored rows. The current code
     * no longer issues this type; see {@link #EMAIL_CHANGE_RELEASE} and
     * {@link #EMAIL_CHANGE_CLAIM} for the two-step flow.
     */
    EMAIL_CHANGE,
    /**
     * Token sent to the user's existing address to authorise releasing it for an
     * email change. Metadata format: {@code <requestId>|<newEmail>}; the change only
     * commits once the matching {@link #EMAIL_CHANGE_CLAIM} has also been clicked.
     */
    EMAIL_CHANGE_RELEASE,
    /**
     * Token sent to the new address to confirm receipt for an email change. Metadata
     * format: {@code <requestId>|<newEmail>}; the change only commits once the matching
     * {@link #EMAIL_CHANGE_RELEASE} has also been clicked.
     */
    EMAIL_CHANGE_CLAIM,
    /**
     * Token sent to confirm station deletion, with the station ID stored as metadata.
     */
    STATION_DELETE,
    TWO_FACTOR_PENDING,
    /**
     * Short-lived token holding a serialized {@code PublicKeyCredentialCreationOptions} while
     * the user completes a WebAuthn registration ceremony. Metadata stores the JSON the client
     * must hand back to {@code /webauthn/register/confirm}.
     */
    TWO_FACTOR_WEBAUTHN_REG,
    /**
     * Short-lived token holding a serialized {@code AssertionRequest} while the user completes
     * a WebAuthn assertion ceremony for login or step-up. Metadata stores the JSON the client
     * must hand back to the matching {@code /webauthn/.../finish} endpoint.
     */
    TWO_FACTOR_WEBAUTHN_ASSERT
}
