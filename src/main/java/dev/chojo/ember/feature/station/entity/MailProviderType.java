/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * Supported mail provider types for station email configuration.
 */
public enum MailProviderType {
    NONE,
    SMTP,
    RAPIDMAIL,
    TWILIO,
    SWEEGO,
    BREVO
}
