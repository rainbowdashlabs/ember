/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ManagedLoginNoticeRepository;
import dev.chojo.ember.feature.members.repository.ManagedLoginNoticeRepository.PendingNotice;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Telling a member that the guardian who looks after them switched signing in on or off.
 *
 * <p>The mail waits a few minutes rather than leaving with the switch. A guardian who flicks the
 * toggle by accident and flicks it straight back should reach nobody at all, and that can only be
 * known once the waiting time has passed. What waits is the state to announce; which mail says it is
 * decided here, from the account as it stands when the wait is over.
 *
 * <p>An account that has never been claimed is sent the password-setup mail instead of a notice,
 * because being allowed to sign in is of no use to somebody who has no password yet. The same
 * account being switched off again is sent nothing: it would name the loss of something the member
 * never had.
 */
@Singleton
public class ManagedLoginNoticeService {
    private static final Logger log = LoggerFactory.getLogger(ManagedLoginNoticeService.class);

    private final ManagedLoginNoticeRepository noticeRepository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final MailLocaleService mailLocaleService;
    private final MailRecipientService mailRecipientService;
    private final AuthService authService;
    private final EmailService emailService;
    private final Auth authConfig;

    @Inject
    public ManagedLoginNoticeService(
            ManagedLoginNoticeRepository noticeRepository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            MailLocaleService mailLocaleService,
            MailRecipientService mailRecipientService,
            AuthService authService,
            EmailService emailService,
            Auth authConfig) {
        this.noticeRepository = noticeRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.mailLocaleService = mailLocaleService;
        this.mailRecipientService = mailRecipientService;
        this.authService = authService;
        this.emailService = emailService;
        this.authConfig = authConfig;
    }

    /**
     * Notes that signing in was switched, so the member is told once the waiting time has passed.
     *
     * <p>A member has at most one change waiting. Switching back to what was true before the wait
     * started drops it and sends nothing, which is what makes an accidental toggle silent.
     *
     * @param memberId the member the change was made for
     * @param granted  whether signing in was switched on, rather than taken away
     */
    public void record(int memberId, boolean granted) {
        var waiting = noticeRepository.find(memberId);
        if (waiting.isPresent()) {
            if (waiting.get().granted() != granted) {
                noticeRepository.cancel(memberId);
                log.info("Access change of managed member {} was undone before it was announced", memberId);
            }
            return;
        }
        noticeRepository.schedule(
                memberId, granted, Instant.now().plus(authConfig.managedLoginNoticeMinutes(), ChronoUnit.MINUTES));
    }

    /**
     * Sends everything whose waiting time has passed. A send that fails leaves the change waiting,
     * so the next sweep tries it again.
     */
    public void dispatch() {
        for (PendingNotice notice : noticeRepository.findDue(Instant.now())) {
            try {
                announce(notice.memberId());
                noticeRepository.cancel(notice.memberId());
            } catch (Exception e) {
                log.warn("Announcing the access change of managed member {} failed", notice.memberId(), e);
            }
        }
    }

    /**
     * Says what is true of the member now, rather than what was true when the switch was flicked:
     * the waiting time is long enough for somebody else to have changed it again.
     */
    private void announce(int memberId) {
        StationMember member = memberRepository.findById(memberId).orElse(null);
        if (member == null || member.accountId() == null) return;
        Account account = accountRepository.findById(member.accountId()).orElse(null);
        if (account == null) return;

        var recipients = mailRecipientService.forAccount(account.id());
        if (recipients.isEmpty()) return;

        boolean granted = memberRepository.hasPermission(memberId, StationPermission.LOGIN);
        boolean claimed = accountRepository.findCredential(account.id()).isPresent();
        if (granted && !claimed) {
            authService.sendPasswordSetup(account.id());
            return;
        }
        if (!claimed) return;

        String stationName = stationRepository
                .findById(member.stationId())
                .map(Station::name)
                .orElse("");
        String locale = mailLocaleService.forAccount(account.id());
        for (var recipient : recipients) {
            if (granted) {
                emailService.sendManagedLoginGrantedNotice(recipient.email(), recipient.name(), stationName, locale);
            } else {
                emailService.sendManagedLoginRevokedNotice(recipient.email(), recipient.name(), stationName, locale);
            }
        }
    }
}
