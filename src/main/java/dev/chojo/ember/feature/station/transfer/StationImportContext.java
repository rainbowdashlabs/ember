/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * State of a single import run: the destination station, the source-to-destination id remapping
 * every table importer contributes to, and the accounts this run created. The run is executed on
 * one thread, so no synchronization is needed.
 */
public final class StationImportContext {
    private final int stationId;
    private final IdRemapper idMap;
    private final List<NewAccountRef> newAccounts = new ArrayList<>();

    /**
     * @param stationId the destination station id
     * @param idMap     the shared source-id to destination-id remapping
     */
    public StationImportContext(int stationId, IdRemapper idMap) {
        this.stationId = stationId;
        this.idMap = idMap;
    }

    public int stationId() {
        return stationId;
    }

    public IdRemapper idMap() {
        return idMap;
    }

    /**
     * Records an account that did not exist on the destination before this run, so its avatar can
     * be carried over once all tables are in.
     *
     * @param sourceUid      the account UUID on the source instance
     * @param destinationUid the account UUID on this instance
     */
    public void addNewAccount(UUID sourceUid, UUID destinationUid) {
        newAccounts.add(new NewAccountRef(sourceUid, destinationUid));
    }

    /**
     * @return the accounts created during this run
     */
    public List<NewAccountRef> newAccounts() {
        return newAccounts;
    }

    /**
     * Pairs the source's account UID with the destination UID created for the same account.
     */
    public record NewAccountRef(UUID sourceUid, UUID destinationUid) {}
}
