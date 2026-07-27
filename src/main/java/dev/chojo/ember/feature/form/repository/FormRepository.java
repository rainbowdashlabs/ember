/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.repository;

import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Repository for form-related database operations including forms, questions, responses, answers, and access restrictions.
 */
@Singleton
public class FormRepository {

    private static final String FORM_COLUMNS_BARE =
            "id, station_id, title, description, status, shuffle_questions, allow_edit, forced, start_at, end_at, closed_at, created_by, created_at, updated_at, restriction_mode, purpose, public_uid";
    private static final String FORM_COLUMNS = SqlSupport.alias("f", FORM_COLUMNS_BARE);
    private static final String FORM_COMPUTED =
            "EXISTS(SELECT 1 FROM form_restriction r WHERE r.form_id = f.id) AS restricted, (SELECT count(*) FROM form_response fr WHERE fr.form_id = f.id)::INT AS response_count, GREATEST(f.updated_at, (SELECT MAX(fr2.updated_at) FROM form_response fr2 WHERE fr2.form_id = f.id)) AS last_activity_at";
    private static final String QUESTION_COLUMNS =
            "id, form_id, position, question_type, title, description, required, shuffle, config";
    private static final String RESPONSE_COLUMNS =
            "id, form_id, member_id, submitted_by, submitted_at, updated_at, submitter_hash, acknowledged_at, acknowledged_by";
    private static final String ANSWER_COLUMNS = "id, response_id, question_id, value";

    // -- Forms --

    /**
     * Retrieves all forms for a station, ordered by creation date descending.
     *
     * @param stationId the station to query
     * @return list of forms belonging to the station
     */
    public List<Form> findByStation(int stationId) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    form f
                WHERE f.station_id = :station_id
                ORDER BY f.created_at DESC;""", FORM_COLUMNS, FORM_COMPUTED)
                .single(call().bind("station_id", stationId))
                .map(Form.map())
                .all();
    }

    /**
     * Retrieves all forms for a station with the given purpose, ordered by creation date descending.
     *
     * @param stationId the station to query
     * @param purpose   the purpose to filter by
     * @return list of forms belonging to the station with the given purpose
     */
    public List<Form> findByStationAndPurpose(int stationId, FormPurpose purpose) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    form f
                WHERE f.station_id = :station_id
                  AND f.purpose = :purpose
                ORDER BY f.created_at DESC;""", FORM_COLUMNS, FORM_COMPUTED)
                .single(call().bind("station_id", stationId).bind("purpose", purpose))
                .map(Form.map())
                .all();
    }

    /**
     * Finds a form by its public UUID.
     *
     * @param publicUid the public UUID
     * @return the form, or empty if not found
     */
    public Optional<Form> findByPublicUid(UUID publicUid) {
        return query("""
                SELECT
                    %s, %s
                FROM
                    form f
                WHERE f.public_uid = :public_uid::uuid;""", FORM_COLUMNS, FORM_COMPUTED)
                .single(call().bind("public_uid", publicUid, UUID_STRING))
                .map(Form.map())
                .first();
    }

    /**
     * Retrieves forms for a station that the given member is allowed to see.
     * Uses the DB restriction check function which resolves role inheritance, mode, and manager bypass.
     *
     * @param stationId the station ID
     * @param memberId  the requesting member ID
     * @return the filtered list of forms
     */
    public List<Form> findByStationForMember(int stationId, int memberId) {
        return query("""
                SELECT %s, %s
                FROM form f
                WHERE f.station_id = :station_id
                  AND check_restriction('form_restriction', 'form_id', 'form', 'id', f.id, :member_id, 'POLL_MANAGER')
                ORDER BY f.created_at DESC;""", FORM_COLUMNS, FORM_COMPUTED)
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(Form.map())
                .all();
    }

    /**
     * Finds a form by its unique identifier.
     *
     * @param id the form ID
     * @return the form, or empty if not found
     */
    public Optional<Form> findById(int id) {
        return query("""
                SELECT %s, %s
                FROM form f
                WHERE f.id = :id;""", FORM_COLUMNS, FORM_COMPUTED)
                .single(call().bind("id", id))
                .map(Form.map())
                .first();
    }

    public List<Form> findForcedPending(int stationId, int memberId) {
        return query("""
                SELECT %s, %s
                FROM form f
                WHERE f.station_id = :station_id
                  AND f.forced = TRUE
                  AND f.status = 'OPEN'
                  AND (f.start_at IS NULL OR f.start_at <= now())
                  AND (f.end_at IS NULL OR f.end_at >= now())
                  AND NOT exists (
                      SELECT 1 FROM form_response fr
                      WHERE fr.form_id = f.id AND fr.member_id = :member_id)
                ORDER BY f.title;""", FORM_COLUMNS, FORM_COMPUTED)
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(Form.map())
                .all();
    }

    /**
     * Creates a new form in DRAFT status.
     *
     * @param stationId        the station this form belongs to
     * @param title            form title
     * @param description      form description
     * @param shuffleQuestions whether to randomize question order
     * @param allowEdit        whether respondents may edit their response
     * @param startAt          optional start time for accepting responses
     * @param endAt            optional end time for accepting responses
     * @param createdBy        member ID of the creator
     * @return the newly created form
     */
    public Form create(
            int stationId,
            String title,
            String description,
            boolean shuffleQuestions,
            boolean allowEdit,
            boolean forced,
            Instant startAt,
            Instant endAt,
            int createdBy,
            FormPurpose purpose) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO form AS f(station_id, title, description, shuffle_questions, allow_edit, forced, start_at, end_at, created_by, purpose)
                VALUES (:station_id, :title, :description, :shuffle_questions, :allow_edit, :forced, :start_at, :end_at, :created_by, :purpose)
                RETURNING %s, %s;""",
                call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("shuffle_questions", shuffleQuestions)
                        .bind("allow_edit", allowEdit)
                        .bind("forced", forced)
                        .bind("start_at", startAt, INSTANT_TIMESTAMP)
                        .bind("end_at", endAt, INSTANT_TIMESTAMP)
                        .bind("created_by", createdBy)
                        .bind("purpose", purpose.name()),
                Form.map(),
                FORM_COLUMNS,
                FORM_COMPUTED);
    }

    /**
     * Updates the editable fields of a form.
     *
     * @param id               the form ID
     * @param title            new title
     * @param description      new description
     * @param shuffleQuestions whether to randomize question order
     * @param allowEdit        whether respondents may edit their response
     * @param startAt          optional start time
     * @param endAt            optional end time
     * @return {@code true} if a row was updated
     */
    public boolean update(
            int id,
            String title,
            String description,
            boolean shuffleQuestions,
            boolean allowEdit,
            boolean forced,
            Instant startAt,
            Instant endAt) {
        return query("""
                UPDATE form
                SET title = :title, description = :description,
                    shuffle_questions = :shuffle_questions, allow_edit = :allow_edit,
                    forced = :forced,
                    start_at = :start_at, end_at = :end_at, updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("shuffle_questions", shuffleQuestions)
                        .bind("allow_edit", allowEdit)
                        .bind("forced", forced)
                        .bind("start_at", startAt, INSTANT_TIMESTAMP)
                        .bind("end_at", endAt, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Deletes a form by ID.
     *
     * @param id the form ID
     * @return {@code true} if a row was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("form", id);
    }

    /**
     * Updates the status of a form. When closing, the {@code closed_at} timestamp is set automatically.
     *
     * @param id     the form ID
     * @param status the new status
     * @return {@code true} if a row was updated
     */
    public boolean updateStatus(int id, Form.FormStatus status) {
        return query("""
                UPDATE form
                SET
                    status     = :status,
                    closed_at  = CASE WHEN :status = 'CLOSED' THEN now() ELSE closed_at END,
                    updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("status", status))
                .update()
                .changed();
    }

    // -- Questions --

    /**
     * Retrieves all questions for a form, ordered by position.
     *
     * @param formId the form ID
     * @return list of questions
     */
    public List<FormQuestion> findQuestions(int formId) {
        return query("SELECT %s FROM form_question WHERE form_id = :form_id ORDER BY position;", QUESTION_COLUMNS)
                .single(call().bind("form_id", formId))
                .map(FormQuestion.map())
                .all();
    }

    /**
     * Creates a new question for a form.
     *
     * @param formId           the form to add the question to
     * @param position         display order position
     * @param formQuestionType the type of question
     * @param title            the question text
     * @param description      optional description
     * @param required         whether an answer is mandatory
     * @param shuffle          whether answer options should be randomized
     * @param config           type-specific configuration as JSON
     * @return the newly created question
     */
    public FormQuestion createQuestion(
            int formId,
            int position,
            FormQuestionType formQuestionType,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            FormQuestionConfig config) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO form_question(form_id, position, question_type, title, description, required, shuffle, config)
                VALUES (:form_id, :position, :question_type, :title, :description, :required, :shuffle, :config::JSONB)
                RETURNING %s;""",
                call().bind("form_id", formId)
                        .bind("position", position)
                        .bind("question_type", formQuestionType.name())
                        .bind("title", title)
                        .bind("description", description)
                        .bind("required", required)
                        .bind("shuffle", shuffle)
                        .bind("config", config.toJson()),
                FormQuestion.map(),
                QUESTION_COLUMNS);
    }

    /**
     * Updates an existing question's fields.
     *
     * @param id          the question ID
     * @param title       new question text
     * @param description new description
     * @param required    whether an answer is mandatory
     * @param shuffle     whether answer options should be randomized
     * @param config      type-specific configuration as JSON
     * @param position    new display order position
     * @return {@code true} if a row was updated
     */
    public boolean updateQuestion(
            int id,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            FormQuestionConfig config,
            int position) {
        return query("""
                UPDATE form_question
                SET title = :title, description = :description, required = :required,
                    shuffle = :shuffle, config = :config::JSONB, position = :position
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("required", required)
                        .bind("shuffle", shuffle)
                        .bind("config", config.toJson())
                        .bind("position", position))
                .update()
                .changed();
    }

    /**
     * Deletes a question by ID.
     *
     * @param id the question ID
     * @return {@code true} if a row was deleted
     */
    public boolean deleteQuestion(int id) {
        return SqlSupport.deleteById("form_question", id);
    }

    /**
     * Deletes all questions belonging to a form.
     *
     * @param formId the form ID
     */
    public void deleteQuestionsByForm(int formId) {
        query("DELETE FROM form_question WHERE form_id = :form_id;")
                .single(call().bind("form_id", formId))
                .delete();
    }

    // -- Responses --

    /**
     * Retrieves all responses for a form, ordered by submission time.
     *
     * @param formId the form ID
     * @return list of responses
     */
    public List<FormResponse> findResponses(int formId) {
        return query("SELECT %s FROM form_response WHERE form_id = :form_id ORDER BY submitted_at;", RESPONSE_COLUMNS)
                .single(call().bind("form_id", formId))
                .map(FormResponse.map())
                .all();
    }

    /**
     * Finds a specific member's response to a form.
     *
     * @param formId   the form ID
     * @param memberId the member ID
     * @return the response, or empty if the member has not responded
     */
    public Optional<FormResponse> findResponse(int formId, int memberId) {
        return query(
                        "SELECT %s FROM form_response WHERE form_id = :form_id AND member_id = :member_id;",
                        RESPONSE_COLUMNS)
                .single(call().bind("form_id", formId).bind("member_id", memberId))
                .map(FormResponse.map())
                .first();
    }

    /**
     * Looks up a single response by primary key. Used by the acknowledge endpoint when verifying
     * a {@code responseId} actually belongs to the form being acknowledged.
     */
    public Optional<FormResponse> findResponseById(int responseId) {
        return SqlSupport.findById("form_response", RESPONSE_COLUMNS, responseId, FormResponse.map());
    }

    /**
     * Marks a CONTACT-form submission as acknowledged. Idempotent — the first acknowledgement
     * wins (later calls leave {@code acknowledged_at} / {@code acknowledged_by} untouched), so a
     * second viewer doesn't overwrite the original handler's identity.
     */
    public void acknowledgeResponse(int responseId, int acknowledgerMemberId) {
        query("""
                UPDATE form_response
                SET acknowledged_at = now(),
                    acknowledged_by = :member_id
                WHERE id = :id
                  AND acknowledged_at IS NULL;""")
                .single(call().bind("id", responseId).bind("member_id", acknowledgerMemberId))
                .update();
    }

    /**
     * Creates or updates a response for a member. Uses upsert to handle re-submissions.
     *
     * @param formId      the form ID
     * @param memberId    the member the response is for
     * @param submittedBy the member who submitted the response
     * @return the created or updated response
     */
    public FormResponse createResponse(int formId, int memberId, int submittedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    form_response(form_id, member_id, submitted_by)
                VALUES
                    (:form_id, :member_id, :submitted_by)
                ON CONFLICT (form_id, member_id)
                    DO UPDATE
                    SET
                        submitted_by = :submitted_by,
                        updated_at   = now()
                RETURNING %s;""",
                call().bind("form_id", formId).bind("member_id", memberId).bind("submitted_by", submittedBy),
                FormResponse.map(),
                RESPONSE_COLUMNS);
    }

    /**
     * Creates an anonymous response for a public form submission.
     *
     * @param formId        the form ID
     * @param submitterHash SHA-256 hash identifying the submitter
     * @return the newly created response
     */
    public FormResponse createAnonymousResponse(int formId, byte[] submitterHash, ConsentProof consent) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO form_response(form_id, member_id, submitted_by, submitter_hash, consent_proof)
                VALUES (:form_id, NULL, NULL, :submitter_hash, :consent_proof::JSONB)
                RETURNING %s;""",
                call().bind("form_id", formId)
                        .bind("submitter_hash", submitterHash)
                        .bind("consent_proof", consent.toJson()),
                FormResponse.map(),
                RESPONSE_COLUMNS);
    }

    /**
     * Finds the most recent anonymous response for a form by submitter hash.
     *
     * @param formId        the form ID
     * @param submitterHash SHA-256 hash identifying the submitter
     * @return the response, or empty if no such submission exists
     */
    public Optional<FormResponse> findAnonymousResponse(int formId, byte[] submitterHash) {
        return query("""
                SELECT
                    %s
                FROM
                    form_response
                WHERE form_id = :form_id
                  AND submitter_hash = :submitter_hash
                ORDER BY submitted_at DESC
                LIMIT 1;""", RESPONSE_COLUMNS)
                .single(call().bind("form_id", formId).bind("submitter_hash", submitterHash))
                .map(FormResponse.map())
                .first();
    }

    /**
     * Deletes a response by ID.
     *
     * @param responseId the response ID
     * @return {@code true} if a row was deleted
     */
    public boolean deleteResponse(int responseId) {
        return SqlSupport.deleteById("form_response", responseId);
    }

    /**
     * Counts the total number of responses for a form.
     *
     * @param formId the form ID
     * @return the response count
     */
    public int countResponses(int formId) {
        return SqlSupport.count(
                "SELECT count(*) AS cnt FROM form_response WHERE form_id = :form_id;", call().bind("form_id", formId));
    }

    /**
     * Checks whether a member has already submitted a response to a form.
     *
     * @param formId   the form ID
     * @param memberId the member ID
     * @return {@code true} if the member has responded
     */
    public boolean hasResponded(int formId, int memberId) {
        return SqlSupport.exists(
                "SELECT 1 FROM form_response WHERE form_id = :form_id AND member_id = :member_id;",
                call().bind("form_id", formId).bind("member_id", memberId));
    }

    // -- Answers --

    /**
     * Retrieves all answers for a specific response.
     *
     * @param responseId the response ID
     * @return list of answers
     */
    public List<FormAnswer> findAnswers(int responseId) {
        return query("SELECT %s FROM form_answer WHERE response_id = :response_id;", ANSWER_COLUMNS)
                .single(call().bind("response_id", responseId))
                .map(FormAnswer.map())
                .all();
    }

    /**
     * Retrieves all answers submitted for a specific question across all responses. Useful for analytics.
     *
     * @param questionId the question ID
     * @return list of answers from all respondents
     */
    public List<FormAnswer> findAllAnswersForQuestion(int questionId) {
        return query("SELECT %s FROM form_answer WHERE question_id = :question_id;", ANSWER_COLUMNS)
                .single(call().bind("question_id", questionId))
                .map(FormAnswer.map())
                .all();
    }

    /**
     * Inserts or updates an answer for a specific question within a response.
     *
     * @param responseId the response ID
     * @param questionId the question ID
     * @param value      the answer value as a JSON string
     */
    public void upsertAnswer(int responseId, int questionId, FormAnswerValue value) {
        query("""
                INSERT INTO form_answer(response_id, question_id, value)
                VALUES (:response_id, :question_id, :value::JSONB)
                ON CONFLICT (response_id, question_id) DO UPDATE SET value = :value::JSONB;""")
                .single(call().bind("response_id", responseId)
                        .bind("question_id", questionId)
                        .bind("value", value.toJson()))
                .insert();
    }

    // -- Restrictions --

    /**
     * Updates the restriction mode for a form.
     *
     * @param formId the form ID
     * @param mode   the restriction mode
     */
    public void updateRestrictionMode(int formId, RestrictionMode mode) {
        query("UPDATE form SET restriction_mode = :mode WHERE id = :id;")
                .single(call().bind("mode", mode.name()).bind("id", formId))
                .update()
                .changed();
    }
}
