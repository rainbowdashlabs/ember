/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.feature.station.entity.ApplicationStatus;
import dev.chojo.ember.feature.station.entity.StationApplication;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for station application CRUD and lifecycle operations.
 */
@Singleton
public class StationApplicationRepository {

    private static final String STATION_APPLICATION_COLUMNS =
            "id, first_name, last_name, email, station_name, introduction, verification_token, status, deny_reason, created_at, resolved_at";

    /**
     * Creates a new station application.
     *
     * @param firstName         the applicant's first name
     * @param lastName          the applicant's last name
     * @param email             the applicant's email address
     * @param stationName       the desired station name
     * @param introduction      an optional introduction text
     * @param verificationToken the email verification token
     * @return the created application
     */
    public StationApplication create(
            String firstName,
            String lastName,
            String email,
            String stationName,
            String introduction,
            String verificationToken) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    station_application(first_name, last_name, email, station_name, introduction, verification_token)
                VALUES
                    (:first_name, :last_name, :email, :station_name, :introduction, :verification_token)
                RETURNING %s;""",
                call().bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("email", email)
                        .bind("station_name", stationName)
                        .bind("introduction", introduction != null ? introduction : "")
                        .bind("verification_token", verificationToken),
                StationApplication.map(),
                STATION_APPLICATION_COLUMNS);
    }

    /**
     * Finds an application by its ID.
     *
     * @param id the application ID
     * @return the application, or empty if not found
     */
    public Optional<StationApplication> findById(int id) {
        return SqlSupport.findById("station_application", STATION_APPLICATION_COLUMNS, id, StationApplication.map());
    }

    /**
     * Finds all applications with the given status, ordered by creation date.
     *
     * @param status the status to filter by
     * @return a list of matching applications
     */
    public List<StationApplication> findByStatus(ApplicationStatus status) {
        return query("""
                SELECT
                    %s
                FROM
                    station_application
                WHERE status = :status
                ORDER BY created_at;
                """, STATION_APPLICATION_COLUMNS)
                .single(call().bind("status", status))
                .map(StationApplication.map())
                .all();
    }

    /**
     * Retrieves all applications, ordered by creation date descending.
     *
     * @return a list of all applications
     */
    public List<StationApplication> findAll() {
        return query("""
                SELECT
                    %s
                FROM
                    station_application
                ORDER BY created_at DESC;""", STATION_APPLICATION_COLUMNS)
                .single()
                .map(StationApplication.map())
                .all();
    }

    /**
     * Finds an application by its verification token.
     *
     * @param token the verification token
     * @return the application, or empty if not found
     */
    public Optional<StationApplication> findByToken(String token) {
        return query("""
                SELECT
                    %s
                FROM
                    station_application
                WHERE verification_token = :token;""", STATION_APPLICATION_COLUMNS)
                .single(call().bind("token", token))
                .map(StationApplication.map())
                .first();
    }

    /**
     * Verifies an unverified application, transitioning it to "pending" status.
     *
     * @param id the application ID
     * @return {@code true} if the application was successfully verified
     */
    public boolean verify(int id) {
        return query("""
                UPDATE station_application
                SET
                    status             = 'PENDING',
                    verification_token = NULL
                WHERE id = :id
                  AND status = 'UNVERIFIED';""").single(call().bind("id", id)).update().changed();
    }

    /**
     * Accepts a pending application.
     *
     * @param id the application ID
     * @return {@code true} if the application was successfully accepted
     */
    public boolean accept(int id) {
        return query("""
                UPDATE station_application
                SET
                    status      = 'ACCEPTED',
                    resolved_at = now()
                WHERE id = :id
                  AND status = 'PENDING';""").single(call().bind("id", id)).update().changed();
    }

    /**
     * Denies a pending application with a reason.
     *
     * @param id     the application ID
     * @param reason the reason for denial
     * @return {@code true} if the application was successfully denied
     */
    public boolean deny(int id, String reason) {
        return query("""
                UPDATE station_application
                SET
                    status      = 'DENIED',
                    deny_reason = :reason,
                    resolved_at = now()
                WHERE id = :id
                  AND status = 'PENDING';""")
                .single(call().bind("id", id).bind("reason", reason))
                .update()
                .changed();
    }
}
