/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * The one copy of a document an appointment asks for that the members of partner stations sign alike on a
 * date, as the station holding the appointment drew it.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param id              the copy
 * @param eventId         the appointment
 * @param eventDate       the date
 * @param templateId      the template it was drawn from
 * @param templateVersion the version of the template
 * @param title           the title it carries
 * @param fileName        the file name it is handed out under
 * @param content         the PDF
 * @param sha256          SHA-256 of the PDF, lower-case hexadecimal
 */
public record HandedOutAgreement(
        int id,
        int eventId,
        LocalDate eventDate,
        int templateId,
        int templateVersion,
        String title,
        String fileName,
        byte[] content,
        String sha256) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS =
            "id, event_id, event_date, template_id, template_version, title, file_name, content, sha256";

    public static RowMapping<HandedOutAgreement> map() {
        return row -> new HandedOutAgreement(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getObject("event_date", LocalDate.class),
                row.getInt("template_id"),
                row.getInt("template_version"),
                row.getString("title"),
                row.getString("file_name"),
                row.getBytes("content"),
                row.getString("sha256"));
    }
}
