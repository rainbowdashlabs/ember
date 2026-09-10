/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailRule;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which rule takes a message.
 *
 * <p>Rules are ordered and the first one that matches takes it; the others do not run. What matching
 * means is fixed here and nowhere else, because a log saying which rule took a message is unanswerable
 * unless the answer is one thing.
 *
 * <p>A rule matches on the message: the senders it trusts, its subject filter, and whether any
 * attachment name fits its name filter. The type and size bounds are deliberately not part of it, since
 * they judge each attachment on its own and one message can carry both a PDF worth having and a
 * signature logo worth dropping.
 *
 * <p>The consequence is deliberate. A rule that takes a message and then refuses every attachment in it
 * has still taken the message, and the next rule does not get a turn. A station that wants one sender's
 * PDFs and photographs filed differently separates them by attachment name rather than hoping a second
 * rule picks up the leftovers.
 */
public final class RuleSelection {

    private RuleSelection() {}

    /**
     * The rule that takes this message, or nothing where none does.
     *
     * @param rules           the mailbox's rules, in any order: the position decides, not the list
     * @param sender          the address the mail came from, reduced to the bare address
     * @param subject         what the message was called, which may be empty
     * @param attachmentNames the names of the files hanging off it
     * @return the rule that takes it
     */
    public static Optional<MailRule> firstMatch(
            List<MailRule> rules, String sender, String subject, List<String> attachmentNames) {
        if (rules == null) return Optional.empty();
        return rules.stream()
                .filter(MailRule::enabled)
                .sorted((left, right) -> {
                    int byPosition = Integer.compare(left.position(), right.position());
                    return byPosition != 0 ? byPosition : Integer.compare(left.id(), right.id());
                })
                .filter(rule -> takes(rule, sender, subject, attachmentNames))
                .findFirst();
    }

    /**
     * Whether this one rule takes the message.
     *
     * @param rule            the rule being asked
     * @param sender          the address the mail came from
     * @param subject         what the message was called
     * @param attachmentNames the names of the files hanging off it
     * @return whether it takes it
     */
    public static boolean takes(MailRule rule, String sender, String subject, List<String> attachmentNames) {
        if (!SenderPatterns.accepts(rule.senderPatterns(), sender)) return false;
        if (!contains(subject, rule.subjectFilter())) return false;
        return anyNameFits(attachmentNames, rule.attachmentNameFilter());
    }

    private static boolean anyNameFits(List<String> attachmentNames, String filter) {
        if (filter == null || filter.isBlank()) return true;
        if (attachmentNames == null || attachmentNames.isEmpty()) return false;
        return attachmentNames.stream().anyMatch(name -> contains(name, filter));
    }

    private static boolean contains(String text, String needle) {
        if (needle == null || needle.isBlank()) return true;
        if (text == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(needle.trim().toLowerCase(Locale.ROOT));
    }
}
