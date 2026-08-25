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
 * <p>A task naming a module is offered only where that module is on, and one naming an audience only
 * to the readers that audience covers. Nothing here can point at a page its reader cannot open.
 */
public enum OnboardingTask {
    PROFILE_FIELDS("member.profile", OnboardingLevel.MEMBER, true),
    NOTIFICATIONS("member.notifications", OnboardingLevel.MEMBER, true),
    EVENT_ANSWER("member.eventAnswer", OnboardingLevel.MEMBER, true, StationModule.EVENTS),
    CALENDAR_FEED("member.calendar", OnboardingLevel.MEMBER, true, StationModule.EVENTS),
    ABSENCE("member.absence", OnboardingLevel.MEMBER, false),
    BOOKMARK("member.bookmark", OnboardingLevel.MEMBER, false),
    WIKI("member.wiki", OnboardingLevel.MEMBER, false, StationModule.KNOWLEDGE_BASE),
    QUIZ("member.quiz", OnboardingLevel.MEMBER, false, Audience.except(StationUserType.GUARDIAN), StationModule.QUIZ),

    GUARDIAN_PROFILE("guardian.profile", OnboardingLevel.MEMBER, false, Audience.only(StationUserType.GUARDIAN)),
    GUARDIAN_USERNAME("guardian.username", OnboardingLevel.MEMBER, true, Audience.only(StationUserType.GUARDIAN)),
    GUARDIAN_LOGIN("guardian.login", OnboardingLevel.MEMBER, true, Audience.only(StationUserType.GUARDIAN)),
    GUARDIAN_PASSWORD("guardian.password", OnboardingLevel.MEMBER, true, Audience.only(StationUserType.GUARDIAN)),
    GUARDIAN_EVENT_ANSWER(
            "guardian.eventAnswer",
            OnboardingLevel.MEMBER,
            true,
            Audience.only(StationUserType.GUARDIAN),
            StationModule.EVENTS),

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

    /**
     * Who a task is asked of.
     *
     * <p>A task without one is asked of everybody who signs in. A task reserved for a type reaches
     * only that type, which is how the guardian chain stays out of a member's list. A task withheld
     * from a type reaches everybody else, which is what a task about the reader's own training
     * needs: it concerns a guardian's child rather than the guardian.
     */
    public record Audience(StationUserType only, StationUserType except) {
        public static Audience only(StationUserType type) {
            return new Audience(type, null);
        }

        public static Audience except(StationUserType type) {
            return new Audience(null, type);
        }

        public boolean includes(StationUserType type) {
            return only != null ? only == type : except != type;
        }
    }

    private final String key;
    private final OnboardingLevel level;
    private final boolean derived;
    private final StationModule module;
    private final Audience audience;

    OnboardingTask(String key, OnboardingLevel level, boolean derived) {
        this(key, level, derived, null, null);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, StationModule module) {
        this(key, level, derived, null, module);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, Audience audience) {
        this(key, level, derived, audience, null);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, Audience audience, StationModule module) {
        this.key = key;
        this.level = level;
        this.derived = derived;
        this.audience = audience;
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

    /** Whether this task is asked of the given user type at all. */
    public boolean reaches(StationUserType userType) {
        return audience == null || audience.includes(userType);
    }

    /**
     * Whether this task is asked once per member in somebody's care rather than once. A guardian
     * looking after two children sets up two children, and one tick may not stand for both.
     */
    public boolean perManagedMember() {
        return audience != null && audience.only() == StationUserType.GUARDIAN;
    }

    /** The tasks of one level, in the order they are asked. */
    public static List<OnboardingTask> of(OnboardingLevel level) {
        return Arrays.stream(values()).filter(task -> task.level == level).toList();
    }

    public static Optional<OnboardingTask> byKey(String key) {
        return Arrays.stream(values()).filter(task -> task.key.equals(key)).findFirst();
    }
}
