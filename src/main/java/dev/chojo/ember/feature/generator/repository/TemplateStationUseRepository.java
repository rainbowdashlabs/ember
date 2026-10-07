/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.TemplateStationUse;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * How stations use the templates of their association: one row per station and template, written the
 * first time a station decides anything about it.
 */
@Singleton
public class TemplateStationUseRepository {

    /**
     * @param templateId a template of an association
     * @param stationId  a station of it
     * @return how the station uses it, or empty where it never decided
     */
    public Optional<TemplateStationUse> find(int templateId, int stationId) {
        return query("""
                SELECT %s
                FROM document_template_station_use
                WHERE template_id = :template_id
                  AND station_id = :station_id;""", TemplateStationUse.COLUMNS)
                .single(call().bind("template_id", templateId).bind("station_id", stationId))
                .map(TemplateStationUse.map())
                .first();
    }

    /**
     * @param stationId a station
     * @return how it uses every template of its association it decided about
     */
    public List<TemplateStationUse> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM document_template_station_use
                WHERE station_id = :station_id;""", TemplateStationUse.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(TemplateStationUse.map())
                .all();
    }

    /**
     * Writes how a station uses a template of its association, replacing what it decided before.
     *
     * @param templateId      the template
     * @param stationId       the station
     * @param selfService     whether its members generate it through self service
     * @param restrictionMode how the parts of its audience combine
     * @param issuer          who of its members issues the documents, and what they do
     * @return the row as written
     */
    public TemplateStationUse write(
            int templateId,
            int stationId,
            boolean selfService,
            RestrictionMode restrictionMode,
            DocumentIssuer issuer) {
        return SqlSupport.insertReturning(
                """
                        INSERT INTO document_template_station_use(template_id, station_id, self_service, restriction_mode,
                                                                  issuer_id, issuer_function)
                        VALUES (:template_id, :station_id, :self_service, :restriction_mode, :issuer_id, :issuer_function)
                        ON CONFLICT (template_id, station_id) DO UPDATE
                            SET self_service     = excluded.self_service,
                                restriction_mode = excluded.restriction_mode,
                                issuer_id        = excluded.issuer_id,
                                issuer_function  = excluded.issuer_function,
                                updated_at       = now()
                        RETURNING %s;""",
                call().bind("template_id", templateId)
                        .bind("station_id", stationId)
                        .bind("self_service", selfService)
                        .bind("restriction_mode", restrictionMode)
                        .bind("issuer_id", issuer.memberId())
                        .bind("issuer_function", issuer.function()),
                TemplateStationUse.map(),
                TemplateStationUse.COLUMNS);
    }
}
