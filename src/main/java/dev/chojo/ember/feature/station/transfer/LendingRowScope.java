/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.tracking.engine.ImportReach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.feature.station.transfer.WireValues.asInteger;
import static dev.chojo.ember.feature.station.transfer.WireValues.asUuid;

/**
 * Holds the lending rows of an import to the station it brings, since they name stations by uid and the
 * stations named there see them.
 *
 * <p>A lending request arrives only where the imported station is one of its two stations, and the other
 * one is either not on this installation or one the import may name ({@link ImportReach}). A message
 * arrives only where its request arrived in the same run and its sender is one of that request's two
 * stations. Every row held back is counted in the log.
 */
public final class LendingRowScope {
    private static final Logger log = LoggerFactory.getLogger(LendingRowScope.class);

    private LendingRowScope() {}

    /**
     * The lending requests of a page the imported station is a party to, each recorded with its two
     * stations for the messages that follow.
     *
     * @param context     the run
     * @param importedUid the uid the imported station carries here
     * @param rows        the page's rows
     * @return the rows that may arrive
     */
    public static List<Map<String, Object>> requests(
            StationImportContext context, UUID importedUid, List<Map<String, Object>> rows) {
        var kept = rows.stream()
                .filter(row -> namesTheStation(context, importedUid, row))
                .toList();
        report(rows.size() - kept.size(), "lending request(s) that are not the imported station's");
        return kept;
    }

    /**
     * The lending messages of a page sent by one of the two stations of a request the run took.
     *
     * @param context the run
     * @param rows    the page's rows
     * @return the rows that may arrive
     */
    public static List<Map<String, Object>> messages(StationImportContext context, List<Map<String, Object>> rows) {
        var kept = rows.stream().filter(row -> fromAParty(context, row)).toList();
        report(rows.size() - kept.size(), "lending message(s) not sent by a station of their request");
        return kept;
    }

    private static boolean namesTheStation(StationImportContext context, UUID importedUid, Map<String, Object> row) {
        UUID requesting = asUuid(row.get("requesting_station_uid"));
        UUID owning = asUuid(row.get("owning_station_uid"));
        Integer sourceId = asInteger(row.get("id"));
        if (requesting == null || owning == null || sourceId == null) return false;
        UUID other;
        if (importedUid.equals(requesting)) {
            other = owning;
        } else if (importedUid.equals(owning)) {
            other = requesting;
        } else {
            return false;
        }
        if (!other.equals(importedUid) && !ImportReach.mayNameStation(context.stationId(), other)) return false;
        context.recordLendingParties(sourceId, requesting, owning);
        return true;
    }

    private static boolean fromAParty(StationImportContext context, Map<String, Object> row) {
        Integer requestId = asInteger(row.get("request_id"));
        UUID sender = asUuid(row.get("sender_station_uid"));
        if (requestId == null || sender == null) return false;
        return context.lendingPartiesOf(requestId).stream().anyMatch(party -> Objects.equals(party, sender));
    }

    private static void report(int heldBack, String what) {
        if (heldBack > 0) log.warn("{} {} not imported", heldBack, what);
    }
}
