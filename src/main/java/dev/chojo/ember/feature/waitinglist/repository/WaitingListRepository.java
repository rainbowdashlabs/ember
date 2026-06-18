/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.repository;

import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import dev.chojo.ember.feature.waitinglist.entity.WaitlistVerificationToken;
import dev.chojo.ember.util.JsonUtil;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class WaitingListRepository {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    // --- Waiting List CRUD ---

    public List<WaitingList> findAll() {
        return query("SELECT * FROM waiting_list ORDER BY created_at DESC;")
                .single(call())
                .map(WaitingList.map())
                .all();
    }

    public List<WaitingList> findByStation(int stationId) {
        return query("SELECT * FROM waiting_list WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(call().bind("station_id", stationId))
                .map(WaitingList.map())
                .all();
    }

    public Optional<WaitingList> findById(int id) {
        return query("SELECT * FROM waiting_list WHERE id = :id;")
                .single(call().bind("id", id))
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
            int attendanceThreshold,
            boolean isPublic) {
        return query("""
                INSERT
                INTO
                    waiting_list
                    (station_id, name, description, scoring_formula, confirm_interval_days,
                     testing_group_id, join_group_id, attendance_threshold, public)
                VALUES
                    (:station_id, :name, :description, :scoring_formula, :confirm_interval_days,
                     :testing_group_id, :join_group_id, :attendance_threshold, :public)
                RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("scoring_formula", scoringFormula)
                        .bind("confirm_interval_days", confirmIntervalDays)
                        .bind("testing_group_id", testingGroupId)
                        .bind("join_group_id", joinGroupId)
                        .bind("attendance_threshold", attendanceThreshold)
                        .bind("public", isPublic))
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
            int attendanceThreshold,
            boolean isPublic) {
        return query("""
                UPDATE waiting_list
                SET
                    name                  = :name,
                    description           = :description,
                    scoring_formula       = :scoring_formula,
                    confirm_interval_days = :confirm_interval_days,
                    testing_group_id      = :testing_group_id,
                    join_group_id         = :join_group_id,
                    attendance_threshold  = :attendance_threshold,
                    public                = :public
                WHERE id = :id
                RETURNING *;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("scoring_formula", scoringFormula)
                        .bind("confirm_interval_days", confirmIntervalDays)
                        .bind("testing_group_id", testingGroupId)
                        .bind("join_group_id", joinGroupId)
                        .bind("attendance_threshold", attendanceThreshold)
                        .bind("public", isPublic))
                .map(WaitingList.map())
                .first();
    }

    public Optional<WaitingList> updateVisibleFields(int id, String visibleFieldsJson) {
        return query("""
                UPDATE waiting_list SET visible_fields = :visible_fields::JSONB
                WHERE id = :id RETURNING *;""")
                .single(call().bind("id", id).bind("visible_fields", visibleFieldsJson))
                .map(WaitingList.map())
                .first();
    }

    public void delete(int id) {
        query("DELETE FROM waiting_list WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    // --- Fields ---

    public List<WaitingListField> findFieldsByList(int listId) {
        return query("SELECT * FROM waiting_list_field WHERE list_id = :list_id ORDER BY position;")
                .single(call().bind("list_id", listId))
                .map(WaitingListField.map())
                .all();
    }

    public WaitingListField createField(
            int listId,
            String name,
            WaitingListFieldType fieldType,
            WaitingListFieldConfig config,
            int position,
            boolean required,
            boolean isPublic) {
        return query("""
                INSERT INTO waiting_list_field (list_id, name, field_type, config, position, required, public)
                VALUES (:list_id, :name, :field_type, :config::JSONB, :position, :required, :public)
                RETURNING *;""")
                .single(call().bind("list_id", listId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("required", required)
                        .bind("public", isPublic))
                .map(WaitingListField.map())
                .first()
                .orElseThrow();
    }

    public Optional<WaitingListField> updateField(
            int fieldId,
            String name,
            WaitingListFieldType fieldType,
            WaitingListFieldConfig config,
            int position,
            boolean required,
            boolean isPublic) {
        return query("""
                UPDATE waiting_list_field
                SET
                    name       = :name,
                    field_type = :field_type,
                    config     = :config::JSONB,
                    position   = :position,
                    required   = :required,
                    public     = :public
                WHERE id = :id
                RETURNING *;""")
                .single(call().bind("id", fieldId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("required", required)
                        .bind("public", isPublic))
                .map(WaitingListField.map())
                .first();
    }

    public void deleteField(int fieldId) {
        query("DELETE FROM waiting_list_field WHERE id = :id;")
                .single(call().bind("id", fieldId))
                .delete();
    }

    // --- Invites ---

    public List<WaitingListInvite> findInvitesByList(int listId) {
        return query("SELECT * FROM waiting_list_invite WHERE list_id = :list_id ORDER BY created_at DESC;")
                .single(call().bind("list_id", listId))
                .map(WaitingListInvite.map())
                .all();
    }

    public WaitingListInvite createInvite(int listId, String code, int maxUses, Instant expiresAt) {
        return query("""
                INSERT
                INTO
                    waiting_list_invite
                    (list_id, code, max_uses, expires_at)
                VALUES
                    (:list_id, :code, :max_uses, :expires_at)
                RETURNING *;""")
                .single(call().bind("list_id", listId)
                        .bind("code", code)
                        .bind("max_uses", maxUses)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .map(WaitingListInvite.map())
                .first()
                .orElseThrow();
    }

    public void incrementInviteUses(int inviteId) {
        query("UPDATE waiting_list_invite SET uses = uses + 1 WHERE id = :id;")
                .single(call().bind("id", inviteId))
                .update();
    }

    public Optional<WaitingListInvite> findInviteByCode(String code) {
        return query("SELECT * FROM waiting_list_invite WHERE code = :code;")
                .single(call().bind("code", code))
                .map(WaitingListInvite.map())
                .first();
    }

    public void deleteInvite(int inviteId) {
        query("DELETE FROM waiting_list_invite WHERE id = :id;")
                .single(call().bind("id", inviteId))
                .delete();
    }

    // --- Entries ---

    public List<WaitingListEntry> findEntriesByList(int listId) {
        return query("SELECT * FROM waiting_list_entry WHERE list_id = :list_id ORDER BY created_at;")
                .single(call().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findEntriesByStatus(int listId, WaitingListEntryStatus status) {
        return query(
                        "SELECT * FROM waiting_list_entry WHERE list_id = :list_id AND status = :status ORDER BY created_at;")
                .single(call().bind("list_id", listId).bind("status", status.name()))
                .map(WaitingListEntry.map())
                .all();
    }

    public Optional<WaitingListEntry> findEntryById(int id) {
        return query("SELECT * FROM waiting_list_entry WHERE id = :id;")
                .single(call().bind("id", id))
                .map(WaitingListEntry.map())
                .first();
    }

    public Optional<WaitingListEntry> findEntryByToken(String token) {
        return query("SELECT * FROM waiting_list_entry WHERE access_token = :token;")
                .single(call().bind("token", token))
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
            String notes,
            ConsentProof consent) {
        return query("""
                INSERT
                INTO
                    waiting_list_entry
                    (list_id, firstname, lastname, parent_name, email, access_token, notes, consent_proof)
                VALUES
                    (:list_id, :firstname, :lastname, :parent_name, :email, :access_token, :notes, :consent_proof::JSONB)
                RETURNING *;""")
                .single(call().bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("access_token", accessToken)
                        .bind("notes", notes)
                        .bind("consent_proof", consent != null ? consent.toJson() : null))
                .map(WaitingListEntry.map())
                .first()
                .orElseThrow();
    }

    public void updateEntryStatus(int id, WaitingListEntryStatus status) {
        query("UPDATE waiting_list_entry SET status = :status WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status.name()))
                .update();
    }

    public void updateEntryStatusWithTimestamp(int id, WaitingListEntryStatus status, String timestampColumn) {
        query("UPDATE waiting_list_entry SET status = :status, " + timestampColumn + " = :ts WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("status", status.name())
                        .bind("ts", Instant.now(), INSTANT_TIMESTAMP))
                .update();
    }

    public void updateCreatedAt(int id, Instant createdAt) {
        query("UPDATE waiting_list_entry SET created_at = :created_at WHERE id = :id;")
                .single(call().bind("id", id).bind("created_at", createdAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void updateEntry(int id, String firstname, String lastname, String parentName, String email, String notes) {
        query("""
                UPDATE waiting_list_entry
                SET
                    firstname   = :firstname,
                    lastname    = :lastname,
                    parent_name = :parent_name,
                    email       = :email,
                    notes       = :notes
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("notes", notes))
                .update();
    }

    public void updateConfirmedAt(int id, Instant confirmedAt) {
        query("""
                UPDATE waiting_list_entry
                SET
                    confirmed_at     = :confirmed_at,
                    reminder_sent_at = NULL
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("confirmed_at", confirmedAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void updateReminderSentAt(int id, Instant sentAt) {
        query("UPDATE waiting_list_entry SET reminder_sent_at = :sent_at WHERE id = :id;")
                .single(call().bind("id", id).bind("sent_at", sentAt, INSTANT_TIMESTAMP))
                .update();
    }

    public void linkMember(int entryId, int memberId) {
        query("UPDATE waiting_list_entry SET member_id = :member_id WHERE id = :id;")
                .single(call().bind("id", entryId).bind("member_id", memberId))
                .update();
    }

    public void incrementAttendanceCount(int entryId) {
        query("UPDATE waiting_list_entry SET attendance_count = attendance_count + 1 WHERE id = :id;")
                .single(call().bind("id", entryId))
                .update();
    }

    public void deleteEntry(int id) {
        query("DELETE FROM waiting_list_entry WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    public List<WaitingListEntry> findExpiredConfirmations(int listId, int intervalDays) {
        return query("""
                SELECT *
                FROM
                    waiting_list_entry
                WHERE list_id = :list_id
                  AND status = 'WAITING'
                  AND confirmed_at + make_interval(days => :interval_days) < now()
                  AND reminder_sent_at IS NULL;""")
                .single(call().bind("list_id", listId).bind("interval_days", intervalDays))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findPreRemovalWarningDue(int listId) {
        return query("""
                SELECT *
                FROM
                    waiting_list_entry
                WHERE list_id = :list_id
                  AND status = 'WAITING'
                  AND reminder_sent_at IS NOT NULL
                  AND reminder_sent_at + INTERVAL '16 days' < now()
                  AND reminder_sent_at + INTERVAL '17 days' > now();""")
                .single(call().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findGracePeriodExpired(int listId) {
        return query("""
                SELECT *
                FROM
                    waiting_list_entry
                WHERE list_id = :list_id
                  AND status = 'WAITING'
                  AND reminder_sent_at IS NOT NULL
                  AND reminder_sent_at + INTERVAL '30 days' < now();""")
                .single(call().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public int countEntriesByList(int listId) {
        return query("SELECT count(*) AS cnt FROM waiting_list_entry WHERE list_id = :list_id AND status = 'WAITING';")
                .single(call().bind("list_id", listId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    // --- Entry Values ---

    public List<WaitingListEntryValue> findEntryValues(int entryId) {
        return query("SELECT * FROM waiting_list_entry_value WHERE entry_id = :entry_id;")
                .single(call().bind("entry_id", entryId))
                .map(WaitingListEntryValue.map())
                .all();
    }

    public void upsertEntryValue(int entryId, int fieldId, JsonNode value) {
        query("""
                INSERT INTO waiting_list_entry_value (entry_id, field_id, value)
                VALUES (:entry_id, :field_id, :value::JSONB)
                ON CONFLICT (entry_id, field_id) DO UPDATE SET value = :value::JSONB;""")
                .single(call().bind("entry_id", entryId)
                        .bind("field_id", fieldId)
                        .bind("value", JsonUtil.toJson(value)))
                .insert();
    }

    public int countPendingEntries(int stationId) {
        return query("""
                SELECT
                    count(*) AS cnt
                FROM
                    waiting_list_entry wle
                        JOIN waiting_list wl
                        ON wl.id = wle.list_id
                WHERE wl.station_id = :station_id
                  AND wle.status = 'WAITING';""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    // --- Guardians ---

    public List<WaitingListEntryGuardian> findGuardiansByEntry(int entryId) {
        return query("SELECT * FROM waiting_list_entry_guardian WHERE entry_id = :entry_id ORDER BY position;")
                .single(call().bind("entry_id", entryId))
                .map(WaitingListEntryGuardian.map())
                .all();
    }

    public List<WaitingListEntryGuardian> findGuardiansByList(int listId) {
        return query("""
                SELECT
                    g.*
                FROM
                    waiting_list_entry_guardian g
                        JOIN waiting_list_entry e
                        ON e.id = g.entry_id
                WHERE e.list_id = :list_id
                ORDER BY g.entry_id, g.position;""")
                .single(call().bind("list_id", listId))
                .map(WaitingListEntryGuardian.map())
                .all();
    }

    public WaitingListEntryGuardian createGuardian(
            int entryId, String firstname, String lastname, String email, String phone, int position) {
        return query("""
                INSERT
                INTO
                    waiting_list_entry_guardian
                    (entry_id, firstname, lastname, email, phone, position)
                VALUES
                    (:entry_id, :firstname, :lastname, :email, :phone, :position)
                RETURNING *;""")
                .single(call().bind("entry_id", entryId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("email", email)
                        .bind("phone", phone)
                        .bind("position", position))
                .map(WaitingListEntryGuardian.map())
                .first()
                .orElseThrow();
    }

    public void deleteGuardiansByEntry(int entryId) {
        query("DELETE FROM waiting_list_entry_guardian WHERE entry_id = :entry_id;")
                .single(call().bind("entry_id", entryId))
                .delete();
    }

    // --- Public waitlist queries ---

    public List<WaitingList> findPublicByStation(int stationId) {
        return query(
                        "SELECT * FROM waiting_list WHERE station_id = :station_id AND public = TRUE ORDER BY created_at DESC;")
                .single(call().bind("station_id", stationId))
                .map(WaitingList.map())
                .all();
    }

    public List<WaitingListField> findPublicFieldsByList(int listId) {
        return query("SELECT * FROM waiting_list_field WHERE list_id = :list_id AND public = TRUE ORDER BY position;")
                .single(call().bind("list_id", listId))
                .map(WaitingListField.map())
                .all();
    }

    public boolean hasPublicWaitlists(int stationId) {
        return query(
                        "SELECT exists(SELECT 1 FROM waiting_list WHERE station_id = :station_id AND public = TRUE) AS exists;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getBoolean("exists"))
                .first()
                .orElse(false);
    }

    public WaitingListEntry createEntryWithStatus(
            int listId,
            String firstname,
            String lastname,
            String parentName,
            String email,
            String accessToken,
            String notes,
            WaitingListEntryStatus status,
            ConsentProof consent) {
        return query("""
                INSERT
                INTO
                    waiting_list_entry
                    (list_id, firstname, lastname, parent_name, email, access_token, notes, status, consent_proof)
                VALUES
                    (:list_id, :firstname, :lastname, :parent_name, :email, :access_token, :notes, :status, :consent_proof::JSONB)
                RETURNING *;""")
                .single(call().bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("access_token", accessToken)
                        .bind("notes", notes)
                        .bind("status", status.name())
                        .bind("consent_proof", consent != null ? consent.toJson() : null))
                .map(WaitingListEntry.map())
                .first()
                .orElseThrow();
    }

    // --- Verification tokens ---

    public WaitlistVerificationToken createVerificationToken(
            String token,
            int listId,
            String firstname,
            String lastname,
            String email,
            List<GuardianInput> guardians,
            Map<Integer, JsonNode> fieldValues,
            String notes,
            ConsentProof consent) {
        String guardiansPayload = JSON.writeValueAsString(guardians != null ? guardians : List.of());
        String fieldValuesPayload = JSON.writeValueAsString(fieldValues != null ? fieldValues : Map.of());
        return query("""
                INSERT
                INTO
                    waitlist_verification_token
                    (token, list_id, firstname, lastname, email, guardians, field_values, notes, consent_proof)
                VALUES
                    (:token, :list_id, :firstname, :lastname, :email, :guardians::JSONB, :field_values::JSONB, :notes, :consent_proof::JSONB)
                RETURNING *;""")
                .single(call().bind("token", token)
                        .bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("email", email)
                        .bind("guardians", guardiansPayload)
                        .bind("field_values", fieldValuesPayload)
                        .bind("notes", notes)
                        .bind("consent_proof", consent.toJson()))
                .map(WaitlistVerificationToken.map())
                .first()
                .orElseThrow();
    }

    public Optional<WaitlistVerificationToken> findVerificationByToken(String token) {
        return query("SELECT * FROM waitlist_verification_token WHERE token = :token;")
                .single(call().bind("token", token))
                .map(WaitlistVerificationToken.map())
                .first();
    }

    public void deleteVerificationToken(int id) {
        query("DELETE FROM waitlist_verification_token WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    public void deleteExpiredVerificationTokens() {
        query("DELETE FROM waitlist_verification_token WHERE expires_at < now();")
                .single(call())
                .delete();
    }
}
