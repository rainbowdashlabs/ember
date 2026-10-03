/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

/**
 * How a station uses a template of its association: whether its members generate it through self
 * service, and how the parts of the audience it chose for that combine.
 *
 * @param id              the identifier its audience is kept under
 * @param templateId      the template of the association
 * @param stationId       the station
 * @param selfService     whether the station offers it to its members for self service
 * @param restrictionMode how the parts of the station's audience combine
 */
public record TemplateStationUse(
        int id, int templateId, int stationId, boolean selfService, RestrictionMode restrictionMode) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = "id, template_id, station_id, self_service, restriction_mode";

    public static RowMapping<TemplateStationUse> map() {
        return row -> new TemplateStationUse(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getInt("station_id"),
                row.getBoolean("self_service"),
                row.getEnum("restriction_mode", RestrictionMode.class));
    }
}
