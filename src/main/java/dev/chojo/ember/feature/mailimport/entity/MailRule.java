/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What a mailbox does with the mail it finds.
 *
 * <p>Whether this rule takes a message is decided by the message alone: the senders it trusts, the
 * subject filter and the attachment name filter. The type and size bounds are not part of that,
 * because they judge each attachment separately and one message can carry both a PDF worth having and
 * a signature logo worth dropping.
 *
 * <p>The consequence is deliberate. A rule that takes a message and then refuses every attachment in
 * it has still taken the message, and the next rule does not get a turn. A station wanting the same
 * sender's PDFs and photographs filed differently separates them by attachment name, not by hoping a
 * second rule picks up the leftovers.
 *
 * @param id                   the rule identifier
 * @param mailboxId            the mailbox it belongs to
 * @param name                 what the station calls it, so the log can name the rule that took a message
 * @param position             where it stands in the order, since the first match wins
 * @param enabled              whether it takes part at all
 * @param subjectFilter        text the subject must contain, or null for any
 * @param attachmentNameFilter text an attachment name must contain, or null for any
 * @param acceptedTypes        the content types it files, decided by sniffing the bytes
 * @param minSizeBytes         how big a file has to be to count as a document
 * @param includeInline        whether embedded pictures count as files
 * @param titleSource          where the document title comes from
 * @param hidden               whether what it files is kept from the members it is bound to
 * @param keepOnArchive        whether what it files survives its members being marked former
 * @param readSubjectForMember whether a name in the subject binds the document to that member
 * @param action               what becomes of the message afterwards
 * @param moveToFolder         where {@link MailRuleAction#MOVE} puts it, null for every other action
 * @param senderPatterns       the addresses and domains it trusts. Empty matches nothing, on purpose
 * @param tags                 the words it puts on what it files
 * @param createdAt            when it was written
 */
public record MailRule(
        int id,
        int mailboxId,
        String name,
        int position,
        boolean enabled,
        String subjectFilter,
        String attachmentNameFilter,
        List<String> acceptedTypes,
        long minSizeBytes,
        boolean includeInline,
        MailTitleSource titleSource,
        boolean hidden,
        boolean keepOnArchive,
        boolean readSubjectForMember,
        MailRuleAction action,
        String moveToFolder,
        List<String> senderPatterns,
        List<String> tags,
        Instant createdAt) {

    /**
     * Reads the rule's own row. The two lists hang off other tables and are filled in by the repository,
     * so a row on its own maps to a rule that trusts nobody until they are.
     */
    public static RowMapping<MailRule> map() {
        return row -> new MailRule(
                row.getInt("id"),
                row.getInt("mailbox_id"),
                row.getString("name"),
                row.getInt("position"),
                row.getBoolean("enabled"),
                row.getString("subject_filter"),
                row.getString("attachment_name_filter"),
                List.of((String[]) row.getArray("accepted_types").getArray()),
                row.getLong("min_size_bytes"),
                row.getBoolean("include_inline"),
                MailTitleSource.valueOf(row.getString("title_source")),
                row.getBoolean("hidden"),
                row.getBoolean("keep_on_archive"),
                row.getBoolean("read_subject_for_member"),
                MailRuleAction.valueOf(row.getString("action")),
                row.getString("move_to_folder"),
                List.of(),
                List.of(),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    /** The same rule with the lists that hang off other tables filled in. */
    public MailRule with(List<String> senderPatterns, List<String> tags) {
        return new MailRule(
                id,
                mailboxId,
                name,
                position,
                enabled,
                subjectFilter,
                attachmentNameFilter,
                acceptedTypes,
                minSizeBytes,
                includeInline,
                titleSource,
                hidden,
                keepOnArchive,
                readSubjectForMember,
                action,
                moveToFolder,
                senderPatterns,
                tags,
                createdAt);
    }
}
