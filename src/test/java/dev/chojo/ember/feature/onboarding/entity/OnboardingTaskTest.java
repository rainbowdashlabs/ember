/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.chojo.ember.feature.onboarding.entity.OnboardingTask.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order of the first steps and what each waits for. A changed line here changes which three tasks
 * a newcomer sees first, so every task is named rather than only the ones that wait.
 */
class OnboardingTaskTest {

    @Test
    void everyTaskWaitsForExactlyWhatItShould() {
        Map<OnboardingTask, Set<OnboardingTask>> expected = new EnumMap<>(OnboardingTask.class);
        for (OnboardingTask task : values()) expected.put(task, Set.of());
        expected.put(PROFILE_FIELDS, Set.of(TOUR));
        expected.put(NOTIFICATIONS, Set.of(TOUR));
        expected.put(PUSH, Set.of(NOTIFICATIONS));
        expected.put(EVENT_ANSWER, Set.of(TOUR));
        expected.put(CALENDAR_FEED, Set.of(EVENT_ANSWER));
        expected.put(ABSENCE, Set.of(TOUR, EVENT_ANSWER));
        expected.put(BOOKMARK, Set.of(TOUR, PUSH));
        expected.put(WIKI, Set.of(TOUR));
        expected.put(QUIZ, Set.of(TOUR));
        expected.put(GUARDIAN_USERNAME, Set.of(GUARDIAN_PROFILE));
        expected.put(GUARDIAN_LOGIN, Set.of(GUARDIAN_USERNAME));
        expected.put(GUARDIAN_PASSWORD, Set.of(GUARDIAN_LOGIN));
        expected.put(GUARDIAN_EVENT_ANSWER, Set.of(GUARDIAN_PROFILE));
        expected.put(STATION_INVITES, Set.of(STATION_MAIL, STATION_MEMBER_TYPES));
        expected.put(STATION_FIRST_EVENT, Set.of(STATION_GROUPS));
        expected.put(STATION_FEDERATION, Set.of(STATION_INVITES));
        expected.put(INSTANCE_MAIL, Set.of(INSTANCE_OWN_ACCOUNT));
        expected.put(INSTANCE_LEGAL, Set.of(INSTANCE_MAIL));
        expected.put(INSTANCE_FIRST_STATION, Set.of(INSTANCE_MAIL, INSTANCE_LEGAL));
        expected.put(INSTANCE_STATION_REGISTRATION, Set.of(INSTANCE_FIRST_STATION));
        expected.put(INSTANCE_SECURITY, Set.of(INSTANCE_OWN_ACCOUNT));
        expected.put(INSTANCE_STORAGE, Set.of(INSTANCE_OWN_ACCOUNT));
        expected.put(INSTANCE_OPERATIONS, Set.of(INSTANCE_OWN_ACCOUNT));

        for (OnboardingTask task : values()) {
            assertEquals(expected.get(task), task.prerequisites(), task.key());
        }
    }

    /** Otherwise the first open task could be held back, and a level could show nothing to do at all. */
    @Test
    void aTaskOnlyWaitsForEarlierTasksOfItsOwnLevel() {
        for (OnboardingTask task : values()) {
            for (OnboardingTask before : task.prerequisites()) {
                assertEquals(task.level(), before.level(), task.key());
                assertTrue(before.ordinal() < task.ordinal(), task.key() + " waits for the later " + before.key());
            }
        }
    }

    @Test
    void aStationIsSetUpInTheOrderItsStepsBuildOnEachOther() {
        assertEquals(
                List.of(
                        STATION_MAIL,
                        STATION_MEMBER_TYPES,
                        STATION_GROUPS,
                        STATION_INVITES,
                        STATION_BRANDING,
                        STATION_FIRST_EVENT,
                        STATION_KB_SEED,
                        STATION_FEDERATION),
                OnboardingTask.of(OnboardingLevel.STATION));
    }
}
