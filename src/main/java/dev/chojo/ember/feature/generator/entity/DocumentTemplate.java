/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A template a station turns into a PDF for one member at a time, and how the result is filed.
 *
 * @param id               the template identifier
 * @param stationId        the station that owns it
 * @param kind             what it is made of
 * @param name             what it is called in the list of templates
 * @param titlePattern     the title a generated document is filed under, with placeholders
 * @param fileNamePattern  the file name a generated document is filed under, with placeholders
 * @param tags             the document tags a generated document is filed with
 * @param hidden           whether a document a manager generates is hidden from the member
 * @param keepOnArchive    whether a generated document outlasts the membership
 * @param legal            whether it makes a legal document, which uses official names only
 * @param selfService      whether members may generate it for themselves and their children
 * @param cooldownDays     how many days a member waits before generating it again through self service
 * @param restrictionMode  how the parts of the self service audience combine
 * @param pronounSource    the field the pronouns follow, or null where they always use the first name
 * @param version          counts up with every change
 * @param createdAt        when it was created
 * @param updatedAt        when it was last changed
 * @param archivedAt       when it was archived, or null while it is in use
 */
public record DocumentTemplate(
        int id,
        int stationId,
        DocumentTemplateKind kind,
        String name,
        String titlePattern,
        String fileNamePattern,
        List<String> tags,
        boolean hidden,
        boolean keepOnArchive,
        boolean legal,
        boolean selfService,
        int cooldownDays,
        RestrictionMode restrictionMode,
        @Nullable PronounSource pronounSource,
        int version,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant archivedAt) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = """
            id, station_id, kind, name, title_pattern, file_name_pattern, tags, hidden, keep_on_archive, legal,
            self_service, self_service_cooldown_days, restriction_mode, pronoun_field_id,
            pronoun_mapping::text AS pronoun_mapping, version, created_at, updated_at, archived_at""";

    /** Whether the template is archived and generates nothing more. */
    public boolean archived() {
        return archivedAt != null;
    }

    public static RowMapping<DocumentTemplate> map() {
        return row -> new DocumentTemplate(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getEnum("kind", DocumentTemplateKind.class),
                row.getString("name"),
                row.getString("title_pattern"),
                row.getString("file_name_pattern"),
                List.of((String[]) row.getArray("tags").getArray()),
                row.getBoolean("hidden"),
                row.getBoolean("keep_on_archive"),
                row.getBoolean("legal"),
                row.getBoolean("self_service"),
                row.getInt("self_service_cooldown_days"),
                row.getEnum("restriction_mode", RestrictionMode.class),
                PronounSource.of(row.getObject("pronoun_field_id", Integer.class), row.getString("pronoun_mapping")),
                row.getInt("version"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.get("archived_at", INSTANT_TIMESTAMP));
    }
}
