/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Appointing the first person who may act for a cluster, the one cluster call an instance
 * administrator makes from outside.
 *
 * <p>A cluster the instance has just created has nobody in it, and every other cluster call asks
 * for a right only a member can hold. Without this a new cluster could never get its first person.
 * It appoints and nothing more; who else acts for the cluster afterwards is the cluster's business.
 */
@Singleton
public class ClusterAppointmentService {
    private final ClusterService clusterService;
    private final AccountRepository accounts;

    @Inject
    public ClusterAppointmentService(ClusterService clusterService, AccountRepository accounts) {
        this.clusterService = clusterService;
        this.accounts = accounts;
    }

    /**
     * Makes an account an administrator of a cluster.
     *
     * @param clusterUid the cluster
     * @param accountUid the account to appoint
     */
    public void appointAdministrator(UUID clusterUid, UUID accountUid) {
        var cluster =
                clusterService.findByUid(clusterUid).orElseThrow(ClusterRefusal.CLUSTER_NOT_HERE_ON_APPOINTMENT::raise);
        var account = accounts.findByUid(accountUid)
                .orElseThrow(ClusterRefusal.ACCOUNT_NOT_HERE_ON_CLUSTER_APPOINTMENT::raise);
        clusterService.addMember(cluster.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);
    }
}
