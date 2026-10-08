/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import dev.chojo.ember.util.sql.SqlSupport;

import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;

/**
 * Which rows already here a station import may name, so a bundle attaches its rows only to what
 * belongs to the station it brings.
 *
 * <p>A bundle is written by whoever runs the source installation, and a station manager may pull one
 * from any address. A lookup by uid or address therefore must not reach every row here that answers to
 * it:
 * <ul>
 *   <li>A station is found only while it runs here, and only where it is the imported station itself or
 *       one of its partners: a station that keeps a partnership with the imported station's uid that is
 *       no longer a pending request. That partnership is the partner's own row, which no bundle writes,
 *       and a pending one may have been asked for by anybody. The copy a station left behind when it
 *       moved away carries the same uid and is never found.</li>
 *   <li>An account is found only where it arrived with the run or belongs to a member of the imported
 *       station.</li>
 * </ul>
 * Rows of every other table are found by their lookup as they stand.
 */
public final class ImportReach {
    private static final String STATION = "station";
    private static final String ACCOUNT = "account";
    private static final String STATION_PARAMETER = "import_station_id";

    private static final String RUNS_HERE = " AND t.moved_away_at IS NULL";

    private static final String IN_REACH = """
            (t.id = :import_station_id
             OR EXISTS (SELECT 1
                        FROM federation_partner p
                                 JOIN station imported ON imported.id = :import_station_id
                        WHERE p.station_id = t.id
                          AND p.partner_station_id = imported.uid
                          AND p.status <> 'PENDING'))""";

    private ImportReach() {}

    /**
     * The condition a lookup into {@code table} adds to its {@code WHERE}, on the table aliased {@code t}.
     *
     * @param table the table looked into
     * @return the condition, starting with {@code AND}, or nothing for a table every row of which may be
     * named
     */
    static String condition(String table) {
        return STATION.equals(table) ? RUNS_HERE + " AND " + IN_REACH : "";
    }

    /**
     * Binds what {@link #condition(String)} asks for.
     *
     * @param table     the table looked into
     * @param call      the lookup's parameters so far
     * @param stationId the imported station
     * @return the parameters
     */
    static Call bind(String table, Call call, int stationId) {
        return STATION.equals(table) ? call.bind(STATION_PARAMETER, stationId) : call;
    }

    /**
     * Whether a row a lookup found may be named by the import, for what {@link #condition(String)} cannot
     * say in the lookup itself.
     *
     * @param table     the table the row is in
     * @param id        the row's id
     * @param stationId the imported station
     * @param idMap     the rows of the run
     * @return whether the import may name it
     */
    static boolean mayName(String table, int id, int stationId, IdRemapper idMap) {
        if (!ACCOUNT.equals(table) || idMap.arrived(ACCOUNT, id)) return true;
        return SqlSupport.exists("""
                SELECT 1 FROM station_member
                WHERE station_id = :station_id AND account_id = :account_id;""", call().bind("station_id", stationId).bind("account_id", id));
    }

    /**
     * Whether a row of an import into a station may name a station by its uid.
     *
     * @param stationId the imported station
     * @param uid       the uid the row names
     * @return {@code true} where no station runs here under that uid, as for a station on another
     * installation, or where the one that does is the imported station or one of its partners
     */
    public static boolean mayNameStation(int stationId, UUID uid) {
        return !SqlSupport.exists(
                "SELECT 1 FROM station t WHERE t.uid = :uid::uuid" + RUNS_HERE + " AND NOT " + IN_REACH + ";",
                call().bind("uid", uid, StandardValueConverter.UUID_STRING).bind(STATION_PARAMETER, stationId));
    }
}
