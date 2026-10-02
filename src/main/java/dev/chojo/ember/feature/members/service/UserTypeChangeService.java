/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Changes a member's user type together with what follows from it for their groups.
 *
 * <p>A group bound to user types takes only members of those types, so a member who becomes another
 * type leaves every bound group that does not take the new one, in the same transaction. The edit
 * page asks {@link #consequences} first and lets the manager confirm; the waiting list and a cluster
 * change the type without asking, and the groups follow all the same.
 */
@Singleton
public class UserTypeChangeService {
    private static final Logger log = LoggerFactory.getLogger(UserTypeChangeService.class);
    private final StationMemberRepository memberRepository;
    private final GroupMembershipService membershipService;

    @Inject
    public UserTypeChangeService(StationMemberRepository memberRepository, GroupMembershipService membershipService) {
        this.memberRepository = memberRepository;
        this.membershipService = membershipService;
    }

    /**
     * The groups a member would leave on becoming another type.
     *
     * @param memberId the member
     * @param userType the type they would become
     * @return the groups whose binding does not take that type
     */
    public List<MemberGroup> consequences(int memberId, StationUserType userType) {
        return membershipService.groupsUnfitFor(memberId, userType);
    }

    /**
     * The groups a member would leave on becoming another type, with the type named as the address
     * names it.
     *
     * @param memberId the member
     * @param named    the name of the type they would become
     * @return the groups whose binding does not take that type
     */
    public List<MemberGroup> consequences(int memberId, String named) {
        var userType = Arrays.stream(StationUserType.values())
                .filter(type -> type.name().equals(named))
                .findFirst()
                .orElseThrow(() -> MemberRefusal.USER_TYPE_UNKNOWN_FOR_CONSEQUENCES.raise(named));
        return consequences(memberId, userType);
    }

    /**
     * Sets a member's type and takes them out of the groups that do not take it.
     *
     * @param memberId the member
     * @param userType their new type
     * @return the groups they left
     */
    public List<MemberGroup> change(int memberId, StationUserType userType) {
        var left = Transactions.call(() -> {
            memberRepository.setUserType(memberId, userType);
            return membershipService.leaveGroupsUnfitFor(memberId, userType);
        });
        log.info("User type changed for member {}: {}, left {} group(s)", memberId, userType, left.size());
        return left;
    }
}
