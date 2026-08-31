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
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import dev.chojo.ember.feature.waitinglist.entity.WaitlistVerificationToken;
import dev.chojo.ember.util.JsonUtil;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.alias;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class WaitingListRepository {

    private static final String WAITING_LIST_COLUMNS = """
            id, station_id, name, description, scoring_formula, confirm_interval_days, created_at, \
            visible_fields, testing_group_id, join_group_id, join_user_type, attendance_threshold, public, \
            min_age_register, min_age_join""";
    private static final String WAITING_LIST_FIELD_COLUMNS =
            "id, list_id, name, field_type, config, position, required, public";
    private static final String WAITING_LIST_INVITE_COLUMNS =
            "id, list_id, code, max_uses, uses, expires_at, created_at";
    private static final String WAITING_LIST_ENTRY_COLUMNS = """
            id, list_id, firstname, lastname, parent_name, email, access_token, status, confirmed_at, \
            reminder_sent_at, created_at, notes, member_id, invited_at, testing_at, joined_at, \
            withdrawn_at, attendance_count, invited_event_id, invited_event_date, invited_arrival_time""";
    private static final String WAITING_LIST_ENTRY_VALUE_COLUMNS = "entry_id, field_id, value";
    private static final String WAITING_LIST_ENTRY_GUARDIAN_COLUMNS =
            "id, entry_id, firstname, lastname, email, phone, position";
    private static final String WAITLIST_VERIFICATION_TOKEN_COLUMNS = """
            id, token, list_id, firstname, lastname, email, guardians, field_values, notes, \
            created_at, expires_at, consent_proof""";

    // --- Waiting List CRUD ---

    public List<WaitingList> findAll() {
        return query("SELECT %s FROM waiting_list ORDER BY created_at DESC;", WAITING_LIST_COLUMNS)
                .single(call())
                .map(WaitingList.map())
                .all();
    }

    public List<WaitingList> findByStation(int stationId) {
        return query(
                        "SELECT %s FROM waiting_list WHERE station_id = :station_id ORDER BY created_at DESC;",
                        WAITING_LIST_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(WaitingList.map())
                .all();
    }

    public Optional<WaitingList> findById(int id) {
        return SqlSupport.findById("waiting_list", WAITING_LIST_COLUMNS, id, WaitingList.map());
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
            boolean isPublic,
            Integer minAgeRegister,
            Integer minAgeJoin) {
        return insertReturning(
                """
                INSERT
                INTO
                    waiting_list
                    (station_id, name, description, scoring_formula, confirm_interval_days,
                     testing_group_id, join_group_id, attendance_threshold, public,
                     min_age_register, min_age_join)
                VALUES
                    (:station_id, :name, :description, :scoring_formula, :confirm_interval_days,
                     :testing_group_id, :join_group_id, :attendance_threshold, :public,
                     :min_age_register, :min_age_join)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("scoring_formula", scoringFormula)
                        .bind("confirm_interval_days", confirmIntervalDays)
                        .bind("testing_group_id", testingGroupId)
                        .bind("join_group_id", joinGroupId)
                        .bind("attendance_threshold", attendanceThreshold)
                        .bind("public", isPublic)
                        .bind("min_age_register", minAgeRegister)
                        .bind("min_age_join", minAgeJoin),
                WaitingList.map(),
                WAITING_LIST_COLUMNS);
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
            boolean isPublic,
            Integer minAgeRegister,
            Integer minAgeJoin) {
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
                    public                = :public,
                    min_age_register      = :min_age_register,
                    min_age_join          = :min_age_join
                WHERE id = :id
                RETURNING %s;""", WAITING_LIST_COLUMNS)
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("scoring_formula", scoringFormula)
                        .bind("confirm_interval_days", confirmIntervalDays)
                        .bind("testing_group_id", testingGroupId)
                        .bind("join_group_id", joinGroupId)
                        .bind("attendance_threshold", attendanceThreshold)
                        .bind("public", isPublic)
                        .bind("min_age_register", minAgeRegister)
                        .bind("min_age_join", minAgeJoin))
                .map(WaitingList.map())
                .first();
    }

    public Optional<WaitingList> updateVisibleFields(int id, String visibleFieldsJson) {
        return query("""
                UPDATE waiting_list SET visible_fields = :visible_fields::JSONB
                WHERE id = :id RETURNING %s;""", WAITING_LIST_COLUMNS)
                .single(call().bind("id", id).bind("visible_fields", visibleFieldsJson))
                .map(WaitingList.map())
                .first();
    }

    public void delete(int id) {
        deleteById("waiting_list", id);
    }

    // --- Fields ---

    public Optional<WaitingListField> findFieldById(int id) {
        return SqlSupport.findById("waiting_list_field", WAITING_LIST_FIELD_COLUMNS, id, WaitingListField.map());
    }

    public List<WaitingListField> findFieldsByList(int listId) {
        return query(
                        "SELECT %s FROM waiting_list_field WHERE list_id = :list_id ORDER BY position;",
                        WAITING_LIST_FIELD_COLUMNS)
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
        return insertReturning(
                """
                INSERT INTO waiting_list_field (list_id, name, field_type, config, position, required, public)
                VALUES (:list_id, :name, :field_type, :config::JSONB, :position, :required, :public)
                RETURNING %s;""",
                call().bind("list_id", listId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("required", required)
                        .bind("public", isPublic),
                WaitingListField.map(),
                WAITING_LIST_FIELD_COLUMNS);
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
                RETURNING %s;""", WAITING_LIST_FIELD_COLUMNS)
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
        deleteById("waiting_list_field", fieldId);
    }

    // --- Invites ---

    public List<WaitingListInvite> findInvitesByList(int listId) {
        return query(
                        "SELECT %s FROM waiting_list_invite WHERE list_id = :list_id ORDER BY created_at DESC;",
                        WAITING_LIST_INVITE_COLUMNS)
                .single(call().bind("list_id", listId))
                .map(WaitingListInvite.map())
                .all();
    }

    public WaitingListInvite createInvite(int listId, String code, int maxUses, Instant expiresAt) {
        return insertReturning(
                """
                INSERT
                INTO
                    waiting_list_invite
                    (list_id, code, max_uses, expires_at)
                VALUES
                    (:list_id, :code, :max_uses, :expires_at)
                RETURNING %s;""",
                call().bind("list_id", listId)
                        .bind("code", code)
                        .bind("max_uses", maxUses)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP),
                WaitingListInvite.map(),
                WAITING_LIST_INVITE_COLUMNS);
    }

    public void incrementInviteUses(int inviteId) {
        query("UPDATE waiting_list_invite SET uses = uses + 1 WHERE id = :id;")
                .single(call().bind("id", inviteId))
                .update();
    }

    public Optional<WaitingListInvite> findInviteByCode(String code) {
        return query("SELECT %s FROM waiting_list_invite WHERE code = :code;", WAITING_LIST_INVITE_COLUMNS)
                .single(call().bind("code", code))
                .map(WaitingListInvite.map())
                .first();
    }

    public void deleteInvite(int inviteId) {
        deleteById("waiting_list_invite", inviteId);
    }

    // --- Entries ---

    public List<WaitingListEntry> findEntriesByList(int listId) {
        return query(
                        "SELECT %s FROM waiting_list_entry WHERE list_id = :list_id ORDER BY created_at;",
                        WAITING_LIST_ENTRY_COLUMNS)
                .single(call().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findEntriesByStatus(int listId, WaitingListEntryStatus status) {
        return query(
                        "SELECT %s FROM waiting_list_entry WHERE list_id = :list_id AND status = :status ORDER BY created_at;",
                        WAITING_LIST_ENTRY_COLUMNS)
                .single(call().bind("list_id", listId).bind("status", status.name()))
                .map(WaitingListEntry.map())
                .all();
    }

    public Optional<WaitingListEntry> findEntryById(int id) {
        return SqlSupport.findById("waiting_list_entry", WAITING_LIST_ENTRY_COLUMNS, id, WaitingListEntry.map());
    }

    public Optional<WaitingListEntry> findEntryByToken(String token) {
        return query("SELECT %s FROM waiting_list_entry WHERE access_token = :token;", WAITING_LIST_ENTRY_COLUMNS)
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
        return insertReturning(
                """
                INSERT
                INTO
                    waiting_list_entry
                    (list_id, firstname, lastname, parent_name, email, access_token, notes, consent_proof)
                VALUES
                    (:list_id, :firstname, :lastname, :parent_name, :email, :access_token, :notes, :consent_proof::JSONB)
                RETURNING %s;""",
                call().bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("access_token", accessToken)
                        .bind("notes", notes)
                        .bind("consent_proof", consent != null ? consent.toJson() : null),
                WaitingListEntry.map(),
                WAITING_LIST_ENTRY_COLUMNS);
    }

    public void updateEntryStatus(int id, WaitingListEntryStatus status) {
        query("UPDATE waiting_list_entry SET status = :status WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status.name()))
                .update();
    }

    public void updateEntryStatusWithTimestamp(int id, WaitingListEntryStatus status, String timestampColumn) {
        query("UPDATE waiting_list_entry SET status = :status, %s = :ts WHERE id = :id;", timestampColumn)
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

    /**
     * Writes the one appointment the current invitation names, or clears it when the invitation is
     * withdrawn. The date goes with the appointment, so an entry never carries half a reference.
     */
    public void updateInvitation(int entryId, WaitingListInvitation invitation) {
        query("""
                UPDATE waiting_list_entry
                SET
                    invited_event_id     = :event_id,
                    invited_event_date   = :event_date,
                    invited_arrival_time = :arrival_time
                WHERE id = :id;""")
                .single(call().bind("id", entryId)
                        .bind("event_id", invitation != null ? invitation.eventId() : null)
                        .bind("event_date", invitation != null ? invitation.date() : null)
                        .bind("arrival_time", invitation != null ? invitation.arrivalTime() : null))
                .update();
    }

    public void incrementAttendanceCount(int entryId) {
        query("UPDATE waiting_list_entry SET attendance_count = attendance_count + 1 WHERE id = :id;")
                .single(call().bind("id", entryId))
                .update();
    }

    public void deleteEntry(int id) {
        deleteById("waiting_list_entry", id);
    }

    public List<WaitingListEntry> findExpiredConfirmations(int listId, int intervalDays) {
        return query("""
                SELECT %s
                FROM
                    waiting_list_entry
                WHERE list_id = :list_id
                  AND status = 'WAITING'
                  AND confirmed_at + make_interval(days => :interval_days) < now()
                  AND reminder_sent_at IS NULL;""", WAITING_LIST_ENTRY_COLUMNS)
                .single(call().bind("list_id", listId).bind("interval_days", intervalDays))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findPreRemovalWarningDue(int listId) {
        return query("""
                SELECT %s
                FROM
                    waiting_list_entry
                WHERE list_id = :list_id
                  AND status = 'WAITING'
                  AND reminder_sent_at IS NOT NULL
                  AND reminder_sent_at + INTERVAL '16 days' < now()
                  AND reminder_sent_at + INTERVAL '17 days' > now();""", WAITING_LIST_ENTRY_COLUMNS)
                .single(call().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public List<WaitingListEntry> findGracePeriodExpired(int listId) {
        return query("""
                SELECT %s
                FROM
                    waiting_list_entry
                WHERE list_id = :list_id
                  AND status = 'WAITING'
                  AND reminder_sent_at IS NOT NULL
                  AND reminder_sent_at + INTERVAL '30 days' < now();""", WAITING_LIST_ENTRY_COLUMNS)
                .single(call().bind("list_id", listId))
                .map(WaitingListEntry.map())
                .all();
    }

    public int countEntriesByList(int listId) {
        return count(
                "SELECT count(*) AS cnt FROM waiting_list_entry WHERE list_id = :list_id AND status = 'WAITING';",
                call().bind("list_id", listId));
    }

    // --- Entry Values ---

    public List<WaitingListEntryValue> findEntryValues(int entryId) {
        return query(
                        "SELECT %s FROM waiting_list_entry_value WHERE entry_id = :entry_id;",
                        WAITING_LIST_ENTRY_VALUE_COLUMNS)
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
        return count("""
                SELECT
                    count(*) AS cnt
                FROM
                    waiting_list_entry wle
                        JOIN waiting_list wl
                        ON wl.id = wle.list_id
                WHERE wl.station_id = :station_id
                  AND wle.status = 'WAITING';""", call().bind("station_id", stationId));
    }

    // --- Guardians ---

    public List<WaitingListEntryGuardian> findGuardiansByEntry(int entryId) {
        return query(
                        "SELECT %s FROM waiting_list_entry_guardian WHERE entry_id = :entry_id ORDER BY position;",
                        WAITING_LIST_ENTRY_GUARDIAN_COLUMNS)
                .single(call().bind("entry_id", entryId))
                .map(WaitingListEntryGuardian.map())
                .all();
    }

    public List<WaitingListEntryGuardian> findGuardiansByList(int listId) {
        return query("""
                SELECT
                    %s
                FROM
                    waiting_list_entry_guardian g
                        JOIN waiting_list_entry e
                        ON e.id = g.entry_id
                WHERE e.list_id = :list_id
                ORDER BY g.entry_id, g.position;""", alias("g", WAITING_LIST_ENTRY_GUARDIAN_COLUMNS))
                .single(call().bind("list_id", listId))
                .map(WaitingListEntryGuardian.map())
                .all();
    }

    public void createGuardian(
            int entryId, String firstname, String lastname, String email, String phone, int position) {
        insertReturning(
                """
                INSERT
                INTO
                    waiting_list_entry_guardian
                    (entry_id, firstname, lastname, email, phone, position)
                VALUES
                    (:entry_id, :firstname, :lastname, :email, :phone, :position)
                RETURNING %s;""",
                call().bind("entry_id", entryId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("email", email)
                        .bind("phone", phone)
                        .bind("position", position),
                WaitingListEntryGuardian.map(),
                WAITING_LIST_ENTRY_GUARDIAN_COLUMNS);
    }

    public void deleteGuardiansByEntry(int entryId) {
        query("DELETE FROM waiting_list_entry_guardian WHERE entry_id = :entry_id;")
                .single(call().bind("entry_id", entryId))
                .delete();
    }

    // --- Public waitlist queries ---

    public List<WaitingList> findPublicByStation(int stationId) {
        return query(
                        "SELECT %s FROM waiting_list WHERE station_id = :station_id AND public = TRUE ORDER BY created_at DESC;",
                        WAITING_LIST_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(WaitingList.map())
                .all();
    }

    public List<WaitingListField> findPublicFieldsByList(int listId) {
        return query(
                        "SELECT %s FROM waiting_list_field WHERE list_id = :list_id AND public = TRUE ORDER BY position;",
                        WAITING_LIST_FIELD_COLUMNS)
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
        return insertReturning(
                """
                INSERT
                INTO
                    waiting_list_entry
                    (list_id, firstname, lastname, parent_name, email, access_token, notes, status, consent_proof)
                VALUES
                    (:list_id, :firstname, :lastname, :parent_name, :email, :access_token, :notes, :status, :consent_proof::JSONB)
                RETURNING %s;""",
                call().bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("parent_name", parentName)
                        .bind("email", email)
                        .bind("access_token", accessToken)
                        .bind("notes", notes)
                        .bind("status", status.name())
                        .bind("consent_proof", consent != null ? consent.toJson() : null),
                WaitingListEntry.map(),
                WAITING_LIST_ENTRY_COLUMNS);
    }

    // --- Verification tokens ---

    public void createVerificationToken(
            String token,
            int listId,
            String firstname,
            String lastname,
            String email,
            List<GuardianInput> guardians,
            Map<Integer, JsonNode> fieldValues,
            String notes,
            ConsentProof consent) {
        insertReturning(
                """
                INSERT
                INTO
                    waitlist_verification_token
                    (token, list_id, firstname, lastname, email, guardians, field_values, notes, consent_proof)
                VALUES
                    (:token, :list_id, :firstname, :lastname, :email, :guardians::JSONB, :field_values::JSONB, :notes, :consent_proof::JSONB)
                RETURNING %s;""".formatted(WAITLIST_VERIFICATION_TOKEN_COLUMNS),
                call().bind("token", token)
                        .bind("list_id", listId)
                        .bind("firstname", firstname)
                        .bind("lastname", lastname)
                        .bind("email", email)
                        .bind("guardians", WaitlistVerificationToken.guardiansToJson(guardians))
                        .bind("field_values", WaitlistVerificationToken.fieldValuesToJson(fieldValues))
                        .bind("notes", notes)
                        .bind("consent_proof", consent.toJson()),
                WaitlistVerificationToken.map(),
                WAITLIST_VERIFICATION_TOKEN_COLUMNS);
    }

    public Optional<WaitlistVerificationToken> findVerificationByToken(String token) {
        return query(
                        "SELECT %s FROM waitlist_verification_token WHERE token = :token;",
                        WAITLIST_VERIFICATION_TOKEN_COLUMNS)
                .single(call().bind("token", token))
                .map(WaitlistVerificationToken.map())
                .first();
    }

    public void deleteVerificationToken(int id) {
        deleteById("waitlist_verification_token", id);
    }
}
