/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class WaitingListRepository {

    // --- Waiting List CRUD ---

    public List<WaitingList> findAll() {
        return Query.query("SELECT * FROM waiting_list ORDER BY created_at DESC;")
                .single(Call.of())
                .map(WaitingList.map())
                .all();
    }

    public List<WaitingList> findByStation(int stationId) {
        return Query.query("SELECT * FROM waiting_list WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(WaitingList.map())
                .all();
    }

    public Optional<WaitingList> findById(int id) {
        return Query.query("SELECT * FROM waiting_list WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(WaitingList.map())
                .first();
    }

    public WaitingList create(
            int stationId,
            String name,
            String description,
            String scoringFormula,
            int confirmIntervalDays,
            Integer testingGroupId,
            Integer joinGroupId,
            int attendanceThreshold) {
        return Query.query("""
                        INSERT INTO waiting_list (station_id, name, description, scoring_formula, confirm_interval_days,
                            testing_group_id, join_group_id, attendance_threshold)
                        VALUES (:station_id, :name, :description, :scoring_formula, :confirm_interval_days,
                            :testing_group_id, :join_group_id, :attendance_threshold)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("scoring_formula", scoringFormula)
                        .bind("confirm_interval_days", confirmIntervalDays)
                        .bind("testing_group_id", testingGroupId)
                        .bind("join_group_id", joinGroupId)
                        .bind("attendance_threshold", attendanceThreshold))
                .map(WaitingList.map())
                .first()
                .orElseThrow();
    }

    public Optional<WaitingList> update(
            int id,
            String name,
            String description,
            String scoringFormula,
            int confirmIntervalDays,
            Integer testingGroupId,
            Integer joinGroupId,
            int attendanceThreshold) {
        return Query.query("""
                        UPDATE waiting_list SET name = :name, description = :description,
                        scoring_formula = :scoring_formula, confirm_interval_days = :confirm_interval_days,
                        testing_group_id = :testing_group_id, join_group_id = :join_group_id,
                        attendance_threshold = :attendance_threshold
                        WHERE id = :id RETURNING *;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("scoring_formula", scoringFormula)
                        .bind("confirm_interval_days", confirmIntervalDays)
                        .bind("testing_group_id", testingGroupId)
                        .bind("join_group_id", joinGroupId)
                        .bind("attendance_threshold", attendanceThreshold))
                .map(WaitingList.map())
                .first();
    }

    public Optional<WaitingList> updateVisibleFields(int id, String visibleFieldsJson) {
        return Query.query("""
                        UPDATE waiting_list SET visible_fields = :visible_fields::jsonb
                        WHERE id = :id RETURNING *;""")
                .single(Call.of().bind("id", id).bind("visible_fields", visibleFieldsJson))
                .map(WaitingList.map())
                .first();
    }

    public void delete(int id) {
        Query.query("DELETE FROM waiting_list WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete();
    }

    // --- Fields ---

    public List<WaitingListField> findFieldsByList(int listId) {
        return Query.query("SELECT * FROM waiting_list_field WHERE list_id = :list_id ORDER BY position;")
                .single(Call.of().bind("list_id", listId))
                .map(WaitingListField.map())
                .all();
    }

    public WaitingListField createField(
            int listId, String name, String fieldType, WaitingListFieldConfig config, int position, boolean required) {
        return Query.query("""
                        INSERT INTO waiting_list_field (list_id, name, field_type, config, position, required)
                        VALUES (:list_id, :name, :field_type, :config::jsonb, :position, :required)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("list_id", listId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("required", required))
                .map(WaitingListField.map())
                .first()
                .orElseThrow();
    }

    public Optional<WaitingListField> updateField(
            int fieldId, String name, String fieldType, WaitingListFieldConfig config, int position, boolean required) {
        return Query.query("""
                        UPDATE waiting_list_field SET name = :name, field_type = :field_type,
                        config = :config::jsonb, position = :position, required = :required
                        WHERE id = :id RETURNING *;""")
                .single(Call.of()
                        .bind("id", fieldId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("required", required))
                .map(WaitingListField.map())
                .first();
    }

    public void deleteField(int fieldId) {
        Query.query("DELETE FROM waiting_list_field WHERE id = :id;")
                .single(Call.of().bind("id", fieldId))
                .delete();
    }

    // --- Invites ---

    public List<WaitingListInvite> findInvitesByList(int listId) {
        return Query.query("SELECT * FROM waiting_list_invite WHERE list_id = :list_id ORDER BY created_at DESC;")
                .single(Call.of().bind("list_id", listId))
                .map(WaitingListInvite.map())
                .all();
    }

    public WaitingListInvite createInvite(int listId, String code, int maxUses, Instant expiresAt) {
        return Query.query("""
                        INSERT INTO waiting_list_invite (list_id, code, max_uses, expires_at)
                        VALUES (:list_id, :code, :max_uses, :expires_at)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("list_id", listId)
                        .bind("code", code)
                        .bind("max_uses", maxUses)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .map(WaitingListInvite.map())
                .first()
                .orElseThrow();
    }

    public void incrementInviteUses(int inviteId) {
        Query.query("UPDATE waiting_list_invite SET uses = uses + 1 WHERE id = :id;")
                .single(Call.of().bind("id", inviteId))
                .update();
    }

    public Optional<WaitingListInvite> findInviteByCode(String code) {
        return Query.query("SELECT * FROM waiting_list_invite WHERE code = :code;")
                .single(Call.of().bind("code", code))
                .map(WaitingListInvite.map())
                .first();
    }

    public void deleteInvite(int inviteId) {
        Query.query("DELETE FROM waiting_list_invite WHERE id = :id;")
                .single(Call.of().bind("id", inviteId))
                .delete();
    }

    // --- Entries ---

    public List<WaitingListEntry> findEntriesByList(int listId) {
        return Query.query("SELECT * FROM waiting_list_entry WHERE list_id = :list_id ORDER BY created_at;")
                .single(Call.of().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findEntriesByStatus(int listId, WaitingListEntryStatus status) {
        return Query.query(
                        "SELECT * FROM waiting_list_entry WHERE list_id = :list_id AND status = :status ORDER BY created_at;")
                .single(Call.of().bind("list_id", listId).bind("status", status.name()))
                .map(WaitingListEntry.map())
                .all();
    }

    public Optional<WaitingListEntry> findEntryById(int id) {
        return Query.query("SELECT * FROM waiting_list_entry WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(WaitingListEntry.map())
                .first();
    }

    public Optional<WaitingListEntry> findEntryByToken(String token) {
        return Query.query("SELECT * FROM waiting_list_entry WHERE access_token = :token;")
                .single(Call.of().bind("token", token))
                .map(WaitingListEntry.map())
                .first();
    }

    public WaitingListEntry createEntry(
            int listId,
            String firstname,
            String lastname,
            String parentName,
            String email,
            String accessToken,
            String notes) {
        return Query.query("""
                        INSERT INTO waiting_list_entry (list_id, firstname, lastname, parent_name, email, access_token, notes)
                        VALUES (:list_id, :firstname, :lastname, :parent_name, :email, :access_token, :notes)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("access_token", accessToken)
                        .bind("notes", notes))
                .map(WaitingListEntry.map())
                .first()
                .orElseThrow();
    }

    public void updateEntryStatus(int id, WaitingListEntryStatus status) {
        Query.query("UPDATE waiting_list_entry SET status = :status WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("status", status.name()))
                .update();
    }

    public void updateEntryStatusWithTimestamp(int id, WaitingListEntryStatus status, String timestampColumn) {
        Query.query("UPDATE waiting_list_entry SET status = :status, " + timestampColumn + " = :ts WHERE id = :id;")
                .single(Call.of()
                        .bind("id", id)
                        .bind("status", status.name())
                        .bind("ts", Instant.now(), INSTANT_TIMESTAMP))
                .update();
    }

    public void updateCreatedAt(int id, Instant createdAt) {
        Query.query("UPDATE waiting_list_entry SET created_at = :created_at WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("created_at", createdAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void updateEntry(int id, String firstname, String lastname, String parentName, String email, String notes) {
        Query.query("""
                        UPDATE waiting_list_entry SET firstname = :firstname, lastname = :lastname,
                        parent_name = :parent_name, email = :email, notes = :notes WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("notes", notes))
                .update();
    }

    public void updateConfirmedAt(int id, Instant confirmedAt) {
        Query.query(
                        "UPDATE waiting_list_entry SET confirmed_at = :confirmed_at, reminder_sent_at = NULL WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("confirmed_at", confirmedAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void updateReminderSentAt(int id, Instant sentAt) {
        Query.query("UPDATE waiting_list_entry SET reminder_sent_at = :sent_at WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("sent_at", sentAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void linkMember(int entryId, int memberId) {
        Query.query("UPDATE waiting_list_entry SET member_id = :member_id WHERE id = :id;")
                .single(Call.of().bind("id", entryId).bind("member_id", memberId))
                .update();
    }

    public void incrementAttendanceCount(int entryId) {
        Query.query("UPDATE waiting_list_entry SET attendance_count = attendance_count + 1 WHERE id = :id;")
                .single(Call.of().bind("id", entryId))
                .update();
    }

    public void deleteEntry(int id) {
        Query.query("DELETE FROM waiting_list_entry WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete();
    }

    public List<WaitingListEntry> findExpiredConfirmations(int listId, int intervalDays) {
        return Query.query("""
                        SELECT * FROM waiting_list_entry
                        WHERE list_id = :list_id AND status = 'WAITING'
                        AND confirmed_at + make_interval(days => :interval_days) < now()
                        AND reminder_sent_at IS NULL;""")
                .single(Call.of().bind("list_id", listId).bind("interval_days", intervalDays))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findPreRemovalWarningDue(int listId) {
        return Query.query("""
                        SELECT * FROM waiting_list_entry
                        WHERE list_id = :list_id AND status = 'WAITING'
                        AND reminder_sent_at IS NOT NULL
                        AND reminder_sent_at + interval '16 days' < now()
                        AND reminder_sent_at + interval '17 days' > now();""")
                .single(Call.of().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findGracePeriodExpired(int listId) {
        return Query.query("""
                        SELECT * FROM waiting_list_entry
                        WHERE list_id = :list_id AND status = 'WAITING'
                        AND reminder_sent_at IS NOT NULL
                        AND reminder_sent_at + interval '30 days' < now();""")
                .single(Call.of().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public int countEntriesByList(int listId) {
        return Query.query(
                        "SELECT count(*) as cnt FROM waiting_list_entry WHERE list_id = :list_id AND status = 'WAITING';")
                .single(Call.of().bind("list_id", listId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    // --- Entry Values ---

    public List<WaitingListEntryValue> findEntryValues(int entryId) {
        return Query.query("SELECT * FROM waiting_list_entry_value WHERE entry_id = :entry_id;")
                .single(Call.of().bind("entry_id", entryId))
                .map(WaitingListEntryValue.map())
                .all();
    }

    public void upsertEntryValue(int entryId, int fieldId, String value) {
        Query.query("""
                        INSERT INTO waiting_list_entry_value (entry_id, field_id, value)
                        VALUES (:entry_id, :field_id, :value::jsonb)
                        ON CONFLICT (entry_id, field_id) DO UPDATE SET value = :value::jsonb;""")
                .single(Call.of()
                        .bind("entry_id", entryId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    public int countPendingEntries(int stationId) {
        return Query.query("""
                        SELECT count(*) AS cnt FROM waiting_list_entry wle
                        JOIN waiting_list wl ON wl.id = wle.list_id
                        WHERE wl.station_id = :station_id AND wle.status = 'WAITING';""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    // --- Guardians ---

    public List<WaitingListEntryGuardian> findGuardiansByEntry(int entryId) {
        return Query.query("SELECT * FROM waiting_list_entry_guardian WHERE entry_id = :entry_id ORDER BY position;")
                .single(Call.of().bind("entry_id", entryId))
                .map(WaitingListEntryGuardian.map())
                .all();
    }

    public List<WaitingListEntryGuardian> findGuardiansByList(int listId) {
        return Query.query("""
                        SELECT g.* FROM waiting_list_entry_guardian g
                        JOIN waiting_list_entry e ON e.id = g.entry_id
                        WHERE e.list_id = :list_id
                        ORDER BY g.entry_id, g.position;""")
                .single(Call.of().bind("list_id", listId))
                .map(WaitingListEntryGuardian.map())
                .all();
    }

    public WaitingListEntryGuardian createGuardian(int entryId, String name, String email, String phone, int position) {
        return Query.query("""
                        INSERT INTO waiting_list_entry_guardian (entry_id, name, email, phone, position)
                        VALUES (:entry_id, :name, :email, :phone, :position) RETURNING *;""")
                .single(Call.of()
                        .bind("entry_id", entryId)
                        .bind("name", name)
                        .bind("email", email)
                        .bind("phone", phone)
                        .bind("position", position))
                .map(WaitingListEntryGuardian.map())
                .first()
                .orElseThrow();
    }

    public void deleteGuardiansByEntry(int entryId) {
        Query.query("DELETE FROM waiting_list_entry_guardian WHERE entry_id = :entry_id;")
                .single(Call.of().bind("entry_id", entryId))
                .delete();
    }
}
