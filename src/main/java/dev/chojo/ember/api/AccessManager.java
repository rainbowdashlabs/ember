/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import io.javalin.http.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages authentication and authorization by resolving session tokens into user sessions
 * and computing effective role sets for members.
 */
@Singleton
public class AccessManager {
    private static final Logger log = LoggerFactory.getLogger(AccessManager.class);

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final FederationRepository federationRepository;
    private final FederationSigningService signingService;

    @Inject
    public AccessManager(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            FederationRepository federationRepository,
            FederationSigningService signingService) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.federationRepository = federationRepository;
        this.signingService = signingService;
    }

    /**
     * Resolves a session token to an active account session.
     * Expired sessions are automatically deleted and treated as absent.
     *
     * @param token the bearer token from the Authorization header
     * @return the account session if the token is valid and not expired
     */
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

    /**
     * Resolves a session token and optional station context into a full user session.
     * Combines account-level roles with station-specific member and group roles, then expands the role hierarchy.
     *
     * @param token   the bearer token
     * @param station the station to scope the session to, or {@code null} for account-level only
     * @return the user session with resolved roles, or empty if the token is invalid
     */
    public Optional<UserSession> resolveUserSession(String token, Station station) {
        Optional<AccountSession> sessionOpt = resolveSession(token);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        int accountId = sessionOpt.get().accountId();
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return Optional.empty();
        }

        Account account = accountOpt.get();
        Integer stationId = station != null ? station.id() : null;
        UUID stationUid = station != null ? station.uid() : null;

        // Resolve account-level roles (e.g. admin)
        Set<Roles> roles = resolveAccountRoles(accountId);

        if (stationId != null) {
            Optional<StationMember> memberOpt = stationMemberRepository.findByStationAndAccount(stationId, accountId);
            if (memberOpt.isPresent()) {
                StationMember member = memberOpt.get();
                // Direct member roles
                List<Role> dbRoles = stationMemberRepository.findRoles(member.id());
                dbRoles.stream().map(Role::role).forEach(roles::add);
                // Roles inherited from groups
                List<Role> groupRoles = memberGroupRepository.findRolesForMemberViaGroups(member.id());
                groupRoles.stream().map(Role::role).forEach(roles::add);
                roles = Roles.expand(roles);
                return Optional.of(new UserSession(account, stationId, stationUid, member, roles));
            }
        }

        return Optional.of(new UserSession(account, stationId, stationUid, null, roles));
    }

    /**
     * Resolves the expanded roles for a station member by their member ID.
     */
    public Set<Roles> resolveExpandedMemberRoles(int memberId) {
        Set<Roles> roles = EnumSet.noneOf(Roles.class);
        List<Role> dbRoles = stationMemberRepository.findRoles(memberId);
        dbRoles.stream().map(Role::role).forEach(roles::add);
        List<Role> groupRoles = memberGroupRepository.findRolesForMemberViaGroups(memberId);
        groupRoles.stream().map(Role::role).forEach(roles::add);
        return Roles.expand(roles);
    }

    /**
     * Verifies federation signature headers on a request and returns the authenticated partner.
     * Checks the {@code X-Federation-Station-Id}, {@code X-Federation-Signature}, and
     * {@code X-Federation-Timestamp} headers against the partner's stored public key.
     * Also tracks the remote federation version if it changed.
     *
     * @param ctx the Javalin request context
     * @return the federation session if signature verification succeeds, or empty
     */
    public Optional<FederationSession> resolveFederationSession(Context ctx) {
        String stationIdHeader = ctx.header("X-Federation-Station-Id");
        String signature = ctx.header("X-Federation-Signature");
        String timestampHeader = ctx.header("X-Federation-Timestamp");

        if (stationIdHeader == null || signature == null || timestampHeader == null) {
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

        var partner = federationRepository.findPartnerByRemoteStationUid(remoteStationUid);
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

        var publicKey = signingService.decodePublicKey(p.partnerPublicKey());
        String body = ctx.body().isEmpty() ? ctx.queryString() != null ? ctx.queryString() : "" : ctx.body();
        boolean valid = signingService.verify(body, signature, publicKey, timestamp);
        if (!valid) {
            log.warn("Invalid federation signature from partner {} (station {})", p.id(), remoteStationUid);
            return Optional.empty();
        }

        // Track remote federation version
        String remoteVersion = ctx.header("X-Federation-Version");
        if (remoteVersion != null && !remoteVersion.equals(p.federationVersion())) {
            federationRepository.updateFederationVersion(p.id(), remoteVersion);
            if (!remoteVersion.equals(FederationService.FEDERATION_VERSION)) {
                log.warn(
                        "Federation partner {} (station {}) is running version {} (we are version {})",
                        p.id(),
                        p.partnerStationId(),
                        remoteVersion,
                        FederationService.FEDERATION_VERSION);
            }
        }

        return Optional.of(new FederationSession(p, remoteStationUid));
    }

    /**
     * Resolves account-level roles (e.g. ADMIN) from the database and maps them to the {@link Roles} enum.
     */
    private Set<Roles> resolveAccountRoles(int accountId) {
        Set<Roles> roles = EnumSet.noneOf(Roles.class);
        List<String> accountRoles = accountRepository.findAccountRoles(accountId);
        for (String role : accountRoles) {
            Roles mapped = Roles.fromDbName(role);
            if (mapped != null) {
                roles.add(mapped);
            }
        }
        return roles;
    }
}
