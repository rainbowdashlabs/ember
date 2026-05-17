/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.Form;
import dev.chojo.ember.entity.FormAnswer;
import dev.chojo.ember.entity.FormQuestion;
import dev.chojo.ember.entity.FormResponse;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class FormRepository {

    // -- Forms --

    public List<Form> findByStation(int stationId) {
        return Query.query("SELECT * FROM form WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(Form.map())
                .all();
    }

    public Optional<Form> findById(int id) {
        return Query.query("SELECT * FROM form WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Form.map())
                .first();
    }

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

    public boolean delete(int id) {
        return Query.query("DELETE FROM form WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public boolean updateStatus(int id, String status) {
        return Query.query(
                        "UPDATE form SET status = :status, closed_at = CASE WHEN :status = 'CLOSED' THEN now() ELSE closed_at END, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("status", status))
                .update()
                .changed();
    }

    // -- Questions --

    public List<FormQuestion> findQuestions(int formId) {
        return Query.query("SELECT * FROM form_question WHERE form_id = :form_id ORDER BY position;")
                .single(Call.of().bind("form_id", formId))
                .map(FormQuestion.map())
                .all();
    }

    public FormQuestion createQuestion(
            int formId,
            int position,
            String questionType,
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
                        .bind("question_type", questionType)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("required", required)
                        .bind("shuffle", shuffle)
                        .bind("config", config))
                .map(FormQuestion.map())
                .first()
                .orElseThrow();
    }

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

    public boolean deleteQuestion(int id) {
        return Query.query("DELETE FROM form_question WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public void deleteQuestionsByForm(int formId) {
        Query.query("DELETE FROM form_question WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .delete();
    }

    // -- Responses --

    public List<FormResponse> findResponses(int formId) {
        return Query.query("SELECT * FROM form_response WHERE form_id = :form_id ORDER BY submitted_at;")
                .single(Call.of().bind("form_id", formId))
                .map(FormResponse.map())
                .all();
    }

    public Optional<FormResponse> findResponse(int formId, int memberId) {
        return Query.query("SELECT * FROM form_response WHERE form_id = :form_id AND member_id = :member_id;")
                .single(Call.of().bind("form_id", formId).bind("member_id", memberId))
                .map(FormResponse.map())
                .first();
    }

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

    public boolean deleteResponse(int responseId) {
        return Query.query("DELETE FROM form_response WHERE id = :id;")
                .single(Call.of().bind("id", responseId))
                .delete()
                .changed();
    }

    public int countResponses(int formId) {
        return Query.query("SELECT count(*) AS cnt FROM form_response WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public boolean hasResponded(int formId, int memberId) {
        return Query.query("SELECT 1 FROM form_response WHERE form_id = :form_id AND member_id = :member_id;")
                .single(Call.of().bind("form_id", formId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    // -- Answers --

    public List<FormAnswer> findAnswers(int responseId) {
        return Query.query("SELECT * FROM form_answer WHERE response_id = :response_id;")
                .single(Call.of().bind("response_id", responseId))
                .map(FormAnswer.map())
                .all();
    }

    public List<FormAnswer> findAllAnswersForQuestion(int questionId) {
        return Query.query("SELECT * FROM form_answer WHERE question_id = :question_id;")
                .single(Call.of().bind("question_id", questionId))
                .map(FormAnswer.map())
                .all();
    }

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

    public List<Integer> findRoleRestrictions(int formId) {
        return Query.query("SELECT role_id FROM form_role_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("role_id"))
                .all();
    }

    public List<Integer> findGroupRestrictions(int formId) {
        return Query.query("SELECT group_id FROM form_group_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    public List<Integer> findTagRestrictions(int formId) {
        return Query.query("SELECT tag_id FROM form_tag_restriction WHERE form_id = :form_id;")
                .single(Call.of().bind("form_id", formId))
                .map(row -> row.getInt("tag_id"))
                .all();
    }

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
                .forEach(pair -> result.computeIfAbsent(pair[0], k -> new java.util.ArrayList<>())
                        .add(pair[1]));
        return result;
    }

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
                .forEach(pair -> result.computeIfAbsent(pair[0], k -> new java.util.ArrayList<>())
                        .add(pair[1]));
        return result;
    }

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
                .forEach(pair -> result.computeIfAbsent(pair[0], k -> new java.util.ArrayList<>())
                        .add(pair[1]));
        return result;
    }
}
