/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.NameChangeOutcome;
import dev.chojo.ember.feature.members.entity.NameChangeRequest;
import dev.chojo.ember.feature.members.entity.NameChangeView;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.NameChangeRequestRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A new register name a member asks for, which waits for a member manager before it reaches the
 * account.
 *
 * <p>Only a member changing their own name waits. Whoever may confirm member changes at one of
 * their stations, an instance administrator, and an account that belongs to no station are not
 * asked to wait: an approval they could give themselves, or that nobody could give, would only be
 * a click in the way.
 *
 * <p>The name belongs to the account, not to a membership, so the request is shown at every
 * station the member belongs to, and the first decision at any of them is the one that counts.
 */
@Singleton
public class NameChangeService {
    /** The longest reason a denial carries, which is what a notification still shows in full. */
    public static final int MAX_REASON_LENGTH = 500;

    private final NameChangeRequestRepository requests;
    private final AccountRepository accounts;
    private final StationMemberService memberService;
    private final StationMemberRepository memberRepository;
    private final MemberPermissionResolver permissionResolver;
    private final MemberNameResolver nameResolver;
    private final MemberIdentityFactory identityFactory;
    private final Notifier notifier;

    @Inject
    public NameChangeService(
            NameChangeRequestRepository requests,
            AccountRepository accounts,
            StationMemberService memberService,
            StationMemberRepository memberRepository,
            MemberPermissionResolver permissionResolver,
            MemberNameResolver nameResolver,
            MemberIdentityFactory identityFactory,
            Notifier notifier) {
        this.requests = requests;
        this.accounts = accounts;
        this.memberService = memberService;
        this.memberRepository = memberRepository;
        this.permissionResolver = permissionResolver;
        this.nameResolver = nameResolver;
        this.identityFactory = identityFactory;
        this.notifier = notifier;
    }

    /**
     * Whether the reader's change of their own name has to wait for a member manager.
     *
     * @param session the reader, changing their own name
     * @return false for an instance administrator, an account at no station, and anybody who may
     *         confirm member changes at one of their stations
     */
    public boolean needsApproval(UserSession session) {
        if (session.hasInstancePermission(InstancePermission.ADMINISTRATOR)) return false;
        var memberships = membershipsOf(session.accountId());
        if (memberships.isEmpty()) return false;
        return memberships.stream()
                .noneMatch(member -> permissionResolver.resolve(member).contains(StationPermission.MEMBER_CHANGES));
    }

    /**
     * Asks for a new name and tells whoever confirms member changes at each of the member's
     * stations. A request still open is replaced.
     *
     * @param account   the account asking, with the name it carries now
     * @param firstName the first name asked for
     * @param lastName  the last name asked for
     * @return the open request
     */
    public NameChangeRequest request(Account account, String firstName, String lastName) {
        var request = requests.request(account.id(), firstName, lastName);
        var data = NotificationData.of(
                new NotificationParams.NameChangeRequested(account.fullName(), request.fullName()),
                NotificationLinks.memberChanges());
        for (StationMember member : membershipsOf(account.id())) {
            notifier.notify(
                    StationAudience.holders(member.stationId(), StationPermission.MEMBER_CHANGES)
                            .except(member.id()),
                    NotificationType.NAME_CHANGE_REQUESTED,
                    data,
                    Delivery.EVERY_TIME);
        }
        return request;
    }

    /**
     * The open requests of the station's current members, oldest first.
     *
     * @param stationId  the station
     * @param stationUid the same station's uid, which the members on the list carry
     * @return the requests
     */
    public List<NameChangeView> openAt(int stationId, UUID stationUid) {
        return requests.findOpenAtStation(stationId).stream()
                .map(pending -> new NameChangeView(
                        pending.requestId(),
                        identityFactory.enrich(new MemberIdentity(stationUid, pending.memberUid())),
                        pending.currentName(),
                        pending.requestedName(),
                        pending.requestedAt()))
                .toList();
    }

    /**
     * Approves a request: the account takes the name, and the member is told.
     *
     * @param stationId the station deciding, which the member has to belong to
     * @param deciderId the account deciding
     * @param requestId the request
     */
    public void approve(int stationId, int deciderId, int requestId) {
        var request = requireOpenAt(stationId, requestId);
        decide(request, NameChangeOutcome.APPROVED, deciderId, null);
        var account = accounts.findById(request.accountId()).orElseThrow(MemberRefusal.NAME_CHANGE_NOT_OPEN::raise);
        accounts.update(account.id(), account.email(), request.firstName(), request.lastName());
        nameResolver.forgetAccount(account.id());
        tellMember(
                stationId,
                request,
                NotificationType.NAME_CHANGE_APPROVED,
                new NotificationParams.NameChangeApproved(request.fullName()));
    }

    /**
     * Denies a request: the account keeps its name, and the member is told, with the reason where
     * one was given.
     *
     * @param stationId the station deciding, which the member has to belong to
     * @param deciderId the account deciding
     * @param requestId the request
     * @param reason    why, or null or blank for no reason
     */
    public void deny(int stationId, int deciderId, int requestId, @Nullable String reason) {
        var given = reason == null || reason.isBlank() ? null : reason.strip();
        if (given != null && given.length() > MAX_REASON_LENGTH) {
            throw MemberRefusal.NAME_CHANGE_REASON_TOO_LONG.raise();
        }
        var request = requireOpenAt(stationId, requestId);
        decide(request, NameChangeOutcome.DENIED, deciderId, given);
        tellMember(
                stationId,
                request,
                NotificationType.NAME_CHANGE_DENIED,
                new NotificationParams.NameChangeDenied(request.fullName(), given));
    }

    /**
     * The name an account asked for and that still waits.
     *
     * @param accountId the account
     * @return the open request, or empty where nothing waits
     */
    public Optional<NameChangeRequest> openOf(int accountId) {
        return requests.findOpenByAccount(accountId);
    }

    /**
     * Takes back the name an account asked for.
     *
     * @param accountId the account
     */
    public void withdraw(int accountId) {
        var request = requests.findOpenByAccount(accountId).orElseThrow(MemberRefusal.NAME_CHANGE_NONE_WAITING::raise);
        if (!requests.decide(request.id(), NameChangeOutcome.WITHDRAWN, null, null)) {
            throw MemberRefusal.NAME_CHANGE_NONE_WAITING.raise();
        }
    }

    private List<StationMember> membershipsOf(int accountId) {
        return memberService.findBelongingByAccount(accountId).stream()
                .filter(member -> !member.former())
                .toList();
    }

    private NameChangeRequest requireOpenAt(int stationId, int requestId) {
        return requests.findOpenById(requestId)
                .filter(request ->
                        currentMemberAt(stationId, request.accountId()).isPresent())
                .orElseThrow(MemberRefusal.NAME_CHANGE_NOT_OPEN::raise);
    }

    private Optional<StationMember> currentMemberAt(int stationId, int accountId) {
        return memberRepository.findByStationAndAccount(stationId, accountId).filter(member -> !member.former());
    }

    private void decide(NameChangeRequest request, NameChangeOutcome outcome, int deciderId, @Nullable String reason) {
        if (!requests.decide(request.id(), outcome, deciderId, reason)) {
            throw MemberRefusal.NAME_CHANGE_NOT_OPEN.raise();
        }
    }

    private void tellMember(
            int stationId, NameChangeRequest request, NotificationType type, NotificationParams params) {
        currentMemberAt(stationId, request.accountId())
                .ifPresent(member -> notifier.notify(
                        StationAudience.member(member.id()),
                        type,
                        NotificationData.of(params, NotificationLinks.ownAccountProfile()),
                        Delivery.EVERY_TIME));
    }
}
