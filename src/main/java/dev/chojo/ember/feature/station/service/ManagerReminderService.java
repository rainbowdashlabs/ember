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
