/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Warns whoever still owes an answer that sign-ups are about to close, the same way for an appointment and
 * for an event group.
 *
 * <p>Three days out and one day out, each sent once: the sweeps run every few minutes, and a warning that
 * arrived every sweep would be worse than none. The lead times are tried nearest first, and a warning marks
 * every wider lead as warned too, so something whose deadline is already inside several lead times gets the
 * nearest warning only and never a second one about a day that has passed.
 *
 * <p>Only members the thing is meant for are warned, and a station kept read-only is left alone. The
 * warning goes to the member and to whoever looks after them, so a guardian hears once about each person
 * in their household who has not answered.
 */
final class ClosingWarnings {
    private static final Logger log = LoggerFactory.getLogger(ClosingWarnings.class);
    /** The lead times warned about in days, nearest first. */
    private static final List<Integer> LEAD_DAYS = List.of(1, 3);

    /** The widest lead time warned about, in days. */
    static final int MAX_LEAD_DAYS =
            LEAD_DAYS.stream().mapToInt(Integer::intValue).max().orElseThrow();

    private final Notifier notifier;
    private final MemberNameResolver names;
    private final StationReadOnlyGuard readOnlyGuard;

    ClosingWarnings(Notifier notifier, MemberNameResolver names, StationReadOnlyGuard readOnlyGuard) {
        this.notifier = notifier;
        this.names = names;
        this.readOnlyGuard = readOnlyGuard;
    }

    /**
     * Runs one round of warnings over everything one kind of thing has closing.
     *
     * @param kind what is closing, who owes an answer for it and how its warnings are noted
     */
    void warn(Closable kind) {
        for (int daysBefore : LEAD_DAYS) {
            for (var closing : kind.closingIn(daysBefore)) {
                if (!readOnlyGuard.isWritable(closing.stationId())) continue;
                int warned = 0;
                for (int memberId : kind.unansweredMemberIds(closing)) {
                    if (!kind.meantFor(closing, memberId)) continue;
                    warned += warnAbout(kind, closing, memberId, daysBefore) ? 1 : 0;
                }
                for (int wider : LEAD_DAYS) {
                    if (wider >= daysBefore) kind.markWarned(closing, wider);
                }
                log.info(
                        "Warned {} member(s) that registration for '{}' (id={}) closes in {} day(s)",
                        warned,
                        closing.name(),
                        closing.id(),
                        daysBefore);
            }
        }
    }

    private boolean warnAbout(Closable kind, Closing closing, int memberId, int daysBefore) {
        int told = notifier.notify(
                StationAudience.household(List.of(memberId)),
                NotificationType.REGISTRATION_CLOSING,
                NotificationData.of(
                        new NotificationParams.RegistrationClosing(
                                closing.name(), daysBefore, names.called(memberId), closing.namedDate()),
                        kind.linkTo(closing.id())),
                Delivery.EVERY_TIME);
        return told > 0;
    }

    /**
     * Something whose sign-ups close soon.
     *
     * @param id        its id
     * @param stationId its station
     * @param name      its name, as the warning shows it
     * @param date      the date of an appointment whose deadline this is, since a series closes each
     *                  date on its own; null for an event group, which closes once
     * @param series    whether the appointment repeats, so the warning names the date
     */
    record Closing(
            int id, int stationId, String name, @Nullable LocalDate date, boolean series) {
        /** Something that closes once, or a date not worth naming. */
        Closing(int id, int stationId, String name, @Nullable LocalDate date) {
            this(id, stationId, name, date, false);
        }

        /** The date the warning names: the one of a series, which closes each date on its own. */
        @Nullable
        LocalDate namedDate() {
            return series ? date : null;
        }
    }

    /** One kind of thing with a sign-up deadline: what differs between an appointment and an event group. */
    interface Closable {
        /**
         * What closes within a lead time and has not been warned about for it.
         *
         * @param daysBefore the lead time
         * @return those things
         */
        List<Closing> closingIn(int daysBefore);

        /**
         * The members of its station who have not answered it yet.
         *
         * @param closing the thing
         * @return those members
         */
        List<Integer> unansweredMemberIds(Closing closing);

        /**
         * Whether the thing is meant for a member, who then has something to answer.
         *
         * @param closing  the thing
         * @param memberId the member
         * @return true where they are to be warned
         */
        boolean meantFor(Closing closing, int memberId);

        /**
         * Notes that the warning for a lead time went out.
         *
         * @param closing    the thing
         * @param daysBefore the lead time
         */
        void markWarned(Closing closing, int daysBefore);

        /**
         * Where the warning leads.
         *
         * @param id the thing
         * @return the link
         */
        NotificationLink linkTo(int id);
    }
}
