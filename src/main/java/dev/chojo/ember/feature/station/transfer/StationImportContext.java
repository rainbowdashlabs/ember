/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import dev.chojo.ember.tracking.engine.WaitingRows;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * State of a single import run: the destination station, the source-to-destination id remapping
 * every table importer contributes to, the rows still waiting for a row they name, the lending
 * requests written under a stand-in uid with the two stations of every lending request the run took,
 * the accounts this run created, and the accounts already here that it found by their address and
 * asks their owners to link instead of attaching them. The run is executed on one thread, so no
 * synchronization is needed.
 */
public final class StationImportContext {
    private final int stationId;
    private final IdRemapper idMap;
    private final WaitingRows waitingRows = new WaitingRows();
    private final Map<UUID, UUID> lendingStandIns = new LinkedHashMap<>();
    private final Map<Integer, Set<UUID>> lendingParties = new HashMap<>();
    private final List<NewAccountRef> newAccounts = new ArrayList<>();
    private final Set<Integer> createdAccountIds = new HashSet<>();
    private final Map<String, Integer> foundAccounts = new HashMap<>();
    private final Map<Integer, Integer> membersToLink = new LinkedHashMap<>();

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
     * @return the rows of this run that wait for a row they name
     */
    public WaitingRows waitingRows() {
        return waitingRows;
    }

    /**
     * @return the lending requests of this run written under a stand-in uid, each with the uid of the
     * partner's copy already here that it is merged into once the run has settled
     */
    public Map<UUID, UUID> lendingStandIns() {
        return lendingStandIns;
    }

    /**
     * Records the two stations of a lending request the run took, so its messages can be held to them.
     *
     * @param sourceRequestId the request's id at the source
     * @param requesting      the station asking
     * @param owning          the station lending
     */
    public void recordLendingParties(int sourceRequestId, UUID requesting, UUID owning) {
        lendingParties.put(sourceRequestId, Set.of(requesting, owning));
    }

    /**
     * @param sourceRequestId a lending request's id at the source
     * @return the two stations of that request, empty where the run did not take it
     */
    public Set<UUID> lendingPartiesOf(int sourceRequestId) {
        return lendingParties.getOrDefault(sourceRequestId, Set.of());
    }

    /**
     * Records an account this run created, which is the only kind a password may arrive for.
     *
     * @param accountId the account's id here
     */
    public void accountCreated(int accountId) {
        createdAccountIds.add(accountId);
    }

    /**
     * @param accountId an account's id here
     * @return whether this run created it
     */
    public boolean createdAccount(int accountId) {
        return createdAccountIds.contains(accountId);
    }

    /**
     * Records an account here that a row of the bundle names by its address. The run does not attach it
     * to anything: its members arrive without an account and the person is asked to link it.
     *
     * @param email     the address the row carried
     * @param accountId the account here that carries it
     */
    public void accountFound(String email, int accountId) {
        foundAccounts.put(email.toLowerCase(Locale.ROOT), accountId);
    }

    /**
     * @param email an address a row of the run carries
     * @return the account here the run found by it and left alone, or null where it found none
     */
    public @Nullable Integer foundAccount(String email) {
        return foundAccounts.get(email.toLowerCase(Locale.ROOT));
    }

    /**
     * Records a member of the bundle whose account was found here, so the person can be asked once the
     * member has arrived.
     *
     * @param sourceMemberId the member's id at the source
     * @param accountId      the account here
     */
    public void memberToLink(int sourceMemberId, int accountId) {
        membersToLink.put(sourceMemberId, accountId);
    }

    /**
     * @return the members of the run whose account was found here, by their id at the source, with the
     * account each would be linked to
     */
    public Map<Integer, Integer> membersToLink() {
        return membersToLink;
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
