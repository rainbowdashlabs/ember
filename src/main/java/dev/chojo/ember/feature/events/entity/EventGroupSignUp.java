/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * One member's standing in an event group.
 *
 * @param memberId       the member's id, which sign-ups and decisions for them name
 * @param member         the member
 * @param status         the sign-up's status, or null where the member is not signed up
 * @param registeredAt   when they signed up, or null
 * @param parts          the parts that count for the rules, in id order
 * @param sets           how those parts stand against every set
 * @param answers        the answers given to the event group's questions, those kept for organisers only
 *                       where the reader may read them
 * @param answersMissing whether a standing sign-up still owes an answer to a required question
 * @param mayRegister    whether the member is one of the people the event group takes
 * @param queuePosition  the place in line on the waiting list, 1 moving up next; null where the sign-up
 *                       does not wait
 */
public record EventGroupSignUp(
        int memberId,
        MemberIdentity member,
        @Nullable RegistrationStatus status,
        @Nullable Instant registeredAt,
        List<Integer> parts,
        List<SetResult> sets,
        List<EventRegistrationFieldValue> answers,
        boolean answersMissing,
        boolean mayRegister,
        @Nullable Integer queuePosition) {}
