/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

/**
 * Guardian contact information submitted alongside a waiting-list registration.
 */
public record GuardianInput(String firstname, String lastname, String email, String phone) {}
