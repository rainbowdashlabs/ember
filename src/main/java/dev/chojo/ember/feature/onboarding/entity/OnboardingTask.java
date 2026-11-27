/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.station.entity.StationModule;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Everything Ember asks somebody to do as their first steps, in the order it asks.
 *
 * <p>Some tasks wait for others ({@link #prerequisites()}): inviting members before mail goes out
 * sends invitations that never arrive. The order here already puts a task after what it waits for,
 * so the first few ready tasks are the ones worth doing next.
 *
 * <p>A derived task reads its answer from the data and cannot be ticked off by hand, so it comes
 * back on its own when the thing behind it is undone. A task that is not derived is one Ember cannot
 * see, such as a bookmark, or a decision whose outcome is indistinguishable from the default.
 *
 * <p>A task naming modules is offered only where at least one of them is on, and one naming an
 * audience only to the readers that audience covers. Nothing here can point at a page its reader
 * cannot open.
 *
 * <p>A task that teaches how Ember works is the same on every station, so somebody who settled it on
 * one station of this instance is not asked again on the next. A task about the station's own data
 * or settings is asked on every station.
 */
public enum OnboardingTask {
    /**
     * A walk past the areas of the station that are on for the reader. It comes first because it
     * shows where the later tasks happen.
     */
    TOUR("member.tour", OnboardingLevel.MEMBER, false),
    /**
     * Read rather than derived, although the profile is a thing Ember can look at. The task asks
     * somebody to go and check what is written about them, and a profile that happens to be complete
     * is exactly the one worth checking, so a station's data settling it would hide the task from the
     * people it is for.
     */
    PROFILE_FIELDS("member.profile", OnboardingLevel.MEMBER, false),
    NOTIFICATIONS("member.notifications", OnboardingLevel.MEMBER, true),
    /**
     * Done once the account allowed push on any device, which is the account's and therefore the
     * same on every station; skipped for a device that cannot receive pushes. Only asked while the
     * instance sends pushes.
     */
    PUSH("member.push", OnboardingLevel.MEMBER, true),
    /**
     * Read rather than derived, because it explains a difference rather than asking for an answer.
     * Deriving it from an answer having been given made the walk end in a sign-up nobody had asked
     * for, and settled the task for anybody who had once answered anything without ever showing them
     * what the two kinds of appointment are.
     */
    EVENT_ANSWER("member.eventAnswer", OnboardingLevel.MEMBER, false, StationModule.EVENTS),
    CALENDAR_FEED("member.calendar", OnboardingLevel.MEMBER, true, StationModule.EVENTS),
    /** Absences are read by appointments and by attendance, so the task stays while either runs. */
    ABSENCE(
            "member.absence",
            OnboardingLevel.MEMBER,
            false,
            Audience.EVERYONE,
            StationModule.EVENTS,
            StationModule.ATTENDANCE),
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

    STATION_MAIL("station.mail", OnboardingLevel.STATION, true),
    STATION_MEMBER_TYPES("station.memberTypes", OnboardingLevel.STATION, false),
    STATION_GROUPS("station.groups", OnboardingLevel.STATION, true),
    STATION_INVITES("station.invites", OnboardingLevel.STATION, true),
    STATION_BRANDING("station.branding", OnboardingLevel.STATION, true),
    STATION_FIRST_EVENT("station.firstEvent", OnboardingLevel.STATION, true, StationModule.EVENTS),
    STATION_KB_SEED("station.kbSeed", OnboardingLevel.STATION, true, StationModule.KNOWLEDGE_BASE),
    STATION_FEDERATION("station.federation", OnboardingLevel.STATION, true),

    INSTANCE_OWN_ACCOUNT("instance.ownAccount", OnboardingLevel.INSTANCE, true),
    INSTANCE_MAIL("instance.mail", OnboardingLevel.INSTANCE, true),
    INSTANCE_LEGAL("instance.legal", OnboardingLevel.INSTANCE, true),
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
    public record Audience(
            @Nullable StationUserType only, @Nullable StationUserType except) {
        /** Everybody who signs in. */
        public static final Audience EVERYONE = new Audience(null, null);

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
    private final Set<StationModule> modules;
    private final Audience audience;

    OnboardingTask(String key, OnboardingLevel level, boolean derived) {
        this(key, level, derived, Audience.EVERYONE);
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, StationModule module) {
        this(key, level, derived, Audience.EVERYONE, module);
    }

    /**
     * A task that needs no module. Kept apart from the one that names a module rather than handed a
     * {@code null}, because SpotBugs does not see the nullness marks on an enum constructor's
     * parameters.
     */
    OnboardingTask(String key, OnboardingLevel level, boolean derived, Audience audience) {
        this.key = key;
        this.level = level;
        this.derived = derived;
        this.audience = audience;
        this.modules = Set.of();
    }

    OnboardingTask(String key, OnboardingLevel level, boolean derived, Audience audience, StationModule... modules) {
        this.key = key;
        this.level = level;
        this.derived = derived;
        this.audience = audience;
        this.modules = Set.of(modules);
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

    /**
     * Whether this task is offered on a station with these modules switched off: always when it
     * names none, otherwise while at least one of its modules runs.
     */
    public boolean runsWith(Set<StationModule> disabled) {
        return modules.isEmpty() || modules.stream().anyMatch(module -> !module.isOffIn(disabled));
    }

    /**
     * Whether settling this task on one station of the instance settles it on the others. Only the
     * tasks that teach how Ember works do: they read the same everywhere.
     */
    public boolean carriesAcrossStations() {
        return switch (this) {
            case TOUR, EVENT_ANSWER, ABSENCE, BOOKMARK, WIKI, QUIZ, PUSH -> true;
            default -> false;
        };
    }

    /**
     * The tasks of the same level this one waits for. It is ready once each of them is done, passed
     * over or thrown away, or is not asked of this reader at all. A task about a member in somebody's
     * care waits for the task about the same member.
     */
    public Set<OnboardingTask> prerequisites() {
        return switch (this) {
            case PROFILE_FIELDS, NOTIFICATIONS, EVENT_ANSWER, WIKI, QUIZ -> Set.of(TOUR);
            case PUSH -> Set.of(NOTIFICATIONS);
            case CALENDAR_FEED -> Set.of(EVENT_ANSWER);
            case ABSENCE -> Set.of(TOUR, EVENT_ANSWER);
            case BOOKMARK -> Set.of(TOUR, PUSH);
            case GUARDIAN_USERNAME, GUARDIAN_EVENT_ANSWER -> Set.of(GUARDIAN_PROFILE);
            case GUARDIAN_LOGIN -> Set.of(GUARDIAN_USERNAME);
            case GUARDIAN_PASSWORD -> Set.of(GUARDIAN_LOGIN);
            case STATION_INVITES -> Set.of(STATION_MAIL, STATION_MEMBER_TYPES);
            case STATION_FIRST_EVENT -> Set.of(STATION_GROUPS);
            case STATION_FEDERATION -> Set.of(STATION_INVITES);
            case INSTANCE_MAIL, INSTANCE_SECURITY, INSTANCE_STORAGE, INSTANCE_OPERATIONS ->
                Set.of(INSTANCE_OWN_ACCOUNT);
            case INSTANCE_LEGAL -> Set.of(INSTANCE_MAIL);
            case INSTANCE_FIRST_STATION -> Set.of(INSTANCE_MAIL, INSTANCE_LEGAL);
            case INSTANCE_STATION_REGISTRATION -> Set.of(INSTANCE_FIRST_STATION);
            default -> Set.of();
        };
    }

    /** Whether this task is asked of the given user type at all. */
    public boolean reaches(StationUserType userType) {
        return audience.includes(userType);
    }

    /**
     * Whether this task is asked once per member in somebody's care rather than once. A guardian
     * looking after two children sets up two children, and one tick may not stand for both.
     */
    public boolean perManagedMember() {
        return audience.only() == StationUserType.GUARDIAN;
    }

    /** The tasks of one level, in the order they are asked. */
    public static List<OnboardingTask> of(OnboardingLevel level) {
        return Arrays.stream(values()).filter(task -> task.level == level).toList();
    }

    public static Optional<OnboardingTask> byKey(String key) {
        return Arrays.stream(values()).filter(task -> task.key.equals(key)).findFirst();
    }
}
