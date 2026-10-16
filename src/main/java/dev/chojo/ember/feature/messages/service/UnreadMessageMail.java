/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.UnreadMailDue;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mails people about messages that stayed unread for a while.
 *
 * <p>A message that has waited 15 minutes earns one mail for its conversation, quoting the first
 * lines, and the conversation earns no other until its reader has read it again. Muted
 * conversations, blocked people and readers who turned the mail off are left out. The mail goes to
 * the account's own address only; an account without one is not written to, and nothing is
 * forwarded to the people who look after somebody.
 *
 * <p>This runs on its own sweep rather than through notifications, which address a member of a
 * station, and a conversation belongs to no station.
 */
@Singleton
public class UnreadMessageMail implements TaskSource {
    /** How long a message waits unread before its reader is mailed. */
    public static final Duration WAIT = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(UnreadMessageMail.class);
    private static final Duration SCAN_INTERVAL = Duration.ofSeconds(60);
    private static final int QUOTED = 3;
    private static final int QUOTE_LENGTH = 200;

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final AccountRepository accounts;
    private final MailLocaleService locales;
    private final EmailService email;
    private final Clock clock;

    @Inject
    public UnreadMessageMail(
            MessageRepository messages,
            ConversationRepository conversations,
            AccountRepository accounts,
            MailLocaleService locales,
            EmailService email) {
        this(messages, conversations, accounts, locales, email, Clock.systemUTC());
    }

    /** The same, on a clock a test can move. */
    UnreadMessageMail(
            MessageRepository messages,
            ConversationRepository conversations,
            AccountRepository accounts,
            MailLocaleService locales,
            EmailService email,
            Clock clock) {
        this.messages = messages;
        this.conversations = conversations;
        this.accounts = accounts;
        this.locales = locales;
        this.email = email;
        this.clock = clock;
    }

    /**
     * Mails everybody whose unread messages have waited long enough. A mail that cannot be sent is
     * tried again on the next sweep.
     */
    void sweep() {
        for (UnreadMailDue due : messages.unreadMailDue(clock.instant().minus(WAIT))) {
            try {
                mail(due);
                messages.markMailed(due.conversation().id(), due.accountId());
            } catch (Exception e) {
                log.warn(
                        "Mailing account {} about unread messages in conversation {} failed",
                        due.accountId(),
                        due.conversation().id(),
                        e);
            }
        }
    }

    private void mail(UnreadMailDue due) {
        Account reader = accounts.findById(due.accountId()).orElse(null);
        if (reader == null || !reader.hasRealEmail()) return;
        List<Message> unread = messages.unreadFor(due, QUOTED);
        if (unread.isEmpty()) return;
        Person sender = unread.getFirst().author();
        email.sendUnreadMessages(
                reader.email(),
                NameParts.of(reader).greeting(),
                sender == null ? "?" : nameOf(sender),
                titleOf(due),
                unread.stream()
                        .map(message -> MessageTexts.shortened(MessageTexts.plain(message.content()), QUOTE_LENGTH))
                        .toList(),
                "/messages/" + due.conversation().uid(),
                locales.forAccount(reader.id()));
    }

    /**
     * What a group is called in the mail: its name, or the first names of the others in it. A
     * direct conversation is named by its sender alone, so it gets no title.
     */
    private String titleOf(UnreadMailDue due) {
        if (!due.conversation().isGroup()) return "";
        String name = due.conversation().name();
        if (name != null) return name;
        return conversations.activeMembers(due.conversation().id()).stream()
                .filter(member -> member.accountId() != due.accountId())
                .map(Membership::person)
                .map(Person::firstName)
                .collect(Collectors.joining(", "));
    }

    private static String nameOf(Person person) {
        return (person.firstName() + " " + person.lastName()).strip();
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "unread-message-mail", Schedule.fixedDelay(SCAN_INTERVAL, SCAN_INTERVAL), this::sweep));
    }
}
