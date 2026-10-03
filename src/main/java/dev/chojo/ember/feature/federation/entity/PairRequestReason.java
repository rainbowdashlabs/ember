/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;

import java.util.Arrays;
import java.util.Optional;

/**
 * Why an instance refused a message about a request to federate, as it says so on the wire.
 *
 * <p>The set is closed and travels by name, so the asking instance reads the reason without knowing
 * the refusal codes of the instance that answered. Each value stands for one refusal the receiving
 * side raises; the sending side shows the person who asked the same refusal under its own code.
 */
public enum PairRequestReason {
    /** The message missed a part. */
    INCOMPLETE(FederationRefusal.PAIR_REQUEST_INCOMPLETE),
    /** A part was longer than any honest one would be. */
    TOO_LARGE(FederationRefusal.PAIR_REQUEST_TOO_LARGE),
    /** The message was signed too long ago or arrived twice. */
    OUT_OF_TIME(FederationRefusal.PAIR_REQUEST_OUT_OF_TIME),
    /** The sending instance is not known through discovery. */
    INSTANCE_UNKNOWN(FederationRefusal.PAIR_REQUEST_INSTANCE_UNKNOWN),
    /** The sending instance is blocked or distrusted. */
    INSTANCE_BLOCKED(FederationRefusal.PAIR_REQUEST_INSTANCE_BLOCKED),
    /** The instance signature did not fit. */
    INSTANCE_SIGNATURE_NOT_GOOD(FederationRefusal.PAIR_REQUEST_INSTANCE_SIGNATURE_NOT_GOOD),
    /** The station signature did not fit. */
    STATION_SIGNATURE_NOT_GOOD(FederationRefusal.PAIR_REQUEST_STATION_SIGNATURE_NOT_GOOD),
    /** The address named is not the one the sending instance is known by. */
    ADDRESS_NOT_THE_INSTANCES(FederationRefusal.PAIR_REQUEST_ADDRESS_NOT_THE_INSTANCES),
    /** The two instances run federation versions that cannot talk to each other. */
    CONTRACT_MISMATCH(FederationRefusal.PAIR_REQUEST_CONTRACT_MISMATCH),
    /** The sending instance sent too many requests. */
    TOO_MANY_FROM_INSTANCE(FederationRefusal.PAIR_REQUEST_TOO_MANY_FROM_INSTANCE),
    /** The asked station received too many requests. */
    TOO_MANY_FOR_STATION(FederationRefusal.PAIR_REQUEST_TOO_MANY_FOR_STATION),
    /** The asked station is not there, not public or does not take requests. */
    STATION_NOT_HERE(FederationRefusal.PAIR_REQUEST_STATION_NOT_HERE),
    /** The two stations are partners already. */
    ALREADY_PARTNERS(FederationRefusal.PAIR_REQUEST_ALREADY_PARTNERS),
    /** A request between the two stations still waits for its answer. */
    ALREADY_WAITING(FederationRefusal.PAIR_REQUEST_ALREADY_WAITING),
    /** The asked station declined the last request less than 30 days ago. */
    DECLINED_RECENTLY(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY),
    /** The question named a request that was never received. */
    STATUS_NOT_HERE(FederationRefusal.PAIR_STATUS_NOT_HERE),
    /** The answer named a request nobody waits for. */
    ANSWER_NOT_EXPECTED(FederationRefusal.PAIR_ANSWER_NOT_EXPECTED),
    /** The signature of the answer did not fit. */
    ANSWER_SIGNATURE_NOT_GOOD(FederationRefusal.PAIR_ANSWER_SIGNATURE_NOT_GOOD);

    private final FederationRefusal refusal;

    PairRequestReason(FederationRefusal refusal) {
        this.refusal = refusal;
    }

    /**
     * The refusal this reason stands for, which the asking side shows under its own code.
     *
     * @return the refusal
     */
    public FederationRefusal refusal() {
        return refusal;
    }

    /**
     * The reason a refusal goes out under.
     *
     * @param refusal what refused the message
     * @return the reason, or empty for a refusal that is no reason of its own on the wire
     */
    public static Optional<PairRequestReason> of(Refusal refusal) {
        return Arrays.stream(values())
                .filter(reason -> reason.refusal == refusal)
                .findFirst();
    }
}
