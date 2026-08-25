/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.station.entity.StationModule;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Everything Ember asks somebody to do after the introduction tour, in the order it asks.
 *
 * <p>A derived task reads its answer from the data and cannot be ticked off by hand, so it comes
 * back on its own when the thing behind it is undone. A task that is not derived is one Ember cannot
 * see, such as a bookmark, or a decision whose outcome is indistinguishable from the default.
 *
 * <p>A task naming a module is offered only where that module is on, and one naming a user type only
 * to that type. Nothing here can point at a page its reader cannot open.
 */
public enum OnboardingTask {
    PROFILE_FIELDS("member.profile", OnboardingLevel.MEMBER, true),
    NOTIFICATIONS("member.notifications", OnboardingLevel.MEMBER, true),
    EVENT_ANSWER("member.eventAnswer", OnboardingLevel.MEMBER, true, StationModule.EVENTS),
    CALENDAR_FEED("member.calendar", OnboardingLevel.MEMBER, true, StationModule.EVENTS),
    ABSENCE("member.absence", OnboardingLevel.MEMBER, false),
    BOOKMARK("member.bookmark", OnboardingLevel.MEMBER, false),
    WIKI("member.wiki", OnboardingLevel.MEMBER, false, StationModule.KNOWLEDGE_BASE),
    QUIZ("member.quiz", OnboardingLevel.MEMBER, false, StationModule.QUIZ),

    GUARDIAN_PROFILE("guardian.profile", OnboardingLevel.MEMBER, false, StationUserType.GUARDIAN),
    GUARDIAN_USERNAME("guardian.username", OnboardingLevel.MEMBER, true, StationUserType.GUARDIAN),
    GUARDIAN_LOGIN("guardian.login", OnboardingLevel.MEMBER, true, StationUserType.GUARDIAN),
    GUARDIAN_PASSWORD("guardian.password", OnboardingLevel.MEMBER, true, StationUserType.GUARDIAN),
    GUARDIAN_EVENT_ANSWER(
            "guardian.eventAnswer", OnboardingLevel.MEMBER, true, StationUserType.GUARDIAN, StationModule.EVENTS),

    STATION_GROUPS("station.groups", OnboardingLevel.STATION, true),
    STATION_MAIL("station.mail", OnboardingLevel.STATION, true),
    STATION_BRANDING("station.branding", OnboardingLevel.STATION, true),
    STATION_FIRST_EVENT("station.firstEvent", OnboardingLevel.STATION, true, StationModule.EVENTS),
    STATION_KB_SEED("station.kbSeed", OnboardingLevel.STATION, true, StationModule.KNOWLEDGE_BASE),
    STATION_FEDERATION("station.federation", OnboardingLevel.STATION, true),
    STATION_INVITES("station.invites", OnboardingLevel.STATION, true),
    STATION_MEMBER_TYPES("station.memberTypes", OnboardingLevel.STATION, false),

    INSTANCE_OWN_ACCOUNT("instance.ownAccount", OnboardingLevel.INSTANCE, true),
    INSTANCE_LEGAL("instance.legal", OnboardingLevel.INSTANCE, true),
    INSTANCE_MAIL("instance.mail", OnboardingLevel.INSTANCE, true),
    INSTANCE_FIRST_STATION("instance.firstStation", OnboardingLevel.INSTANCE, true),
    INSTANCE_STATION_REGISTRATION("instance.stationRegistration", OnboardingLevel.INSTANCE, false),
    INSTANCE_SECURITY("instance.security", OnboardingLevel.INSTANCE, false),
    INSTANCE_STORAGE("instance.storage", OnboardingLevel.INSTANCE, false),
    INSTANCE_OPERATIONS("instance.operations", OnboardingLevel.INSTANCE, false);

    private final String key;
    private final OnboardingLevel level;
    private final boolean derived;
    private final StationModule module;
    private final StationUserType userType;

    OnboardingTask(String key, OnboardingLevel level, boolean derived) {
        this(key, level, derived, null, null);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, StationModule module) {
        this(key, level, derived, null, module);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, StationUserType userType) {
        this(key, level, derived, userType, null);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, StationUserType userType, StationModule module) {
        this.key = key;
        this.level = level;
        this.derived = derived;
        this.userType = userType;
        this.module = module;
    }

    /** The stable name this task is stored and translated under. */
    public String key() {
        return key;
    }

    public OnboardingLevel level() {
        return level;
    }

    /** Whether the answer is read from the data rather than ticked off by hand. */
    public boolean derived() {
        return derived;
    }

    public Optional<StationModule> module() {
        return Optional.ofNullable(module);
    }

    /** The one user type this task is for, or empty when it is for everybody who signs in. */
    public Optional<StationUserType> userType() {
        return Optional.ofNullable(userType);
    }

    /**
     * Whether this task is asked once per member in somebody's care rather than once. A guardian
     * looking after two children sets up two children, and one tick may not stand for both.
     */
    public boolean perManagedMember() {
        return userType == StationUserType.GUARDIAN;
    }

    /** The tasks of one level, in the order they are asked. */
    public static List<OnboardingTask> of(OnboardingLevel level) {
        return Arrays.stream(values()).filter(task -> task.level == level).toList();
    }

    public static Optional<OnboardingTask> byKey(String key) {
        return Arrays.stream(values()).filter(task -> task.key.equals(key)).findFirst();
    }
}
