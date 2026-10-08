/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfInspection;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.PdfOriginal;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The uploaded PDFs of PDF templates and what is laid over them.
 *
 * <p>Every upload is a row of its own and none is ever deleted: a generated document names the
 * version it was filled from. The template points at the one it fills now.
 */
@Singleton
public class PdfTemplateRepository {

    /**
     * Writes a newly uploaded version of a template's PDF and makes it the one the template fills.
     *
     * @param templateId the template
     * @param fileName   the name the file was uploaded under
     * @param sizeBytes  its size
     * @param sha256     its SHA-256 as lowercase hex
     * @param inspection its pages and form fields
     * @param uploadedBy the member who uploaded it
     * @return the original as written
     */
    public PdfOriginal addOriginal(
            int templateId, String fileName, long sizeBytes, String sha256, PdfInspection inspection, int uploadedBy) {
        var original = SqlSupport.insertReturning(
                """
                        INSERT INTO document_template_pdf_original(template_id, file_name, size_bytes, sha256,
                                                                   inspection, uploaded_by)
                        VALUES (:template_id, :file_name, :size_bytes, :file_hash, :inspection::jsonb, :uploaded_by)
                        RETURNING %s;""",
                call().bind("template_id", templateId)
                        .bind("file_name", fileName)
                        .bind("size_bytes", sizeBytes)
                        .bind("file_hash", sha256)
                        .bind("inspection", inspection.toJson())
                        .bind("uploaded_by", uploadedBy),
                PdfOriginal.map(),
                PdfOriginal.COLUMNS);
        query("""
                INSERT INTO document_template_pdf(template_id, original_id)
                VALUES (:template_id, :original_id)
                ON CONFLICT (template_id) DO UPDATE SET original_id = excluded.original_id;""")
                .single(call().bind("template_id", templateId).bind("original_id", original.id()))
                .insert();
        return original;
    }

    /**
     * @param templateId the template
     * @return the version of its PDF it fills now, or empty where none was uploaded
     */
    public Optional<PdfOriginal> findCurrentOriginal(int templateId) {
        return query("""
                SELECT %s
                FROM document_template_pdf_original
                WHERE id = (SELECT original_id FROM document_template_pdf WHERE template_id = :template_id);""", PdfOriginal.COLUMNS)
                .single(call().bind("template_id", templateId))
                .map(PdfOriginal.map())
                .first();
    }

    /**
     * Replaces what is laid over a template's PDF.
     *
     * @param templateId the template
     * @param layout     the fields and the form fields' values
     */
    public void writeLayout(int templateId, PdfLayout layout) {
        query("DELETE FROM document_template_field WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        query("DELETE FROM document_template_form_binding WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        var fields = layout.fields();
        for (int position = 0; position < fields.size(); position++) {
            writeField(templateId, position, fields.get(position));
        }
        for (var binding : layout.bindings()) {
            query("""
                    INSERT INTO document_template_form_binding(template_id, field_name, text)
                    VALUES (:template_id, :field_name, :text);""")
                    .single(call().bind("template_id", templateId)
                            .bind("field_name", binding.fieldName())
                            .bind("text", binding.text()))
                    .insert();
        }
    }

    private void writeField(int templateId, int position, PdfField field) {
        var rect = field.rect();
        var role = field.role();
        query("""
                INSERT INTO document_template_field(template_id, position, kind, page, x, y, width, height, text,
                                                    font_size, align, wrap, role, font_family, font_style,
                                                    without_line, print_text, statement)
                VALUES (:template_id, :position, :kind, :page, :x, :y, :width, :height, :text,
                        :font_size, :align, :wrap, :role, :font_family, :font_style, :without_line, :print_text,
                        :statement);""")
                .single(call().bind("template_id", templateId)
                        .bind("position", position)
                        .bind("kind", field.kind())
                        .bind("page", rect.page())
                        .bind("x", rect.x())
                        .bind("y", rect.y())
                        .bind("width", rect.width())
                        .bind("height", rect.height())
                        .bind("text", field.text())
                        .bind("font_size", field.fontSize())
                        .bind("align", field.align())
                        .bind("wrap", field.wrap())
                        .bind("role", role == null ? null : role.name())
                        .bind("font_family", field.fontFamily())
                        .bind("font_style", Objects.requireNonNullElse(field.fontStyle(), FontStyle.REGULAR))
                        .bind("without_line", field.withoutLine())
                        .bind("print_text", field.printText())
                        .bind("statement", field.statement()))
                .insert();
    }

    /**
     * @param templateId the template
     * @return the fields on its pages in the editor's order, and the form fields' values by name
     */
    public PdfLayout findLayout(int templateId) {
        List<PdfField> fields = query("""
                        SELECT %s
                        FROM document_template_field
                        WHERE template_id = :template_id
                        ORDER BY position, id;""", PdfField.COLUMNS)
                .single(call().bind("template_id", templateId))
                .map(PdfField.map())
                .all();
        List<FormBinding> bindings = query("""
                        SELECT field_name, text
                        FROM document_template_form_binding
                        WHERE template_id = :template_id
                        ORDER BY field_name;""")
                .single(call().bind("template_id", templateId))
                .map(FormBinding.map())
                .all();
        return new PdfLayout(fields, bindings);
    }
}
