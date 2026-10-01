/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The accounts a demo or development instance offers on its sign-in page, grouped by the station
 * they belong to.
 *
 * <p>A cluster's home station has no members, so only regular stations form groups. A row on a
 * cluster's own station is a byline on what the cluster writes, not somebody being at a station:
 * an account whose only rows are of that kind is listed without a station, so the demo
 * administrator does not vanish from the picker the moment they write for a cluster.
 */
@Singleton
public class DemoAccountService {

    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final ProfileFieldService profileFieldService;
    private final ClusterRepository clusterRepository;
    private final Provider<ClusterService> clusterService;

    @Inject
    public DemoAccountService(
            AccountRepository accountRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            ProfileFieldService profileFieldService,
            ClusterRepository clusterRepository,
            Provider<ClusterService> clusterService) {
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.profileFieldService = profileFieldService;
        this.clusterRepository = clusterRepository;
        this.clusterService = clusterService;
    }

    /**
     * Every account of the instance, those at a regular station grouped by it and the rest on their
     * own.
     *
     * @return the accounts
     */
    public DemoAccountsResponse accounts() {
        List<Station> stations = stationRepository.findAllRegular();
        List<DemoStationGroup> groups = new ArrayList<>();
        for (Station station : stations) {
            List<DemoAccount> accounts = stationAccounts(station);
            if (!accounts.isEmpty()) {
                groups.add(new DemoStationGroup(station.uid().toString(), station.name(), accounts));
            }
        }
        Set<Integer> regularStationIds = stations.stream().map(Station::id).collect(Collectors.toSet());
        return new DemoAccountsResponse(accountsWithoutStation(regularStationIds), groups);
    }

    private List<DemoAccount> stationAccounts(Station station) {
        List<DemoAccount> accounts = new ArrayList<>();
        for (StationMember member : stationMemberRepository.findByStation(station.id())) {
            if (member.accountId() == null) continue;
            accountRepository
                    .findById(member.accountId())
                    .ifPresent(account -> accounts.add(memberAccount(account, member)));
        }
        return accounts;
    }

    private DemoAccount memberAccount(Account account, StationMember member) {
        List<String> permissions = stationMemberRepository.findPermissions(member.id()).stream()
                .map(permission -> permission.permission().name())
                .toList();
        List<String> groups = memberGroupRepository.findGroupsForMember(member.id()).stream()
                .map(MemberGroup::name)
                .toList();
        List<String> tags = userTagRepository.findTagsForMember(member.id()).stream()
                .map(UserTag::name)
                .toList();
        return new DemoAccount(
                account.email(),
                account.firstName(),
                account.lastName(),
                member.userType(),
                permissions,
                groups,
                tags,
                profileFieldService.isProfileComplete(member.id()),
                account.instanceUserType() == InstanceUserType.ADMINISTRATOR,
                clusterPermissionsOf(account.id()));
    }

    private List<DemoAccount> accountsWithoutStation(Set<Integer> regularStationIds) {
        List<DemoAccount> accounts = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            boolean atAStation = stationMemberRepository.findAllByAccountId(account.id()).stream()
                    .anyMatch(member -> regularStationIds.contains(member.stationId()));
            if (!atAStation) accounts.add(stationlessAccount(account));
        }
        return accounts;
    }

    private DemoAccount stationlessAccount(Account account) {
        boolean administrator = account.instanceUserType() == InstanceUserType.ADMINISTRATOR;
        return new DemoAccount(
                account.email(),
                account.firstName(),
                account.lastName(),
                administrator ? StationUserType.MANAGER : StationUserType.MEMBER,
                List.of(),
                List.of(),
                List.of(),
                true,
                administrator,
                clusterPermissionsOf(account.id()));
    }

    /**
     * Everything an account may do for any cluster it belongs to, flattened.
     *
     * <p>Flattened because the stories that read this pick an actor by what they are allowed to do, and the
     * demo has one cluster: telling them which cluster each right came from would be a distinction with
     * nothing behind it. An account in no cluster answers with nothing, which is the same answer the picker
     * gives.
     */
    private List<String> clusterPermissionsOf(int accountId) {
        var service = clusterService.get();
        return clusterRepository.findAll().stream()
                .flatMap(cluster -> service.findMembers(cluster.id()).stream())
                .filter(member -> member.accountId() == accountId)
                .flatMap(member -> service.resolvePermissions(member).stream())
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * One account the demo instance offers for signing in.
     *
     * @param email                 the address it signs in with
     * @param firstName             the first name
     * @param lastName              the last name
     * @param userType              its user type at the station, or the bucket it is listed under without one
     * @param permissions           the station permissions it holds, expanded
     * @param groups                the names of the groups it belongs to
     * @param tags                  the names of the tags it carries
     * @param profileComplete       whether its profile is fully filled in
     * @param instanceAdministrator whether the account administers the instance itself. Station
     *                              permissions say nothing about that, so a caller looking for
     *                              someone who may reach the admin area has no other way to tell.
     * @param clusterPermissions    everything it may do for any association, expanded
     */
    public record DemoAccount(
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            List<String> permissions,
            List<String> groups,
            List<String> tags,
            boolean profileComplete,
            boolean instanceAdministrator,
            List<String> clusterPermissions) {}

    /**
     * A station and the demo accounts that belong to it.
     *
     * @param stationId   the station's address
     * @param stationName its name
     * @param accounts    its accounts
     */
    public record DemoStationGroup(String stationId, String stationName, List<DemoAccount> accounts) {}

    /**
     * The demo accounts of the instance.
     *
     * @param noStationAccounts the accounts at no regular station
     * @param stationGroups     the accounts at a regular station, by station
     */
    public record DemoAccountsResponse(List<DemoAccount> noStationAccounts, List<DemoStationGroup> stationGroups) {}
}
