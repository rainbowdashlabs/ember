/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.station.entity.ReminderAnswer;
import dev.chojo.ember.feature.station.entity.ReminderKind;
import dev.chojo.ember.feature.station.entity.ReminderResult;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.StationOverview;
import dev.chojo.ember.feature.station.entity.StationState;
import dev.chojo.ember.feature.station.repository.ManagerReminderRepository;
import dev.chojo.ember.feature.station.repository.StationOverviewRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Lists the stations for the instance administration and reminds the manager of a station that waits.
 *
 * <p>Reminders by hand and automatic ones go through the same rules and send the same mail. A manager who
 * cannot sign in yet is sent a fresh link: to choose a password where none was chosen, to confirm the
 * address where one was. A manager who can sign in is told the station waits to be set up, in the
 * station's language. Every reminder is written down in the same transaction that queues its mail, so a
 * reminder that was not written was not sent either.
 */
@Singleton
public class ManagerReminderService {
    private static final Logger log = LoggerFactory.getLogger(ManagerReminderService.class);

    /** How long after a reminder by hand a second one by hand is refused, so a double click sends one mail. */
    static final Duration HAND_GAP = Duration.ofMinutes(10);

    /** How long after any reminder an automatic one is skipped, so the manager is not written twice in a day. */
    static final Duration AUTOMATIC_GAP = Duration.ofHours(24);

    /** How long a station waits before the first automatic reminder. */
    static final Duration AFTER_7 = Duration.ofDays(7);

    /** How long a station waits before the second and last automatic reminder. */
    static final Duration AFTER_21 = Duration.ofDays(21);

    /** How long after the wait began the last automatic reminder is given up when it could not go out. */
    static final Duration GIVE_UP_AFTER = Duration.ofDays(35);

    private final StationOverviewRepository overviews;
    private final ManagerReminderRepository reminders;
    private final StationRepository stations;
    private final AccountRepository accounts;
    private final MailRecipientService recipients;
    private final MailLocaleService mailLocales;
    private final AuthService authService;
    private final EmailService emailService;
    private final Clock clock;

    @Inject
    public ManagerReminderService(
            StationOverviewRepository overviews,
            ManagerReminderRepository reminders,
            StationRepository stations,
            AccountRepository accounts,
            MailRecipientService recipients,
            MailLocaleService mailLocales,
            AuthService authService,
            EmailService emailService) {
        this(
                overviews,
                reminders,
                stations,
                accounts,
                recipients,
                mailLocales,
                authService,
                emailService,
                Clock.systemUTC());
    }

    ManagerReminderService(
            StationOverviewRepository overviews,
            ManagerReminderRepository reminders,
            StationRepository stations,
            AccountRepository accounts,
            MailRecipientService recipients,
            MailLocaleService mailLocales,
            AuthService authService,
            EmailService emailService,
            Clock clock) {
        this.overviews = overviews;
        this.reminders = reminders;
        this.stations = stations;
        this.accounts = accounts;
        this.recipients = recipients;
        this.mailLocales = mailLocales;
        this.authService = authService;
        this.emailService = emailService;
        this.clock = clock;
    }

    /**
     * What sending a reminder's mail came to.
     */
    enum Delivery {
        /** The mail was queued. */
        SENT,
        /** The instance has no way of confirming an address and counted it as confirmed, so no mail was needed. */
        CONFIRMED_WITHOUT_MAIL,
        /** The address had been confirmed meanwhile, so there was nothing to send. */
        ALREADY_CONFIRMED,
        /** Nobody could be written to, so nothing was queued. */
        NOTHING_QUEUED
    }

    /**
     * What an automatic reminder came to.
     */
    public enum Outcome {
        /** The reminder went out. */
        SENT,
        /** The reminder was written down as skipped, because another one went out shortly before. */
        SKIPPED,
        /** Nothing was sent: the manager cannot be written to, or nothing could be queued for them. */
        PASSED_OVER,
        /** Nothing was written: the station no longer waits in the period the reminder was due for. */
        NOT_DUE,
        /** Nothing was written: the reminder was already written for this waiting period. */
        ALREADY_WRITTEN
    }

    /**
     * Every station somebody runs, as the instance administration sees them.
     *
     * @return the stations
     */
    public List<StationOverview> list() {
        return overviews.list();
    }

    /**
     * One station as the instance administration sees it.
     *
     * @param uid the station's identifier
     * @return the station, or empty when there is none by that identifier or it is an association's home
     */
    public Optional<StationOverview> find(UUID uid) {
        return stations.resolveId(uid).flatMap(overviews::find);
    }

    /**
     * Reminds a station's manager by hand.
     *
     * <p>Refused for a station that moved away, is set up or has no manager, within {@link #HAND_GAP} of the
     * last reminder by hand, where nobody can be written to, and where the address was confirmed meanwhile.
     * A station that waited for nobody so far starts waiting now. Where the instance counts an address as
     * confirmed without asking, the manager's address is confirmed instead of written to, and nothing is
     * recorded as sent.
     *
     * @param stationId       the station
     * @param administratorId the instance administrator sending it
     * @return the station as it stands after the reminder, and whether a mail went out
     */
    public ReminderAnswer remindByHand(int stationId, int administratorId) {
        ReminderResult result = Transactions.call(() -> {
            reminders.lockStation(stationId);
            StationOverview station =
                    overviews.find(stationId).orElseThrow(StationRefusal.STATION_NOT_HERE_BY_NAME::raise);
            requireWaiting(station);
            Instant now = clock.instant();
            if (reminders.sentByHandSince(stationId, now.minus(HAND_GAP))) {
                throw StationRefusal.MANAGER_REMINDED_JUST_NOW.raise();
            }
            Account manager = managerOf(station).orElseThrow(StationRefusal.STATION_HAS_NO_MANAGER::raise);
            if (!canWriteTo(station, manager)) {
                throw MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO.raise();
            }
            stations.startWaitingIfIdle(stationId);
            switch (send(station, manager)) {
                case CONFIRMED_WITHOUT_MAIL -> {
                    return ReminderResult.CONFIRMED_WITHOUT_MAIL;
                }
                case ALREADY_CONFIRMED -> throw MemberRefusal.ACCOUNT_ALREADY_SET_UP.raise();
                case NOTHING_QUEUED -> throw MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO.raise();
                case SENT -> {}
            }
            ReminderKind kind = kindFor(station.state());
            if (!reminders.record(stationId, manager.id(), kind, ReminderTrigger.MANUAL, now, administratorId)) {
                throw StationRefusal.STATION_NOT_HERE_BY_NAME.raise();
            }
            log.info("Administrator {} reminded the manager of station {} ({})", administratorId, stationId, kind);
            return ReminderResult.SENT;
        });
        StationOverview after = overviews.find(stationId).orElseThrow(StationRefusal.STATION_NOT_HERE_BY_NAME::raise);
        return new ReminderAnswer(after, result);
    }

    /**
     * The stations an automatic reminder is due for now. A station that waited past the later reminder
     * before the earlier one went out is due only for the later one, so a long pause of the task never
     * sends both at once. The later reminder is given up {@link #GIVE_UP_AFTER} after the wait began:
     * a manager who could not be written to for two weeks past it is better asked in person.
     *
     * @param trigger the automatic reminder
     * @return the stations it is due for
     */
    public List<StationOverview> dueFor(ReminderTrigger trigger) {
        Instant now = clock.instant();
        return switch (trigger) {
            case AFTER_7_DAYS -> overviews.dueForReminder(trigger, now.minus(AFTER_7), now.minus(AFTER_21));
            case AFTER_21_DAYS -> overviews.dueForReminder(trigger, now.minus(AFTER_21), now.minus(GIVE_UP_AFTER));
            case MANUAL -> List.of();
        };
    }

    /**
     * Sends one automatic reminder, unless the station has moved on since it was found due.
     *
     * <p>The station is read afresh under a lock right before sending. One that moved away, is set up
     * meanwhile, whose manager finished their part, or whose waiting period started anew is not reminded.
     * A manager with a stand-in address, or whose address refused the newest mail sent to it, is passed
     * over without a record and looked at again on the next run. Where any reminder went out within
     * {@link #AUTOMATIC_GAP}, the automatic one is written down as skipped and never tried again. The record
     * is written before the mail is queued, in the same transaction: only the run whose record was written
     * sends, so two runs never send the same reminder twice. Where nothing could be queued after all, the
     * record goes with the rolled back transaction and the reminder is passed over; where the instance
     * counted the address as confirmed instead, the record stays as not sent.
     *
     * <p>Runs outside any transaction of its caller: its own transaction is what undoes the record when
     * nothing could be queued, and joining an outer one would let the record stay.
     *
     * @param stationId    the station found due
     * @param trigger      the reminder it was found due for
     * @param waitingSince the waiting period it was found due in
     * @return what came of it
     */
    public Outcome remindAutomatically(int stationId, ReminderTrigger trigger, Instant waitingSince) {
        try {
            return Transactions.call(() -> remindAutomaticallyLocked(stationId, trigger, waitingSince));
        } catch (NothingQueued e) {
            log.debug(
                    "Nothing could be queued for the manager of station {}, so its {} reminder waits",
                    stationId,
                    trigger);
            return Outcome.PASSED_OVER;
        }
    }

    private Outcome remindAutomaticallyLocked(int stationId, ReminderTrigger trigger, Instant waitingSince) {
        reminders.lockStation(stationId);
        StationOverview station = overviews.find(stationId).orElse(null);
        Account manager = station == null ? null : managerOf(station).orElse(null);
        if (station == null
                || manager == null
                || station.movedAway()
                || !station.state().waitsForManager()
                || !waitingSince.equals(station.waitingSince())) {
            log.info("Station {} no longer waits as it did, so its {} reminder is not sent", stationId, trigger);
            return Outcome.NOT_DUE;
        }
        if (!manager.hasRealEmail() || reminders.newestMailRefused(manager.email())) {
            log.debug("The manager of station {} cannot be written to, so its {} reminder waits", stationId, trigger);
            return Outcome.PASSED_OVER;
        }
        Instant now = clock.instant();
        boolean remindedJustNow = reminders.sentSince(stationId, now.minus(AUTOMATIC_GAP));
        ReminderKind kind = kindFor(station.state());
        if (!reminders.record(stationId, manager.id(), kind, trigger, remindedJustNow ? null : now, null)) {
            return Outcome.ALREADY_WRITTEN;
        }
        if (remindedJustNow) {
            log.info("Station {} was reminded shortly before, so its {} reminder is skipped", stationId, trigger);
            return Outcome.SKIPPED;
        }
        Delivery delivery = send(station, manager);
        if (delivery == Delivery.CONFIRMED_WITHOUT_MAIL) {
            reminders.markNotSent(stationId, trigger);
            return Outcome.PASSED_OVER;
        }
        if (delivery != Delivery.SENT) throw new NothingQueued();
        log.info("Reminded the manager of station {} automatically ({}, {})", stationId, trigger, kind);
        return Outcome.SENT;
    }

    /** Rolls back an automatic reminder whose record was written but whose mail reached nobody. */
    private static final class NothingQueued extends RuntimeException {
        private NothingQueued() {
            super(null, null, false, false);
        }
    }

    private static void requireWaiting(StationOverview station) {
        if (station.movedAway()) throw StationRefusal.STATION_MOVED_AWAY.raise();
        if (station.state() == StationState.SET_UP) throw StationRefusal.STATION_ALREADY_SET_UP.raise();
        if (station.state() == StationState.NO_MANAGER) throw StationRefusal.STATION_HAS_NO_MANAGER.raise();
    }

    private Optional<Account> managerOf(StationOverview station) {
        Integer accountId = station.managerAccountId();
        return accountId == null ? Optional.empty() : accounts.findById(accountId);
    }

    private static ReminderKind kindFor(StationState state) {
        return state == StationState.WAITING_FOR_MANAGER ? ReminderKind.SETUP_LINK : ReminderKind.SETUP_REMINDER;
    }

    /**
     * Whether the reminder for the station can reach anybody. A link to confirm an address goes to that
     * address alone, so it needs a real one; every other reminder reaches whoever mail about the account
     * reaches.
     */
    private boolean canWriteTo(StationOverview station, Account manager) {
        if (confirmsAddress(station.state(), manager)) return manager.hasRealEmail();
        return recipients.isReachable(manager.id());
    }

    /**
     * Whether a waiting manager is waited for only to confirm their address: one who chose a password, or
     * finished their account's setup without one, such as with a passkey, needs no link to choose one.
     */
    private boolean confirmsAddress(StationState state, Account manager) {
        return state == StationState.WAITING_FOR_MANAGER
                && (manager.setupCompletedAt() != null || accounts.hasChosenPassword(manager.id()));
    }

    private Delivery send(StationOverview station, Account manager) {
        if (station.state() == StationState.WAITING_FOR_MANAGER) {
            if (confirmsAddress(station.state(), manager)) return confirmAddress(manager);
            return authService.sendPasswordSetup(manager.id()) ? Delivery.SENT : Delivery.NOTHING_QUEUED;
        }
        String locale = languageOf(station, manager.id());
        var to = recipients.forAccount(manager);
        for (var recipient : to) {
            emailService.sendStationSetupReminder(
                    recipient.email(), recipient.name(), station.id(), station.name(), station.uid(), locale);
        }
        return to.isEmpty() ? Delivery.NOTHING_QUEUED : Delivery.SENT;
    }

    private Delivery confirmAddress(Account manager) {
        if (!authService.resendVerification(manager.email())) return Delivery.ALREADY_CONFIRMED;
        boolean confirmedAtOnce =
                accounts.findById(manager.id()).map(Account::emailVerified).orElse(false);
        return confirmedAtOnce ? Delivery.CONFIRMED_WITHOUT_MAIL : Delivery.SENT;
    }

    private String languageOf(StationOverview station, int accountId) {
        String language = Locale.forLanguageTag(station.locale()).getLanguage();
        return language.isBlank() ? mailLocales.forAccount(accountId) : language;
    }
}
