/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Switches the stations of this instance over to a station that has moved to another instance.
 *
 * <p>Until the move, the two were partners on one instance: nothing between them was signed, a
 * lending request was one row both read, and a borrowed copy named its owner by its row here. Once
 * the destination reports the station imported, each of those becomes its form between two
 * instances:
 *
 * <ul>
 *   <li>Every partnership with the moved station points at its new address and verifies it with
 *       the key it signs with, which is the key the copy left here holds, since the station took it
 *       along. The contract is this build's, which the destination runs as well: a station only moves
 *       between instances of one schema. The destination learns the partners' keys from the station
 *       page ({@link PartnersLeftBehind}), so no second pairing is made.
 *   <li>Every shared lending request becomes the partner's copy ({@link LendingRepository#leaveBehind}).
 *   <li>Gear that names the moved station's row here lets go of it
 *       ({@link InventoryRepository#forgetMovedStation}), so deleting that row later takes nothing of
 *       the partners' with it.
 * </ul>
 */
@Singleton
public class MovedStationSwitchover {
    private static final Logger log = LoggerFactory.getLogger(MovedStationSwitchover.class);

    private final FederationRepository federationRepository;
    private final FederationPartnerTransferFixupService fixup;
    private final StationKeyStore keys;
    private final LendingRepository lendingRepository;
    private final InventoryRepository inventoryRepository;

    @Inject
    public MovedStationSwitchover(
            FederationRepository federationRepository,
            FederationPartnerTransferFixupService fixup,
            StationKeyStore keys,
            LendingRepository lendingRepository,
            InventoryRepository inventoryRepository) {
        this.federationRepository = federationRepository;
        this.fixup = fixup;
        this.keys = keys;
        this.lendingRepository = lendingRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Switches every station of this instance that dealt with the moved station over to it at its new
     * address.
     *
     * @param moved          the copy the station left here
     * @param destinationUrl the instance it moved to, or {@code null} where none is known, which leaves
     *                       the partnerships where they are and still lets go of the copy
     */
    public void switchOver(Station moved, @Nullable String destinationUrl) {
        Transactions.run(() -> {
            int partnerships = keyPartnerships(moved, destinationUrl);
            fixup.flipSourceSideRetainedPartners(moved.uid(), destinationUrl);
            int requests = lendingRepository.leaveBehind(moved.uid());
            int pieces = inventoryRepository.forgetMovedStation(moved.id());
            log.info(
                    "Station {} moved to {}: {} partnership(s) verify it there, {} lending request(s) and {} piece(s) of gear here let go of its copy",
                    moved.uid(),
                    destinationUrl == null ? "<unknown>" : destinationUrl,
                    partnerships,
                    requests,
                    pieces);
        });
    }

    /**
     * Gives every active partnership a station here holds with the moved station the key it signs
     * with, its name and this build's contract, while the partnership is still one on this instance.
     *
     * @return how many partnerships were given the key
     */
    private int keyPartnerships(Station moved, @Nullable String destinationUrl) {
        if (destinationUrl == null || destinationUrl.isBlank()) return 0;
        var publicKey = keys.privateKey(moved.id()).map(StationKeyStore::publicKeyOf);
        if (publicKey.isEmpty()) {
            log.warn("Station {} moved away without a federation key; its partners here cannot verify it", moved.uid());
            return 0;
        }
        int keyed = 0;
        for (FederationPartner partnership : federationRepository.findLocalPartnershipsWith(moved.uid())) {
            federationRepository.adoptPartnerKey(partnership.id(), publicKey.get(), moved.name());
            federationRepository.updateFederationContract(partnership.id(), FederationContractVersions.current());
            keyed++;
        }
        return keyed;
    }
}
