/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Setting the name a member is called by.
 * <p>
 * Two people may write it and no others: the member themselves, and whoever looks after them, which
 * is what covers a child whose guardian keeps their profile. A nickname somebody else could impose
 * is how this feature would turn into a way to label people, so the rule is enforced here rather
 * than left to each route, and whoever wrote it is recorded beside it.
 */
@Singleton
public class NicknameService {
    private static final Logger log = LoggerFactory.getLogger(NicknameService.class);

    /**
     * The longest a nickname may be.
     *
     * <p>Long enough for any name somebody is actually called by, short enough that it cannot be
     * used to write a sentence into a member list.
     */
    public static final int MAX_LENGTH = 60;

    private final StationMemberRepository memberRepository;
    private final StationMemberService memberService;
    private final MemberNameResolver nameResolver;

    @Inject
    public NicknameService(
            StationMemberRepository memberRepository,
            StationMemberService memberService,
            MemberNameResolver nameResolver) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.nameResolver = nameResolver;
    }

    /**
     * Writes the name a member is called by.
     *
     * @param memberId the member the name belongs to
     * @param nickname the name, or null or blank to give them their register name back
     * @param actorId the member doing the writing
     * @param permissions what the actor may do at this station
     * @throws ForbiddenResponse where the actor is neither the member, nor one of their managers,
     *     nor somebody who keeps the station's members
     * @throws BadRequestResponse where the name is longer than {@link #MAX_LENGTH} or carries a line
     *     break
     */
    public void set(int memberId, String nickname, int actorId, Set<StationPermission> permissions) {
        var member = memberRepository.findById(memberId).orElseThrow(NotFoundResponse::new);
        requireMayWrite(memberId, actorId, permissions);

        String cleaned = clean(nickname);
        memberRepository.setNickname(memberId, cleaned, actorId);
        nameResolver.forget(memberId);
        log.info(
                "Nickname of member {} at station {} {} by member {}",
                memberId,
                member.stationId(),
                cleaned == null ? "cleared" : "set",
                actorId);
    }

    /**
     * Whether this member may write that member's nickname.
     *
     * <p>Their own, one belonging to somebody they look after, or anybody's where they keep the
     * station's members: a wrong or unwanted name has to be correctable by the people who run the
     * place, and somebody who can already change a member's name in the register is not held back
     * by being refused the shorter one.
     *
     * <p>That this can be used to put a name on somebody who did not choose it is the reason
     * {@code nickname_set_by} exists. What was written and who wrote it is on the row.
     */
    public boolean mayWrite(int memberId, int actorId, Set<StationPermission> permissions) {
        if (memberId == actorId) return true;
        if (permissions.contains(StationPermission.MEMBER_EDIT)) return true;
        return memberService.findManaged(actorId).stream().anyMatch(managed -> managed.id() == memberId);
    }

    private void requireMayWrite(int memberId, int actorId, Set<StationPermission> permissions) {
        if (!mayWrite(memberId, actorId, permissions)) {
            throw new ForbiddenResponse("Only a member, whoever looks after them, or whoever keeps the station's "
                    + "members may set the name they go by");
        }
    }

    private static String clean(String nickname) {
        if (nickname == null) return null;
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_LENGTH) {
            throw new BadRequestResponse("A name to be called by is at most " + MAX_LENGTH + " characters");
        }
        if (trimmed.chars().anyMatch(c -> c == '\n' || c == '\r')) {
            throw new BadRequestResponse("A name to be called by is one line");
        }
        return trimmed;
    }
}
