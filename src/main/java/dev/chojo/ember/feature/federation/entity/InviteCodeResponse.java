/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

/**
 * A pairing code a station hands to another station, which enters it to become its partner.
 *
 * @param inviteCode the code to pass on
 */
public record InviteCodeResponse(String inviteCode) {}
