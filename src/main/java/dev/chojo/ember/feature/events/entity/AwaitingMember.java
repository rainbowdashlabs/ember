/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/** Somebody who still owes an answer, named so a guardian can distinguish their children. */
public record AwaitingMember(int memberId, String name) {}
