/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.owner.Owner;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An association with one station and one member at it, the ground every characterisation test of a
 * system kept per owner stands on.
 *
 * <p>Each scene is new, so a test sees only the rows it made. Closing it releases the station from the
 * association and deletes it, which takes the members and their answers with it. Built on the
 * services {@link RepositoryTestBase} wires, so it is only used from tests extending it.
 *
 * @param association the association
 * @param station     its station
 * @param member      the member at that station
 */
public record OwnerScene(Owner.Association association, Owner.Station station, StationMember member)
        implements AutoCloseable {
    private static final AtomicInteger NAMES = new AtomicInteger();

    /**
     * A new association, a station in it and a member there.
     *
     * @param label what the rows are named after, so a failing test's leftovers say whose they are
     * @return the scene
     */
    public static OwnerScene association(String label) {
        int number = NAMES.incrementAndGet();
        int clusterId = RepositoryTestBase.clusterService
                .create("Verband " + label + " " + number, null)
                .id();
        int stationId = RepositoryTestBase.clusterService
                .createStation(clusterId, "Wache " + label + " " + number)
                .id();
        return new OwnerScene(
                new Owner.Association(clusterId), new Owner.Station(stationId), memberAt(stationId, label));
    }

    /**
     * Another member at the scene's station.
     *
     * @param label what their account is named after
     * @return the member
     */
    public StationMember newMember(String label) {
        return memberAt(station.stationId(), label);
    }

    /**
     * Somebody with an account and no membership at any station, as an association manager often is.
     *
     * @param label what their account is named after
     * @return the account id
     */
    public int newAccount(String label) {
        return RepositoryTestBase.accountRepo
                .create(mail(label), label, "Szene")
                .id();
    }

    private static StationMember memberAt(int stationId, String label) {
        var account = RepositoryTestBase.accountRepo.create(mail(label), label, "Szene");
        return RepositoryTestBase.stationMemberRepo.create(stationId, account.id());
    }

    private static String mail(String label) {
        return "scene-" + label.toLowerCase().replace(' ', '-') + "-" + NAMES.incrementAndGet() + "@test.com";
    }

    @Override
    public void close() {
        RepositoryTestBase.clusterService.releaseStation(association.clusterId(), station.stationId());
        RepositoryTestBase.stationRepo.delete(station.stationId());
    }
}
