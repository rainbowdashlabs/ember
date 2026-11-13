/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.conf.file.elements.Messages;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.DigestMessage;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.StationRef;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The daily mail about messages people have not read yet.
 *
 * <p>Once a day, at the hour the operator sets ({@code messages.digestHour}, on the instance's
 * clock), every account that wants it is sent one mail covering what is new and unread since its
 * last one: every message of its direct conversations and groups, and from station chats the
 * messages that mention it, or all of them where it chose so. Muted conversations, blocked writers,
 * its own messages and system lines are left out, and nobody is mailed when nothing is new. The mail
 * goes to the account's own address only.
 *
 * <p>The sweep looks in every quarter hour and runs once the hour has come, remembering the day it
 * last ran, so a restart neither skips a day nor sends twice. The hour is read in the server's time
 * zone. An account without an address of its own is not written to. Notifications are not used: they
 * address a member of a station and run on each station's mail times, while messages belong to
 * accounts.
 */
@Singleton
public class MessageDigestMail implements TaskSource {
    /** The application setting that remembers when the daily mail last went out. */
    static final String LAST_RUN = "messages.digest.lastRun";

    /** How many of a conversation's newest messages a section quotes. */
    static final int QUOTED = 3;

    private static final Logger log = LoggerFactory.getLogger(MessageDigestMail.class);
    private static final Duration TICK = Duration.ofMinutes(15);

    /**
     * How far before the run the accounts it did not mail are marked, so a message whose writing
     * began before the run but was committed during it is not passed over.
     */
    private static final Duration MARK_MARGIN = Duration.ofMinutes(1);

    private static final int QUOTE_LENGTH = 200;

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final StationChatRepository stationChats;
    private final AccountRepository accounts;
    private final ApplicationSettingRepository settings;
    private final MailLocaleService locales;
    private final EmailService email;
    private final int hour;
    private final ZoneId zone;
    private final Clock clock;

    @Inject
    public MessageDigestMail(
            MessageRepository messages,
            ConversationRepository conversations,
            StationChatRepository stationChats,
            AccountRepository accounts,
            ApplicationSettingRepository settings,
            MailLocaleService locales,
            EmailService email,
            Messages config) {
        this(
                messages,
                conversations,
                stationChats,
                accounts,
                settings,
                locales,
                email,
                config.digestHour(),
                ZoneId.systemDefault(),
                Clock.systemUTC());
    }

    /** The same, at an hour, in a zone and on a clock a test chooses. */
    MessageDigestMail(
            MessageRepository messages,
            ConversationRepository conversations,
            StationChatRepository stationChats,
            AccountRepository accounts,
            ApplicationSettingRepository settings,
            MailLocaleService locales,
            EmailService email,
            int hour,
            ZoneId zone,
            Clock clock) {
        this.messages = messages;
        this.conversations = conversations;
        this.stationChats = stationChats;
        this.accounts = accounts;
        this.settings = settings;
        this.locales = locales;
        this.email = email;
        this.hour = hour;
        this.zone = zone;
        this.clock = clock;
    }

    /**
     * Sends the daily mail once its hour has come today and it has not gone out today yet. The day
     * is compared, not the hour, so moving the hour later never sends a second mail on one day.
     * Afterwards every account that wants the mail starts its next one from this run, so what it
     * left unread and nobody mailed is not looked at again every day.
     */
    void sweep() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant due = today.atTime(hour, 0).atZone(zone).toInstant();
        if (now.isBefore(due) || !LocalDate.ofInstant(lastRun(), zone).isBefore(today)) return;
        List<Integer> readers = messages.digestReaders();
        int sent = 0;
        for (int accountId : readers) {
            try {
                if (mail(accountId, now)) sent++;
            } catch (RuntimeException e) {
                log.warn("Sending the daily message mail to account {} failed", accountId, e);
            }
        }
        messages.markDigestedUpTo(now.minus(MARK_MARGIN), readers);
        settings.set(LAST_RUN, now.toString());
        log.info("Daily message mail sent to {} account(s)", sent);
    }

    private Instant lastRun() {
        return settings.get(LAST_RUN).map(Instant::parse).orElse(Instant.EPOCH);
    }

    private boolean mail(int accountId, Instant now) {
        Account reader = accounts.findById(accountId).orElse(null);
        if (reader == null) return false;
        if (!reader.hasRealEmail()) {
            messages.markDigestSent(accountId, now);
            return false;
        }
        Map<Integer, List<DigestMessage>> byConversation = messages.digestOf(accountId, QUOTED).stream()
                .collect(Collectors.groupingBy(
                        quoted -> quoted.message().conversationId(), LinkedHashMap::new, Collectors.toList()));
        if (byConversation.isEmpty()) return false;
        List<Conversation> found = conversations.findAll(byConversation.keySet());
        Map<Integer, StationRef> stations = stationChats.stations(found.stream()
                .map(Conversation::station)
                .filter(Objects::nonNull)
                .map(StationScope::stationId)
                .collect(Collectors.toSet()));
        Map<Integer, List<Membership>> members = conversations
                .activeMembers(found.stream()
                        .filter(conversation -> !conversation.isStation())
                        .map(Conversation::id)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(Membership::conversationId));
        String baseUrl = email.getBaseUrl();
        String sections = found.stream()
                .sorted((left, right) -> right.lastActivityAt().compareTo(left.lastActivityAt()))
                .map(conversation -> section(
                        conversation,
                        byConversation.get(conversation.id()),
                        titleOf(conversation, accountId, stations, members.getOrDefault(conversation.id(), List.of())),
                        baseUrl))
                .collect(Collectors.joining());
        int count = byConversation.values().stream()
                .mapToInt(quoted -> quoted.getFirst().waiting())
                .sum();
        email.sendMessageDigest(
                reader.email(), NameParts.of(reader).greeting(), sections, count, locales.forAccount(reader.id()));
        Instant newest = byConversation.values().stream()
                .flatMap(List::stream)
                .map(quoted -> quoted.message().createdAt())
                .max(Instant::compareTo)
                .orElse(now);
        messages.markDigestSent(accountId, newest);
        return true;
    }

    /**
     * One conversation of the mail: its title with how many messages wait there, linking to it,
     * and its newest messages, each with who wrote it.
     */
    private static String section(Conversation conversation, List<DigestMessage> quoted, String title, String baseUrl) {
        var html = new StringBuilder("<div style=\"margin:16px 0\">")
                .append("<p style=\"margin:0 0 4px\"><a href=\"")
                .append(baseUrl)
                .append("/messages/")
                .append(conversation.uid())
                .append("\"><strong>")
                .append(escape(title))
                .append("</strong></a> (")
                .append(quoted.getFirst().waiting())
                .append(")</p>");
        quoted.stream().map(DigestMessage::message).forEach(message -> html.append(
                        "<p style=\"margin:0 0 4px;border-left:3px solid #FF6421;padding-left:12px;white-space:pre-line\">")
                .append(escape(nameOf(message.author())))
                .append(": ")
                .append(escape(MessageTexts.shortened(MessageTexts.plain(message.content()), QUOTE_LENGTH)))
                .append("</p>"));
        return html.append("</div>").toString();
    }

    /**
     * What a conversation is called in the mail: a station chat by its name and station, a group by
     * its name or the first names of the others, a direct conversation by the other person. Where
     * nobody else takes part any more, the people who ever did name it.
     */
    private String titleOf(
            Conversation conversation, int readerId, Map<Integer, StationRef> stations, List<Membership> members) {
        StationScope scope = conversation.station();
        String name = conversation.name();
        if (scope != null) {
            StationRef station = stations.get(scope.stationId());
            String chat = Objects.requireNonNullElse(name, "");
            return station == null ? chat : chat + " (" + station.name() + ")";
        }
        if (name != null) return name;
        List<Person> others = othersIn(members, readerId);
        if (others.isEmpty()) others = othersIn(conversations.everyMember(conversation.id()), readerId);
        if (others.isEmpty()) return "?";
        if (conversation.isDirect()) return nameOf(others.getFirst());
        return others.stream().map(Person::firstName).collect(Collectors.joining(", "));
    }

    private static List<Person> othersIn(List<Membership> members, int readerId) {
        return members.stream()
                .filter(member -> member.accountId() != readerId)
                .map(Membership::person)
                .toList();
    }

    private static String nameOf(@Nullable Person person) {
        if (person == null) return "?";
        return (person.firstName() + " " + person.lastName()).strip();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask("message-digest-mail", Schedule.fixedDelay(TICK, TICK), this::sweep));
    }
}
