/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;

/**
 * How a station uses a template of its association: whether its members generate it through self
 * service, how the parts of the audience it chose for that combine, and who of its members issues the
 * documents.
 *
 * @param id              the identifier its audience is kept under
 * @param templateId      the template of the association
 * @param stationId       the station
 * @param selfService     whether the station offers it to its members for self service
 * @param restrictionMode how the parts of the station's audience combine
 * @param issuerId        the member of the station who issues its documents there, or null for nobody
 * @param issuerFunction  what the issuer does at the station, or null where nothing is said
 */
public record TemplateStationUse(
        int id,
        int templateId,
        int stationId,
        boolean selfService,
        RestrictionMode restrictionMode,
        @Nullable Integer issuerId,
        @Nullable String issuerFunction) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS =
            "id, template_id, station_id, self_service, restriction_mode, issuer_id, issuer_function";

    /** @return the issuer the station names for the template */
    public DocumentIssuer issuer() {
        return DocumentIssuer.ofTemplate(issuerId, issuerFunction);
    }

    public static RowMapping<TemplateStationUse> map() {
        return row -> new TemplateStationUse(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getInt("station_id"),
                row.getBoolean("self_service"),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getObject("issuer_id", Integer.class),
                row.getString("issuer_function"));
    }
}
