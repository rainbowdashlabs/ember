/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormResponse;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for form-related database operations including forms, questions, responses, answers, and access restrictions.
 */
@Singleton
public class FormRepository {

    // -- Forms --

    /**
     * Retrieves all forms for a station, ordered by creation date descending.
     *
     * @param stationId the station to query
     * @return list of forms belonging to the station
     */
    public List<Form> findByStation(int stationId) {
        return Query.query("SELECT * FROM form WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
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
        return Query.query("SELECT * FROM form WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Form.map())
                .first();
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
            Instant startAt,
            Instant endAt,
            int createdBy) {
        return Query.query("""
                        INSERT INTO form(station_id, title, description, shuffle_questions, allow_edit, start_at, end_at, created_by)
                        VALUES (:station_id, :title, :description, :shuffle_questions, :allow_edit, :start_at, :end_at, :created_by)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("shuffle_questions", shuffleQuestions)
                        .bind("allow_edit", allowEdit)
                        .bind("start_at", startAt, INSTANT_TIMESTAMP)
                        .bind("end_at", endAt, INSTANT_TIMESTAMP)
                        .bind("created_by", createdBy))
                .map(Form.map())
                .first()
                .orElseThrow();
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
            Instant startAt,
            Instant endAt) {
        return Query.query("""
                        UPDATE form
                        SET title = :title, description = :description,
                            shuffle_questions = :shuffle_questions, allow_edit = :allow_edit,
                            start_at = :start_at, end_at = :end_at, updated_at = now()
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("shuffle_questions", shuffleQuestions)
                        .bind("allow_edit", allowEdit)
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
        return Query.query("DELETE FROM form WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Updates the status of a form. When closing, the {@code closed_at} timestamp is set automatically.
     *
     * @param id     the form ID
     * @param status the new status
     * @return {@code true} if a row was updated
     */
    public boolean updateStatus(int id, Form.FormStatus status) {
        return Query.query(
                        "UPDATE form SET status = :status, closed_at = CASE WHEN :status = 'CLOSED' THEN now() ELSE closed_at END, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("status", status.name()))
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
        return Query.query("SELECT * FROM form_question WHERE form_id = :form_id ORDER BY position;")
                .single(Call.of().bind("form_id", formId))
                .map(FormQuestion.map())
                .all();
    }

    /**
     * Creates a new question for a form.
     *
     * @param formId       the form to add the question to
     * @param position     display order position
     * @param questionType the type of question
     * @param title        the question text
     * @param description  optional description
     * @param required     whether an answer is mandatory
     * @param shuffle      whether answer options should be randomized
     * @param config       type-specific configuration as JSON
     * @return the newly created question
     */
    public FormQuestion createQuestion(
            int formId,
            int position,
            FormQuestion.QuestionType questionType,
            String title,
            String description,
            boolean required,
            boolean shuffle,
            String config) {
        return Query.query("""
                        INSERT INTO form_question(form_id, position, question_type, title, description, required, shuffle, config)
                        VALUES (:form_id, :position, :question_type, :title, :description, :required, :shuffle, :config::JSONB)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("form_id", formId)
                        .bind("position", position)
                        .bind("question_type", questionType.name())
                        .bind("title", title)
                        .bind("description", description)
                        .bind("required", required)
                        .bind("shuffle", shuffle)
                        .bind("config", config))
                .map(FormQuestion.map())
                .first()
                .orElseThrow();
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
            int id, String title, String description, boolean required, boolean shuffle, String config, int position) {
        return Query.query("""
                        UPDATE form_question
                        SET title = :title, description = :description, required = :required,
                            shuffle = :shuffle, config = :config::JSONB, position = :position
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("required", required)
                        .bind("shuffle", shuffle)
                        .bind("config", config)
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
        return Query.query("DELETE FROM form_question WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Deletes all questions belonging to a form.
     *
     * @param formId the form ID
     */
    public void deleteQuestionsByForm(int formId) {
        Query.query("DELETE FROM form_question WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
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
        return Query.query("SELECT * FROM form_response WHERE form_id = :form_id ORDER BY submitted_at;")
                .single(Call.of().bind("form_id", formId))
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
        return Query.query("SELECT * FROM form_response WHERE form_id = :form_id AND member_id = :member_id;")
                .single(Call.of().bind("form_id", formId).bind("member_id", memberId))
                .map(FormResponse.map())
                .first();
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
        return Query.query("""
                        INSERT INTO form_response(form_id, member_id, submitted_by)
                        VALUES (:form_id, :member_id, :submitted_by)
                        ON CONFLICT (form_id, member_id) DO UPDATE SET submitted_by = :submitted_by, updated_at = now()
                        RETURNING *;""")
                .single(Call.of()
                        .bind("form_id", formId)
                        .bind("member_id", memberId)
                        .bind("submitted_by", submittedBy))
                .map(FormResponse.map())
                .first()
                .orElseThrow();
    }

    /**
     * Deletes a response by ID.
     *
     * @param responseId the response ID
     * @return {@code true} if a row was deleted
     */
    public boolean deleteResponse(int responseId) {
        return Query.query("DELETE FROM form_response WHERE id = :id;")
                .single(Call.of().bind("id", responseId))
                .delete()
                .changed();
    }

    /**
     * Counts the total number of responses for a form.
     *
     * @param formId the form ID
     * @return the response count
     */
    public int countResponses(int formId) {
        return Query.query("SELECT count(*) AS cnt FROM form_response WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    /**
     * Checks whether a member has already submitted a response to a form.
     *
     * @param formId   the form ID
     * @param memberId the member ID
     * @return {@code true} if the member has responded
     */
    public boolean hasResponded(int formId, int memberId) {
        return Query.query("SELECT 1 FROM form_response WHERE form_id = :form_id AND member_id = :member_id;")
                .single(Call.of().bind("form_id", formId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    // -- Answers --

    /**
     * Retrieves all answers for a specific response.
     *
     * @param responseId the response ID
     * @return list of answers
     */
    public List<FormAnswer> findAnswers(int responseId) {
        return Query.query("SELECT * FROM form_answer WHERE response_id = :response_id;")
                .single(Call.of().bind("response_id", responseId))
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
        return Query.query("SELECT * FROM form_answer WHERE question_id = :question_id;")
                .single(Call.of().bind("question_id", questionId))
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
    public void upsertAnswer(int responseId, int questionId, String value) {
        Query.query("""
                        INSERT INTO form_answer(response_id, question_id, value)
                        VALUES (:response_id, :question_id, :value::JSONB)
                        ON CONFLICT (response_id, question_id) DO UPDATE SET value = :value::JSONB;""")
                .single(Call.of()
                        .bind("response_id", responseId)
                        .bind("question_id", questionId)
                        .bind("value", value))
                .insert();
    }

    // -- Restrictions --

    /**
     * Retrieves the role IDs that restrict access to a form.
     *
     * @param formId the form ID
     * @return list of role IDs, empty if no role restrictions
     */
    public List<Integer> findRoleRestrictions(int formId) {
        return Query.query("SELECT role_id FROM form_role_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("role_id"))
                .all();
    }

    /**
     * Retrieves the group IDs that restrict access to a form.
     *
     * @param formId the form ID
     * @return list of group IDs, empty if no group restrictions
     */
    public List<Integer> findGroupRestrictions(int formId) {
        return Query.query("SELECT group_id FROM form_group_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    /**
     * Retrieves the tag IDs that restrict access to a form.
     *
     * @param formId the form ID
     * @return list of tag IDs, empty if no tag restrictions
     */
    public List<Integer> findTagRestrictions(int formId) {
        return Query.query("SELECT tag_id FROM form_tag_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("tag_id"))
                .all();
    }

    /**
     * Replaces all role restrictions for a form. Deletes existing restrictions first, then inserts new ones.
     *
     * @param formId  the form ID
     * @param roleIds the new set of role IDs to restrict access to
     */
    public void setRoleRestrictions(int formId, List<Integer> roleIds) {
        Query.query("DELETE FROM form_role_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .delete();
        for (int roleId : roleIds) {
            Query.query("INSERT INTO form_role_restriction(form_id, role_id) VALUES(:form_id, :role_id);")
                    .single(Call.of().bind("form_id", formId).bind("role_id", roleId))
                    .insert();
        }
    }

    /**
     * Replaces all group restrictions for a form. Deletes existing restrictions first, then inserts new ones.
     *
     * @param formId   the form ID
     * @param groupIds the new set of group IDs to restrict access to
     */
    public void setGroupRestrictions(int formId, List<Integer> groupIds) {
        Query.query("DELETE FROM form_group_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .delete();
        for (int groupId : groupIds) {
            Query.query("INSERT INTO form_group_restriction(form_id, group_id) VALUES(:form_id, :group_id);")
                    .single(Call.of().bind("form_id", formId).bind("group_id", groupId))
                    .insert();
        }
    }

    /**
     * Replaces all tag restrictions for a form. Deletes existing restrictions first, then inserts new ones.
     *
     * @param formId the form ID
     * @param tagIds the new set of tag IDs to restrict access to
     */
    public void setTagRestrictions(int formId, List<Integer> tagIds) {
        Query.query("DELETE FROM form_tag_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .delete();
        for (int tagId : tagIds) {
            Query.query("INSERT INTO form_tag_restriction(form_id, tag_id) VALUES(:form_id, :tag_id);")
                    .single(Call.of().bind("form_id", formId).bind("tag_id", tagId))
                    .insert();
        }
    }

    /**
     * Retrieves all role restrictions for all forms in a station, grouped by form ID.
     *
     * @param stationId the station ID
     * @return map of form ID to list of restricted role IDs
     */
    public Map<Integer, List<Integer>> findAllRoleRestrictionsByStation(int stationId) {
        var result = new HashMap<Integer, List<Integer>>();
        Query.query("""
                        SELECT frr.form_id, frr.role_id
                        FROM form_role_restriction frr
                        JOIN form f ON f.id = frr.form_id
                        WHERE f.station_id = :station_id;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new int[] {row.getInt("form_id"), row.getInt("role_id")})
                .all()
                .forEach(pair ->
                        result.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]));
        return result;
    }

    /**
     * Retrieves all group restrictions for all forms in a station, grouped by form ID.
     *
     * @param stationId the station ID
     * @return map of form ID to list of restricted group IDs
     */
    public Map<Integer, List<Integer>> findAllGroupRestrictionsByStation(int stationId) {
        var result = new HashMap<Integer, List<Integer>>();
        Query.query("""
                        SELECT fgr.form_id, fgr.group_id
                        FROM form_group_restriction fgr
                        JOIN form f ON f.id = fgr.form_id
                        WHERE f.station_id = :station_id;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new int[] {row.getInt("form_id"), row.getInt("group_id")})
                .all()
                .forEach(pair ->
                        result.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]));
        return result;
    }

    /**
     * Retrieves all tag restrictions for all forms in a station, grouped by form ID.
     *
     * @param stationId the station ID
     * @return map of form ID to list of restricted tag IDs
     */
    public Map<Integer, List<Integer>> findAllTagRestrictionsByStation(int stationId) {
        var result = new HashMap<Integer, List<Integer>>();
        Query.query("""
                        SELECT ftr.form_id, ftr.tag_id
                        FROM form_tag_restriction ftr
                        JOIN form f ON f.id = ftr.form_id
                        WHERE f.station_id = :station_id;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new int[] {row.getInt("form_id"), row.getInt("tag_id")})
                .all()
                .forEach(pair ->
                        result.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]));
        return result;
    }
}
