/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The questions a template asks the person generating a document.
 */
@Singleton
public class TemplateQuestionRepository {

    /**
     * @param templateId the template
     * @return its questions in the order they are asked
     */
    public List<TemplateQuestion> findByTemplate(int templateId) {
        return query("""
                        SELECT %s
                        FROM document_template_question
                        WHERE template_id = :template_id
                        ORDER BY position, id;""", TemplateQuestion.COLUMNS)
                .single(call().bind("template_id", templateId))
                .map(TemplateQuestion.map())
                .all();
    }

    /**
     * Replaces the questions of a template.
     *
     * @param templateId the template
     * @param questions  its questions in the order they are asked
     */
    public void write(int templateId, List<TemplateQuestion> questions) {
        query("DELETE FROM document_template_question WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (int position = 0; position < questions.size(); position++) {
            var question = questions.get(position);
            query("""
                    INSERT INTO document_template_question(template_id, position, key, label, kind, suggestion,
                                                           required, limits)
                    VALUES (:template_id, :position, :key, :label, :kind, :suggestion, :required,
                            :limits::jsonb);""")
                    .single(call().bind("template_id", templateId)
                            .bind("position", position)
                            .bind("key", question.key())
                            .bind("label", question.label())
                            .bind("kind", question.kind())
                            .bind("suggestion", question.suggestion())
                            .bind("required", question.required())
                            .bind("limits", question.limits().toJson()))
                    .insert();
        }
    }
}
