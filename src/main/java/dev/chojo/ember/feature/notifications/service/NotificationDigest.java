/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.notifications.entity.DigestItem;
import dev.chojo.ember.feature.notifications.entity.DigestMail;
import dev.chojo.ember.feature.notifications.entity.LinkHome;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationScheduleRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mails people what gathered for them, station members and cluster members through one loop.
 *
 * <p>The sweep looks in far more often than anybody is written to. A station or a cluster says the
 * times of day it wants its mail, read on its own clock, and is written to only where one of those
 * times has gone by since it was last; one whose moment has not come keeps its notifications,
 * unmailed and unmarked, for the next look. Everything waiting is read in one statement with who it
 * is for and whether they want mail, the groups in a second and the accounts in a third, so a sweep
 * costs the same whatever the number of people.
 *
 * <p>A notification of a group that was due is marked as mailed whether or not a mail went out: a
 * reader who wants no mail, or whose mail could not be sent, is not a reason to try the same
 * notifications again on every sweep for ever. One reader whose mail fails does not stop the others.
 *
 * <p>Read notifications are pruned from here too, also where the digest is switched off, since the
 * table otherwise only ever grows.
 */
@Singleton
public class NotificationDigest implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(NotificationDigest.class);

    /**
     * How often the sweep looks in, which is not how often anybody is written to. Fifteen minutes is
     * fine enough for an hour's resolution and rare enough to cost nothing where nothing is waiting.
     */
    private static final long TICK_MINUTES = 15;

    /** How long read notifications are left alone between two prunings. */
    private static final Duration PRUNE_INTERVAL = Duration.ofDays(1);

    private static final String TEMPLATE = "notification-digest.html";
    private final NotificationRepository notificationRepository;
    private final NotificationScheduleRepository scheduleRepository;
    private final AccountRepository accountRepository;
    private final MailRecipientService mailRecipientService;
    private final EmailService emailService;
    private final StationLogoService logoService;
    private final NotificationText text;

    /** The shortest gap the operator allows between two mails to the same group. */
    private final Duration floor;

    /** Whether the sweep writes mail at all; pruning goes on either way. */
    private final boolean enabled;

    private final Duration sweepInterval;

    /** When read notifications were last pruned, {@code null} before the first time since start. */
    private Instant lastPrunedAt;

    @Inject
    public NotificationDigest(
            NotificationRepository notificationRepository,
            NotificationScheduleRepository scheduleRepository,
            AccountRepository accountRepository,
            MailRecipientService mailRecipientService,
            EmailService emailService,
            StationLogoService logoService,
            NotificationText text,
            Mailing mailing) {
        this.notificationRepository = notificationRepository;
        this.scheduleRepository = scheduleRepository;
        this.accountRepository = accountRepository;
        this.mailRecipientService = mailRecipientService;
        this.emailService = emailService;
        this.logoService = logoService;
        this.text = text;
        int intervalMinutes = mailing.notificationDigestIntervalMinutes();
        this.floor = Duration.ofMinutes(Math.max(intervalMinutes, 0));
        this.enabled = intervalMinutes > 0;
        this.sweepInterval = sweepInterval(mailing);
        if (enabled) {
            log.info(
                    "Notification digest looks in every {} minutes, no station written to more often than every {}",
                    sweepInterval.toMinutes(),
                    intervalMinutes);
        } else {
            log.info("Notification digest disabled (interval=0)");
        }
    }

    /**
     * How often the sweep looks in: every fifteen minutes, or as often as the digest interval when
     * that is shorter.
     *
     * @param mailing the operator's mail settings
     * @return the time between two sweeps
     */
    static Duration sweepInterval(Mailing mailing) {
        int intervalMinutes = mailing.notificationDigestIntervalMinutes();
        long tick = intervalMinutes > 0 ? Math.min(intervalMinutes, TICK_MINUTES) : TICK_MINUTES;
        return Duration.ofMinutes(tick);
    }

    /**
     * One look in: mail every group whose moment has come, then prune if a day has passed.
     *
     * <p>Never lets an exception escape, because the scheduler would stop running a task that threw.
     *
     * @param now the moment of this sweep
     */
    void sweep(Instant now) {
        if (enabled) sendDue(now);
        pruneAcknowledgedIfDue(now);
    }

    /**
     * Prunes old read notifications unless that already happened within the last day.
     *
     * <p>A failure is logged and not remembered, so the next sweep tries again.
     *
     * @param now the moment of this sweep
     */
    void pruneAcknowledgedIfDue(Instant now) {
        if (lastPrunedAt != null && now.isBefore(lastPrunedAt.plus(PRUNE_INTERVAL))) return;
        try {
            notificationRepository.deleteOldAcknowledged();
            lastPrunedAt = now;
            log.info("Notification cleanup removed the acknowledged entries older than 30 days");
        } catch (RuntimeException e) {
            log.warn("Pruning read notifications failed, trying again on the next sweep", e);
        }
    }

    /**
     * Mails every group that is due and marks what it held.
     *
     * @param now the moment being judged
     */
    void sendDue(Instant now) {
        try {
            var waiting = notificationRepository.findWaitingForDigest();
            if (waiting.isEmpty()) return;
            var byGroup = waiting.stream()
                    .collect(Collectors.groupingBy(DigestItem::group, LinkedHashMap::new, Collectors.toList()));
            var due = dueGroups(byGroup, now);
            if (due.isEmpty()) return;

            var accounts = accountsOf(due, byGroup);
            var mailed = new ArrayList<Integer>();
            for (var group : due) {
                var items = byGroup.get(group.key());
                send(group, items, accounts);
                items.forEach(item -> mailed.add(item.notification().id()));
            }
            notificationRepository.markEmailed(mailed);
            for (var group : due) {
                switch (group.key().kind()) {
                    case STATION ->
                        scheduleRepository.markStationSent(group.key().id(), now);
                    case CLUSTER ->
                        scheduleRepository.markClusterSent(group.key().id(), now);
                }
            }
            log.info("Processed notification digest: {} notifications across {} groups", mailed.size(), due.size());
        } catch (RuntimeException e) {
            log.error("Error processing notification digest", e);
        }
    }

    private List<DigestGroup> dueGroups(Map<DigestGroup.Key, List<DigestItem>> byGroup, Instant now) {
        var groups = scheduleRepository.findDigestGroups(
                idsOf(byGroup.keySet(), DigestGroup.Kind.STATION), idsOf(byGroup.keySet(), DigestGroup.Kind.CLUSTER));
        return groups.stream()
                .filter(group -> group.isDue(oldest(byGroup.get(group.key())), floor, now))
                .toList();
    }

    private static List<Integer> idsOf(Collection<DigestGroup.Key> keys, DigestGroup.Kind kind) {
        return keys.stream()
                .filter(key -> key.kind() == kind)
                .map(DigestGroup.Key::id)
                .toList();
    }

    private static Instant oldest(List<DigestItem> items) {
        return items.stream()
                .map(item -> item.notification().createdAt())
                .min(Instant::compareTo)
                .orElse(null);
    }

    private Map<Integer, Account> accountsOf(List<DigestGroup> due, Map<DigestGroup.Key, List<DigestItem>> byGroup) {
        var ids = due.stream()
                .flatMap(group -> byGroup.get(group.key()).stream())
                .filter(DigestItem::mailWanted)
                .map(DigestItem::accountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return accountRepository.findByIds(ids).stream().collect(Collectors.toMap(Account::id, Function.identity()));
    }

    /**
     * The mails of one group: one per address that receives any of what is waiting by mail, whether
     * for its owner or on behalf of the members it looks after.
     */
    private void send(DigestGroup group, List<DigestItem> items, Map<Integer, Account> accounts) {
        if (!canSend(group)) return;
        for (var mail : DigestAddresses.gather(
                items, accounts, mailRecipientService::forAccount, NotificationDigest::firstNameOf)) {
            try {
                sendTo(group, mail);
            } catch (RuntimeException e) {
                log.warn("Failed to send the digest of {} to one address", group.key(), e);
            }
        }
    }

    private boolean canSend(DigestGroup group) {
        return switch (group.key().kind()) {
            case STATION -> emailService.canStationSend(group.key().id());
            case CLUSTER -> emailService.canInstanceSend();
        };
    }

    private void sendTo(DigestGroup group, DigestMail mail) {
        String locale = text.resolveLocale(group.locale());
        String baseUrl = emailService.getBaseUrl();
        String count = String.valueOf(mail.entries().size());
        var items = new StringBuilder();
        for (var entry : mail.entries()) {
            items.append(itemHtml(entry, locale, baseUrl, group.linkHome()));
        }

        var vars = new HashMap<String, String>();
        vars.put("name", greetingOf(mail));
        vars.put("baseUrl", baseUrl);
        vars.put("stationName", group.name());
        vars.put("count", count);
        vars.put("items", items.toString());
        vars.put("actionUrl", NotificationText.landingUrl(baseUrl, group.linkHome()));
        vars.put("preferencesUrl", NotificationText.preferencesUrl(baseUrl, group.linkHome()));
        vars.put("logoHtml", logoHtml(group, baseUrl));

        String subject = text.resolveLocalized(
                locale,
                "digest",
                mail.entries().size() == 1 ? "subject.one" : "subject.other",
                Map.of("stationName", group.name(), "count", count));
        String body = emailService.loadTemplate(TEMPLATE, locale, vars);
        switch (group.key().kind()) {
            case STATION -> emailService.queueStationEmail(group.key().id(), mail.address(), subject, body);
            case CLUSTER -> emailService.queueInstanceEmail(mail.address(), subject, body);
        }
    }

    /**
     * Who the mail greets: the owner of the address, never a member it receives on behalf of. Where
     * the owner reads nothing of their own in this mail, their account is found by the address.
     */
    private String greetingOf(DigestMail mail) {
        if (mail.owner() != null) return nameOf(mail.owner());
        return accountRepository
                .findByEmail(mail.address())
                .map(NotificationDigest::nameOf)
                .orElse("");
    }

    /** How a child is named on an item in their guardian's mail: as the family calls them. */
    private static String firstNameOf(Account account) {
        String name = NameParts.of(account).greeting();
        return name == null || name.isEmpty() ? nameOf(account) : name;
    }

    private static String nameOf(Account account) {
        String name = NameParts.of(account).called();
        return name == null || name.isEmpty() ? account.loginName() : name;
    }

    private String logoHtml(DigestGroup group, String baseUrl) {
        if (group.key().kind() != DigestGroup.Kind.STATION) return "";
        return logoService
                .mailAddress(baseUrl, group.key().id())
                .map(address -> "<img src=\"" + address + "\" alt=\"\" style=\"height:40px;border-radius:4px\">")
                .orElse("");
    }

    /**
     * One notification as it reads in a digest mail, its link carrying the station or the cluster
     * the mail is about, and naming the members it reaches the address for where it is not for the
     * owner of the address.
     */
    private String itemHtml(DigestMail.Entry entry, String locale, String baseUrl, LinkHome home) {
        var notification = entry.notification();
        String itemUrl = text.resolveNotificationUrl(baseUrl, home, notification.data());
        var item = new StringBuilder("<li class=\"notification-item\">");
        if (itemUrl != null) {
            item.append("<a href=\"").append(itemUrl).append("\" style=\"text-decoration:none;color:inherit\">");
        }
        item.append("<span class=\"category\">")
                .append(text.resolveCategory(locale, notification.type()))
                .append("</span>")
                .append("<p class=\"message\">")
                .append(text.resolveMessage(locale, notification))
                .append("</p>");
        String detail = text.resolveDetail(notification);
        if (detail != null) {
            item.append("<p class=\"detail\">").append(detail).append("</p>");
        }
        if (!entry.forMembers().isEmpty()) {
            String names = entry.forMembers().stream()
                    .map(NotificationDigest::escapeHtml)
                    .collect(Collectors.joining(", "));
            item.append("<p class=\"for-members\" style=\"color:#666;font-size:12px;margin:4px 0 0\">")
                    .append(text.resolveLocalized(locale, "digest", "forMembers", Map.of("names", names)))
                    .append("</p>");
        }
        if (itemUrl != null) {
            item.append("</a>");
        }
        return item.append("</li>").toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "notification-sweep", Schedule.fixedDelay(sweepInterval, sweepInterval), () -> sweep(Instant.now())));
    }
}
