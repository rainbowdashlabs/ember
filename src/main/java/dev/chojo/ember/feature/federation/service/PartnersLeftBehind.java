/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carries the partners a moving station leaves behind on its source instance, so the station knows
 * them as partners on another instance when it arrives.
 *
 * <p>Between two stations of one instance nothing is signed, so the rows of such a partnership hold
 * no key either side could verify the other with. Once one of the two has moved, every request
 * between them is signed and each side needs the other's public key. The source finds the moved
 * station's in the key it still holds ({@link MovedStationSwitchover}). The destination learns each
 * partner's from the station page, which travels under the transfer token the operator carried from
 * one instance to the other by hand, the channel the station's own key travels on
 * ({@link StationKeyTransfer}). No second pairing is made: the partnership rows exist on both sides
 * already and only lack the keys.
 *
 * <p>The keys are public, so they travel as they are, beside the sealed station key under
 * {@value #FIELD}.
 */
@Singleton
public class PartnersLeftBehind {
    /** The field of the exported station page that names the partners left behind. */
    public static final String FIELD = "partnersLeftBehind";

    private static final Logger log = LoggerFactory.getLogger(PartnersLeftBehind.class);

    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationKeyStore keys;

    @Inject
    public PartnersLeftBehind(
            FederationRepository federationRepository, StationRepository stationRepository, StationKeyStore keys) {
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.keys = keys;
    }

    /**
     * The partners of a station that run on this instance, each with the key it signs with, for the
     * page the station is exported with.
     *
     * <p>A partner without a key is given one, and so is the station itself where it has partners
     * here: after the move, the two of them can only reach each other signed.
     *
     * @param stationId the station being exported
     * @return the partners it leaves behind, empty when it has none here
     */
    public List<LeftBehindPartner> of(int stationId) {
        var partners = new ArrayList<LeftBehindPartner>();
        for (FederationPartner partner : federationRepository.findLocalPartnerships(stationId)) {
            stationRepository
                    .findHereByUid(partner.partnerStationId())
                    .ifPresent(station -> partners.add(
                            new LeftBehindPartner(station.uid(), station.name(), keys.ensurePublicKey(station.id()))));
        }
        if (!partners.isEmpty()) keys.ensurePublicKey(stationId);
        return partners;
    }

    /**
     * The partners an exported station page names.
     *
     * @param page the exported station page
     * @return the partners, empty when it names none
     */
    public static List<LeftBehindPartner> read(Map<String, Object> page) {
        if (!(page.get(FIELD) instanceof List<?> entries)) return List.of();
        var partners = new ArrayList<LeftBehindPartner>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> fields)) continue;
            if (!(fields.get("stationUid") instanceof String uid)) continue;
            if (!(fields.get("publicKey") instanceof String publicKey) || publicKey.isBlank()) continue;
            String name = fields.get("name") instanceof String text ? text : "";
            try {
                partners.add(new LeftBehindPartner(UUID.fromString(uid), name, publicKey));
            } catch (IllegalArgumentException e) {
                log.warn("A partner left behind is named by an unreadable uid and was skipped: {}", uid);
            }
        }
        return partners;
    }

    /**
     * Lets an imported station verify the partners it left behind on its source instance.
     *
     * <p>Each of its partnerships with them already points back at the source; it gets the partner's
     * key and name, and the contract of this build, which the source runs as well since a station
     * only moves between instances of one schema.
     *
     * @param stationId the station that arrived
     * @param partners  the partners its page named
     * @return how many partnerships can be verified now
     */
    public int adopt(int stationId, List<LeftBehindPartner> partners) {
        int adopted = 0;
        for (LeftBehindPartner partner : partners) {
            var row = federationRepository.findRemotePartnership(stationId, partner.stationUid());
            if (row.isEmpty()) continue;
            federationRepository.adoptPartnerKey(row.get().id(), partner.publicKey(), partner.name());
            federationRepository.updateFederationContract(row.get().id(), FederationContractVersions.current());
            adopted++;
        }
        log.info(
                "Station {} can verify {} of the {} partner(s) it left on its source instance",
                stationId,
                adopted,
                partners.size());
        return adopted;
    }

    /**
     * A partner a station leaves behind on its source instance.
     *
     * @param stationUid the partner
     * @param name       what it is called
     * @param publicKey  the Base64 public key it signs with
     */
    public record LeftBehindPartner(UUID stationUid, String name, String publicKey) {}
}
