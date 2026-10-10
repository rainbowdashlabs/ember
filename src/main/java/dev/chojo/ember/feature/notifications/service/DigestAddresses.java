/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.MailRecipientService.Recipient;
import dev.chojo.ember.feature.notifications.entity.DigestItem;
import dev.chojo.ember.feature.notifications.entity.DigestMail;
import dev.chojo.ember.feature.notifications.entity.Notification;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The due notifications of one station or association, gathered by the address they go to.
 *
 * <p>A member without an address of their own is mailed through their guardians, and a guardian is a
 * reader of their own as well. Gathered by reader, a guardian with two children received news for the
 * whole station three times. Gathered by address, they receive one mail, which lists a notification
 * once however many readers it reached them through, and names the children on an item that reaches
 * them only on a child's behalf.
 *
 * <p>Two notifications count as one when their type and their data are equal, which is what a
 * notification written for several readers at once looks like.
 */
final class DigestAddresses {
    private static final Comparator<Notification> OLDEST_FIRST =
            Comparator.comparing(Notification::createdAt).thenComparingInt(Notification::id);

    private DigestAddresses() {}

    /**
     * One mail per address, from the items whose readers want them by mail.
     *
     * @param items        the due items of one station or association
     * @param accounts     the accounts behind the readers, by id
     * @param recipientsOf who a mail about an account goes to
     * @param nameOf       how a member is named on an item reaching an address on their behalf
     * @return the mails, in the order their addresses were first reached
     */
    static List<DigestMail> gather(
            List<DigestItem> items,
            Map<Integer, Account> accounts,
            Function<Account, List<Recipient>> recipientsOf,
            Function<Account, String> nameOf) {
        var byAddress = new LinkedHashMap<String, MailDraft>();
        var byReader = items.stream()
                .filter(DigestItem::mailWanted)
                .collect(Collectors.groupingBy(DigestItem::recipientId, LinkedHashMap::new, Collectors.toList()));
        for (var reader : byReader.values()) {
            var accountId = reader.getFirst().accountId();
            var account = accountId == null ? null : accounts.get(accountId);
            if (account == null) continue;
            for (var recipient : recipientsOf.apply(account)) {
                var draft = byAddress.computeIfAbsent(
                        recipient.email().toLowerCase(Locale.ROOT), key -> new MailDraft(recipient.email()));
                if (!recipient.guardian()) draft.owner = account;
                String onBehalfOf = recipient.guardian() ? nameOf.apply(account) : null;
                reader.forEach(item -> draft.add(item.notification(), onBehalfOf));
            }
        }
        return byAddress.values().stream().map(MailDraft::mail).toList();
    }

    private static final class MailDraft {
        private final String address;
        private final Map<String, EntryDraft> entries = new LinkedHashMap<>();
        private @Nullable Account owner;

        private MailDraft(String address) {
            this.address = address;
        }

        private void add(Notification notification, @Nullable String onBehalfOf) {
            var entry = entries.computeIfAbsent(
                    notification.type() + ":" + notification.data().toJson(), key -> new EntryDraft(notification));
            if (onBehalfOf == null) {
                entry.own = true;
            } else {
                entry.forMembers.add(onBehalfOf);
            }
        }

        private DigestMail mail() {
            var drafts = new ArrayList<>(entries.values());
            drafts.sort(Comparator.comparing(draft -> draft.notification, OLDEST_FIRST));
            return new DigestMail(
                    address, owner, drafts.stream().map(EntryDraft::entry).toList());
        }
    }

    private static final class EntryDraft {
        private final Notification notification;
        private final Set<String> forMembers = new LinkedHashSet<>();
        private boolean own;

        private EntryDraft(Notification notification) {
            this.notification = notification;
        }

        private DigestMail.Entry entry() {
            return new DigestMail.Entry(notification, own ? List.of() : List.copyOf(forMembers));
        }
    }
}
