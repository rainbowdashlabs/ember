/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The rules of a mailbox, together with the three lists that hang off each one.
 *
 * <p>A rule is never handed out without its sender patterns. Reading them separately and forgetting to
 * would produce a rule that trusts nobody, which fails closed and is therefore safe, but it would also
 * silently stop a station's import, so they are filled in here rather than left to the caller.
 */
@Singleton
public class MailRuleRepository {

    private static final String COLUMNS = """
            id, mailbox_id, name, position, enabled, subject_filter, attachment_name_filter, accepted_types,
            min_size_bytes, include_inline, title_source, hidden, keep_on_archive, read_subject_for_member,
            action, move_to_folder, lost_member, created_at""";

    /**
     * Writes a rule and the three lists belonging to it.
     *
     * @return the rule as it was written, its lists included
     */
    public MailRule create(
            int mailboxId,
            String name,
            int position,
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
            List<Integer> memberIds) {
        var rule = query("""
                        INSERT INTO mail_rule(mailbox_id, name, position, subject_filter, attachment_name_filter,
                                              accepted_types, min_size_bytes, include_inline, title_source, hidden,
                                              keep_on_archive, read_subject_for_member, action, move_to_folder)
                        VALUES (:mailbox_id, :name, :position, :subject_filter, :attachment_name_filter,
                                :accepted_types, :min_size_bytes, :include_inline, :title_source, :hidden,
                                :keep_on_archive, :read_subject_for_member, :action, :move_to_folder)
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("mailbox_id", mailboxId)
                        .bind("name", name)
                        .bind("position", position)
                        .bind("subject_filter", subjectFilter)
                        .bind("attachment_name_filter", attachmentNameFilter)
                        .bind("accepted_types", acceptedTypes, PostgreSqlTypes.TEXT)
                        .bind("min_size_bytes", minSizeBytes)
                        .bind("include_inline", includeInline)
                        .bind("title_source", titleSource.name())
                        .bind("hidden", hidden)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("read_subject_for_member", readSubjectForMember)
                        .bind("action", action.name())
                        .bind("move_to_folder", moveToFolder))
                .map(MailRule.map())
                .first()
                .orElseThrow();
        setSenderPatterns(rule.id(), senderPatterns);
        setTags(rule.id(), tags);
        setMembers(rule.id(), memberIds);
        return withLists(rule);
    }

    public Optional<MailRule> findById(int id) {
        return query("SELECT %s FROM mail_rule WHERE id = :id;", COLUMNS)
                .single(call().bind("id", id))
                .map(MailRule.map())
                .first()
                .map(this::withLists);
    }

    /** The rules of one mailbox in the order they are applied. */
    public List<MailRule> findByMailbox(int mailboxId) {
        return query("SELECT %s FROM mail_rule WHERE mailbox_id = :mailbox_id ORDER BY position, id;", COLUMNS)
                .single(call().bind("mailbox_id", mailboxId))
                .map(MailRule.map())
                .all()
                .stream()
                .map(this::withLists)
                .toList();
    }

    public boolean update(
            int id,
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
            List<Integer> memberIds) {
        boolean changed = query("""
                        UPDATE mail_rule
                        SET name = :name, position = :position, enabled = :enabled, subject_filter = :subject_filter,
                            attachment_name_filter = :attachment_name_filter, accepted_types = :accepted_types,
                            min_size_bytes = :min_size_bytes, include_inline = :include_inline,
                            title_source = :title_source, hidden = :hidden, keep_on_archive = :keep_on_archive,
                            read_subject_for_member = :read_subject_for_member, action = :action,
                            move_to_folder = :move_to_folder
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("position", position)
                        .bind("enabled", enabled)
                        .bind("subject_filter", subjectFilter)
                        .bind("attachment_name_filter", attachmentNameFilter)
                        .bind("accepted_types", acceptedTypes, PostgreSqlTypes.TEXT)
                        .bind("min_size_bytes", minSizeBytes)
                        .bind("include_inline", includeInline)
                        .bind("title_source", titleSource.name())
                        .bind("hidden", hidden)
                        .bind("keep_on_archive", keepOnArchive)
                        .bind("read_subject_for_member", readSubjectForMember)
                        .bind("action", action.name())
                        .bind("move_to_folder", moveToFolder))
                .update()
                .changed();
        if (!changed) return false;
        setSenderPatterns(id, senderPatterns);
        setTags(id, tags);
        setMembers(id, memberIds);
        return true;
    }

    public boolean delete(int id) {
        return query("DELETE FROM mail_rule WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Says on the rule that it lost a member it could no longer file under.
     *
     * <p>A rule that silently stops working the way it was written is worse than one that says what
     * happened to it, so this is what the page reads to tell somebody their rule changed under them.
     */
    public void markLostMember(int id) {
        query("UPDATE mail_rule SET lost_member = TRUE WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /** Clears that mark once somebody has looked at the rule and decided what it should do now. */
    public boolean clearLostMember(int id) {
        return query("UPDATE mail_rule SET lost_member = FALSE WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Drops a member from every rule that filed under them, marking each one as having lost somebody.
     *
     * <p>The rows would go anyway, since the foreign key cascades. What the cascade cannot do is leave a
     * mark saying it happened, which is the whole difference between a rule that changed and a rule that
     * changed silently.
     */
    public void releaseMember(int memberId) {
        query("""
                        UPDATE mail_rule SET lost_member = TRUE
                        WHERE id IN (SELECT rule_id FROM mail_rule_member WHERE member_id = :member_id);""").single(call().bind("member_id", memberId)).update();
        query("DELETE FROM mail_rule_member WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .update();
    }

    private MailRule withLists(MailRule rule) {
        return rule.with(senderPatterns(rule.id()), tags(rule.id()), members(rule.id()));
    }

    private List<String> senderPatterns(int ruleId) {
        return query("SELECT pattern FROM mail_rule_sender WHERE rule_id = :rule_id ORDER BY pattern;")
                .single(call().bind("rule_id", ruleId))
                .map(row -> row.getString("pattern"))
                .all();
    }

    private List<String> tags(int ruleId) {
        return query("SELECT name FROM mail_rule_tag WHERE rule_id = :rule_id ORDER BY name;")
                .single(call().bind("rule_id", ruleId))
                .map(row -> row.getString("name"))
                .all();
    }

    private List<Integer> members(int ruleId) {
        return query("SELECT member_id FROM mail_rule_member WHERE rule_id = :rule_id ORDER BY member_id;")
                .single(call().bind("rule_id", ruleId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    private void setSenderPatterns(int ruleId, List<String> patterns) {
        query("DELETE FROM mail_rule_sender WHERE rule_id = :rule_id;")
                .single(call().bind("rule_id", ruleId))
                .update();
        for (String pattern : patterns) {
            query("""
                            INSERT INTO mail_rule_sender(rule_id, pattern) VALUES (:rule_id, :pattern)
                            ON CONFLICT DO NOTHING;""")
                    .single(call().bind("rule_id", ruleId).bind("pattern", pattern.trim()))
                    .update();
        }
    }

    private void setTags(int ruleId, List<String> tags) {
        query("DELETE FROM mail_rule_tag WHERE rule_id = :rule_id;")
                .single(call().bind("rule_id", ruleId))
                .update();
        for (String tag : tags) {
            query("INSERT INTO mail_rule_tag(rule_id, name) VALUES (:rule_id, :name) ON CONFLICT DO NOTHING;")
                    .single(call().bind("rule_id", ruleId).bind("name", tag.trim()))
                    .update();
        }
    }

    private void setMembers(int ruleId, List<Integer> memberIds) {
        query("DELETE FROM mail_rule_member WHERE rule_id = :rule_id;")
                .single(call().bind("rule_id", ruleId))
                .update();
        for (int memberId : memberIds) {
            query("""
                            INSERT INTO mail_rule_member(rule_id, member_id) VALUES (:rule_id, :member_id)
                            ON CONFLICT DO NOTHING;""")
                    .single(call().bind("rule_id", ruleId).bind("member_id", memberId))
                    .update();
        }
    }
}
