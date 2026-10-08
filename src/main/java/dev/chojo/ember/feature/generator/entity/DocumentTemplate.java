/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.owner.Owner;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A template turned into a PDF for one member at a time, and how the result is filed.
 *
 * <p>A station keeps templates for itself; an association keeps templates its stations use but do not
 * change. Either way a document is filed at the station of the member it is about.
 *
 * @param id               the template identifier
 * @param owner            the station or the association that keeps it
 * @param kind             what it is made of
 * @param name             what it is called in the list of templates
 * @param titlePattern     the title a generated document is filed under, with placeholders
 * @param fileNamePattern  the file name a generated document is filed under, with placeholders
 * @param tags             the document tags a generated document is filed with
 * @param hidden           whether a document a manager generates is hidden from the member
 * @param keepOnArchive    whether a generated document outlasts the membership
 * @param legal            whether it makes a legal document, which uses official names only
 * @param forAppointments  whether appointments may require it as a document to bring
 * @param selfService     whether members may generate it for themselves and their children
 * @param cooldownDays     how many days a member waits before generating it again through self service
 * @param restrictionMode  how the parts of the self service audience combine
 * @param language         the language its documents are written in
 * @param issuerId         the member of the station who issues its documents, or null where it names
 *                         nobody, for a template of an association, and once that member was deleted
 * @param issuerFunction   what the issuer does at the station, or null where nothing is said
 * @param version          counts up with every change
 * @param createdAt        when it was created
 * @param updatedAt        when it was last changed
 * @param archivedAt       when it was archived, or null while it is in use
 * @param signing          how its documents are kept and sent once they are signed
 */
public record DocumentTemplate(
        int id,
        Owner owner,
        DocumentTemplateKind kind,
        String name,
        String titlePattern,
        String fileNamePattern,
        List<String> tags,
        boolean hidden,
        boolean keepOnArchive,
        boolean legal,
        boolean forAppointments,
        boolean selfService,
        int cooldownDays,
        RestrictionMode restrictionMode,
        DocumentLanguage language,
        @Nullable Integer issuerId,
        @Nullable String issuerFunction,
        int version,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant archivedAt,
        TemplateSigning signing) {

    /**
     * How many months a new legal template keeps the signatures asked for on its documents, their evidence
     * and the signed document after the member has gone. Claims about a signed document fall due within
     * the three years of the regular limitation period (section 195 BGB), which only starts at the end of
     * the year they arose in (section 199 BGB), so four years cover a claim from the member's last year.
     * Any other template keeps them only while the member is a member, since its documents are not kept
     * as evidence.
     */
    public static final int LEGAL_SIGNATURE_RETENTION_MONTHS = 48;

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = """
            id, station_id, cluster_id, kind, name, title_pattern, file_name_pattern, tags, hidden, keep_on_archive,
            legal, for_appointments, self_service, self_service_cooldown_days, restriction_mode, language, issuer_id,
            issuer_function, version, created_at, updated_at, archived_at, signature_retention_months,
            signed_copy_attached""";

    /** @return the issuer the template names itself, nobody for a template of an association */
    public DocumentIssuer issuer() {
        return DocumentIssuer.ofTemplate(issuerId, issuerFunction);
    }

    /** Whether the template is archived and generates nothing more. */
    public boolean archived() {
        return archivedAt != null;
    }

    /** Whether an association keeps the template for its stations. */
    public boolean ofAssociation() {
        return owner instanceof Owner.Association;
    }

    public static RowMapping<DocumentTemplate> map() {
        return row -> new DocumentTemplate(
                row.getInt("id"),
                ownerOf(row.getObject("station_id", Integer.class), row.getObject("cluster_id", Integer.class)),
                row.getEnum("kind", DocumentTemplateKind.class),
                row.getString("name"),
                row.getString("title_pattern"),
                row.getString("file_name_pattern"),
                List.of((String[]) row.getArray("tags").getArray()),
                row.getBoolean("hidden"),
                row.getBoolean("keep_on_archive"),
                row.getBoolean("legal"),
                row.getBoolean("for_appointments"),
                row.getBoolean("self_service"),
                row.getInt("self_service_cooldown_days"),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getEnum("language", DocumentLanguage.class),
                row.getObject("issuer_id", Integer.class),
                row.getString("issuer_function"),
                row.getInt("version"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.get("archived_at", INSTANT_TIMESTAMP),
                new TemplateSigning(
                        row.getObject("signature_retention_months", Integer.class),
                        row.getBoolean("signed_copy_attached")));
    }

    /**
     * The owner a template row names, a station where it names one and the association otherwise, since
     * the table holds exactly one of the two.
     *
     * @param stationId the row's station, or null
     * @param clusterId the row's association, or null
     * @return the owner
     */
    public static Owner ownerOf(@Nullable Integer stationId, @Nullable Integer clusterId) {
        if (stationId != null) return new Owner.Station(stationId);
        return new Owner.Association(Objects.requireNonNull(clusterId, "a template names a station or a cluster"));
    }
}
