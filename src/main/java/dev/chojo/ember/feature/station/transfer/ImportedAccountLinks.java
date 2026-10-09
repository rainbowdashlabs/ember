/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.service.AccountLinkService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.feature.station.transfer.WireValues.asInteger;
import static dev.chojo.ember.feature.station.transfer.WireValues.asString;

/**
 * The members of an import whose account was found here by its address.
 *
 * <p>Such a member arrives without an account, because the bundle may name anybody's address and the
 * person behind it never agreed to join this station. Once the run has settled, each of them gets a
 * link request with origin {@link LinkOrigin#IMPORT}, which the person answers after signing in.
 */
@Singleton
public class ImportedAccountLinks {
    private static final Logger log = LoggerFactory.getLogger(ImportedAccountLinks.class);
    private static final String MEMBERS = "station_member";

    private final AccountLinkService linkService;

    @Inject
    public ImportedAccountLinks(AccountLinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Notes the current members of a page whose account the run found here and left alone. A former
     * member is not worth asking anybody about.
     *
     * @param context the run
     * @param rows    the page's member rows
     */
    public void note(StationImportContext context, List<Map<String, Object>> rows) {
        for (var row : rows) {
            Integer sourceId = asInteger(row.get("id"));
            String email = asString(row.get("account_email"), null);
            if (sourceId == null || email == null || Boolean.TRUE.equals(row.get("former"))) continue;
            Integer accountId = context.foundAccount(email);
            if (accountId != null) context.memberToLink(sourceId, accountId);
        }
    }

    /**
     * Asks the person behind every noted member to link their account, once the members have arrived.
     *
     * @param context the run
     * @return how many requests went out
     */
    public int ask(StationImportContext context) {
        int asked = 0;
        for (var entry : context.membersToLink().entrySet()) {
            Integer memberId = context.idMap().get(MEMBERS, entry.getKey());
            if (memberId == null) continue;
            linkService.ask(context.stationId(), memberId, entry.getValue(), LinkOrigin.IMPORT, null);
            asked++;
        }
        if (asked > 0) {
            log.info(
                    "Station {} import asks {} person(s) to link the account found by their address",
                    context.stationId(),
                    asked);
        }
        return asked;
    }

    /**
     * @param table a table of the bundle
     * @return whether its rows are the members this class notes
     */
    public static boolean isMembers(String table) {
        return MEMBERS.equals(table);
    }
}
