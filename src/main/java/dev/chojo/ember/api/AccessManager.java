/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.contract.FederationContractCatalog;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationContractRefreshService;
import dev.chojo.ember.feature.federation.service.FederationReplayCache;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages authentication and authorization by resolving session tokens into user sessions
 * and computing effective permission sets for members.
 */
@Singleton
public class AccessManager {
    private static final Logger log = LoggerFactory.getLogger(AccessManager.class);

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final FederationRepository federationRepository;
    private final FederationSigningService signingService;
    private final FederationReplayCache replayCache;
    private final StationRepository stationRepository;
    private final FederationContractRefreshService contractRefreshService;

    @Inject
    public AccessManager(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            FederationRepository federationRepository,
            FederationSigningService signingService,
            FederationReplayCache replayCache,
            StationRepository stationRepository,
            FederationContractRefreshService contractRefreshService) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.federationRepository = federationRepository;
        this.signingService = signingService;
        this.replayCache = replayCache;
        this.stationRepository = stationRepository;
        this.contractRefreshService = contractRefreshService;
    }

    public Optional<AccountSession> resolveSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<AccountSession> session = accountRepository.findSession(token);
        if (session.isPresent() && session.get().isExpired()) {
            accountRepository.deleteSession(token);
            return Optional.empty();
        }
        return session;
    }

    public Optional<UserSession> resolveUserSession(String token, Station station) {
        Optional<AccountSession> sessionOpt = resolveSession(token);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        AccountSession accountSession = sessionOpt.get();
        int accountId = accountSession.accountId();
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return Optional.empty();
        }

        Account account = accountOpt.get();
        Integer stationId = station != null ? station.id() : null;
        UUID stationUid = station != null ? station.uid() : null;

        // Resolve instance-level permissions
        Set<InstancePermission> instancePermissions = resolveInstancePermissions(account);

        if (stationId != null) {
            Optional<StationMember> memberOpt = stationMemberRepository.findByStationAndAccount(stationId, accountId);
            if (memberOpt.isPresent()) {
                StationMember member = memberOpt.get();
                Set<StationPermission> permissions = resolveExpandedMemberPermissions(member);
                permissions.add(StationPermission.LOGIN);
                return Optional.of(new UserSession(
                        account,
                        accountSession.id(),
                        stationId,
                        stationUid,
                        member,
                        permissions,
                        instancePermissions,
                        accountSession.twoFactorVerifiedAt()));
            }
        }

        Set<StationPermission> baseline = EnumSet.noneOf(StationPermission.class);
        baseline.add(StationPermission.LOGIN);
        return Optional.of(new UserSession(
                account,
                accountSession.id(),
                stationId,
                stationUid,
                null,
                baseline,
                instancePermissions,
                accountSession.twoFactorVerifiedAt()));
    }

    /**
     * Resolves the expanded permissions for a station member.
     */
    public Set<StationPermission> resolveExpandedMemberPermissions(StationMember member) {
        Set<StationPermission> permissions = EnumSet.noneOf(StationPermission.class);

        // 1. Default permissions from user type (hardcoded in enum)
        permissions.addAll(Arrays.asList(member.userType().defaultPermissions()));

        // 2. Station-level user type permissions (configured per station)
        stationMemberRepository.findUserTypePermissions(member.stationId(), member.userType()).stream()
                .map(Permission::permission)
                .forEach(permissions::add);

        // 3. Direct permission grants
        stationMemberRepository.findPermissions(member.id()).stream()
                .map(Permission::permission)
                .forEach(permissions::add);

        // 4. Group-inherited permissions
        memberGroupRepository.findPermissionsForMemberViaGroups(member.id()).stream()
                .map(Permission::permission)
                .forEach(permissions::add);

        // 5. Expand hierarchy
        return StationPermission.expand(permissions);
    }

    /**
     * Resolves the expanded permissions for a station member by their member ID.
     */
    public Set<StationPermission> resolveExpandedMemberPermissions(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(this::resolveExpandedMemberPermissions)
                .orElse(EnumSet.noneOf(StationPermission.class));
    }

    public Optional<FederationSession> resolveFederationSession(Context ctx) {
        String stationIdHeader = ctx.header("X-Federation-Station-Id");
        String signature = ctx.header("X-Federation-Signature");
        String timestampHeader = ctx.header("X-Federation-Timestamp");
        String nonceHeader = ctx.header("X-Federation-Nonce");

        if (stationIdHeader == null || signature == null || timestampHeader == null || nonceHeader == null) {
            return Optional.empty();
        }

        UUID remoteStationUid;
        try {
            remoteStationUid = UUID.fromString(stationIdHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-Federation-Station-Id header value: {}", stationIdHeader);
            return Optional.empty();
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampHeader);
        } catch (Exception e) {
            log.warn("Invalid X-Federation-Timestamp header value: {}", timestampHeader);
            return Optional.empty();
        }

        UUID nonce;
        try {
            nonce = UUID.fromString(nonceHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-Federation-Nonce header value: {}", nonceHeader);
            return Optional.empty();
        }

        String targetHeader = ctx.header("X-Federation-Target-Station-Id");
        UUID targetStationUid = null;
        if (targetHeader != null && !targetHeader.isBlank()) {
            try {
                targetStationUid = UUID.fromString(targetHeader);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-Federation-Target-Station-Id header value: {}", targetHeader);
                return Optional.empty();
            }
        }
        Optional<FederationPartner> partner = targetStationUid != null
                ? federationRepository.findPartnerByLocalAndRemoteStationUid(targetStationUid, remoteStationUid)
                : federationRepository.findPartnerByRemoteStationUid(remoteStationUid);
        if (partner.isEmpty()) {
            log.warn("Unknown federation partner for station UUID: {}", remoteStationUid);
            return Optional.empty();
        }

        var p = partner.get();
        if (p.status() != FederationPartner.FederationStatus.ACTIVE) {
            log.warn("Federation partner {} is not active (status: {})", p.id(), p.status());
            return Optional.empty();
        }

        if (p.partnerPublicKey() == null || p.partnerPublicKey().isBlank()) {
            log.warn("Federation partner {} has no public key configured", p.id());
            return Optional.empty();
        }

        UUID ourStationUid = stationRepository.resolveUid(p.stationId());
        if (ourStationUid == null) {
            log.warn("Could not resolve receiver station UUID for partner {} (station {})", p.id(), p.stationId());
            return Optional.empty();
        }

        if (FederationSigningService.hasDuplicateQueryKeys(ctx.queryString())) {
            log.warn("Rejecting federation request from partner {} - duplicate query parameter keys", p.id());
            return Optional.empty();
        }

        var publicKey = signingService.decodePublicKey(p.partnerPublicKey());
        String pathWithQuery = FederationSigningService.canonicalPathWithQuery(ctx.path(), ctx.queryString());
        boolean valid = signingService.verify(
                ctx.method().name(),
                pathWithQuery,
                ourStationUid,
                nonce.toString(),
                ctx.body(),
                signature,
                publicKey,
                timestamp);
        if (!valid) {
            log.warn("Invalid federation signature from partner {} (station {})", p.id(), remoteStationUid);
            return Optional.empty();
        }

        if (!replayCache.checkAndRemember(p.id(), nonce)) {
            log.warn("Replayed federation nonce {} from partner {} (station {})", nonce, p.id(), remoteStationUid);
            return Optional.empty();
        }

        if (presentsUnknownContract(ctx, p)) {
            contractRefreshService.refreshAsync(p);
        }

        return Optional.of(new FederationSession(p, remoteStationUid));
    }

    /**
     * Whether the hashes an incoming request carries disagree with the vector stored for
     * the partner, meaning the partner has redeployed since the last exchange.
     * <p>
     * Both hashes have to be checked: a release that rolls only one feature surface leaves
     * the core hash equal, so a core-only comparison would never notice and that feature
     * would stay paused until a restart - while the pause itself stops the outbound calls
     * whose rejection would otherwise trigger the refresh.
     */
    private boolean presentsUnknownContract(Context ctx, FederationPartner partner) {
        var stored = partner.federationContract();
        String remoteCore = ctx.header(FederationHeaders.HEADER_CORE);
        if (remoteCore != null && !remoteCore.equals(partner.coreHash())) return true;

        String remoteSurface = ctx.header(FederationHeaders.HEADER_SURFACE);
        if (remoteSurface == null || stored == null) return false;
        return FederationContractCatalog.surfaceOfRequestPath(ctx.method(), ctx.path())
                .filter(surface -> surface != FederationSurface.CORE)
                .map(surface -> !remoteSurface.equals(stored.featureHash(surface.capability())))
                .orElse(false);
    }

    private Set<InstancePermission> resolveInstancePermissions(Account account) {
        Set<InstancePermission> permissions = EnumSet.noneOf(InstancePermission.class);
        if (account.instanceUserType() == InstanceUserType.ADMINISTRATOR) {
            permissions.addAll(Arrays.asList(InstanceUserType.ADMINISTRATOR.defaultPermissions()));
        }
        return InstancePermission.expand(permissions);
    }
}
