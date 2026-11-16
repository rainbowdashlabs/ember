/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * An event group still waiting on an answer, and everybody in the household who owes one.
 *
 * @param eventGroupId         the event group
 * @param name                 its name
 * @param registrationDeadline when sign-ups close
 * @param members              who still owes an answer
 */
public record EventGroupAwaiting(
        int eventGroupId, String name, @Nullable Instant registrationDeadline, List<AwaitingMember> members) {}
