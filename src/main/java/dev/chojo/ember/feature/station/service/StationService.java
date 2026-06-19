/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.ManagerInfo;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import dev.chojo.ember.util.SlugGenerator;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for station management including CRUD, logo handling, manager assignment,
 * ownership transfer, and module configuration.
 */
@Singleton
public class StationService {
    private static final Logger log = LoggerFactory.getLogger(StationService.class);
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2 MB

    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final FederationService federationService;
    private final Map<Integer, Optional<StationLogo>> logoCache = new ConcurrentHashMap<>();

    @Inject
    public StationService(
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            AuthService authService,
            FederationService federationService) {
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.federationService = federationService;
    }

    /**
     * Retrieves all stations.
     *
     * @return a list of all stations
     */
    public List<Station> findAll() {
        return stationRepository.findAll();
    }

    /**
     * Finds a station by its ID.
     *
     * @param id the station ID
     * @return the station, or empty if not found
     */
    public Optional<Station> findById(int id) {
        return stationRepository.findById(id);
    }

    public Optional<Station> findByUid(UUID uid) {
        return stationRepository.findByUid(uid);
    }

    public boolean updatePublicKbMode(int stationId, PublicKbMode mode) {
        return stationRepository.updatePublicKbMode(stationId, mode);
    }

    /**
     * Creates a new station with the given name.
     *
     * @param name the station name
     * @return the created station
     */
    public Station create(String name) {
        var station = stationRepository.create(name);
        // Ensure every station has a federation private key
        var keyPair = federationService.generateKeyPair();
        stationRepository.updateFederationPrivateKey(station.id(), federationService.encodePrivateKey(keyPair));
        // Auto-generate a public slug from the station name
        var slug = SlugGenerator.uniqueSlug(
                name, (s, _) -> stationRepository.findBySlug(s).isPresent(), 0);
        stationRepository.updatePublicSlug(station.id(), slug);
        log.info("Station created: id={}, name='{}'", station.id(), name);
        return stationRepository.findById(station.id()).orElse(station);
    }

    /**
     * Creates a new station and assigns a manager by email.
     *
     * @param name         the station name
     * @param managerEmail the email of the manager to assign
     * @return the created station
     */
    public Station createWithManager(String name, String managerEmail) {
        var station = stationRepository.create(name);
        assignManager(station.id(), managerEmail);
        log.info("Station created with manager: id={}, name='{}', manager='{}'", station.id(), name, managerEmail);
        return station;
    }

    /**
     * Updates the name of a station.
     *
     * @param id   the station ID
     * @param name the new name
     * @return the updated station, or empty if not found
     */
    public Optional<Station> update(int id, String name) {
        if (stationRepository.update(id, name)) {
            return stationRepository.findById(id);
        }
        return Optional.empty();
    }

    /**
     * Updates the timezone of a station.
     *
     * @param id       the station ID
     * @param timezone the IANA timezone identifier
     * @return the updated station, or empty if not found
     */
    public Optional<Station> updateTimezone(int id, String timezone) {
        if (stationRepository.updateTimezone(id, timezone)) {
            return stationRepository.findById(id);
        }
        return Optional.empty();
    }

    /**
     * Updates the locale of a station.
     *
     * @param id     the station ID
     * @param locale the locale string (e.g., "de-DE")
     * @return the updated station, or empty if not found
     */
    public Optional<Station> updateLocale(int id, String locale) {
        if (stationRepository.updateLocale(id, locale)) {
            return stationRepository.findById(id);
        }
        return Optional.empty();
    }

    public void updateThemeSettings(
            int id,
            String defaultTheme,
            boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean allowUserFeel) {
        stationRepository.updateThemeSettings(
                id, defaultTheme, allowUserTheme, customThemeColors, defaultFeel, allowUserFeel);
    }

    /**
     * Updates a station's name and assigns a manager by email.
     *
     * @param id           the station ID
     * @param name         the new name
     * @param managerEmail the email of the manager to assign
     * @return the updated station, or empty if not found
     */
    public Optional<Station> updateWithManager(int id, String name, String managerEmail) {
        if (!stationRepository.update(id, name)) {
            return Optional.empty();
        }
        assignManager(id, managerEmail);
        return stationRepository.findById(id);
    }

    /**
     * Deletes a station by its ID.
     *
     * @param id the station ID
     * @return {@code true} if the station was deleted
     */
    public boolean delete(int id) {
        log.info("Station deleted: id={}", id);
        return stationRepository.delete(id);
    }

    /**
     * Finds detailed manager information for a station, including account readiness status.
     *
     * @param stationId the station ID
     * @return the manager info, or empty if no manager is found
     */
    public Optional<ManagerInfo> findManagerInfo(int stationId) {
        Permission managerRole = memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElse(null);
        if (managerRole == null) return Optional.empty();

        for (StationMember member : memberRepository.findByStation(stationId)) {
            List<Permission> roles = memberRepository.findPermissions(member.id());
            if (roles.stream().anyMatch(r -> r.id() == managerRole.id())) {
                Account account = accountRepository.findById(member.accountId()).orElse(null);
                if (account == null) continue;
                Optional<AccountCredential> credential = accountRepository.findCredential(account.id());
                boolean hasPassword = credential.isPresent();
                boolean accountReady =
                        hasPassword && !credential.get().forcePasswordChange() && account.emailVerified();
                return Optional.of(
                        new ManagerInfo(account.email(), account.firstName(), account.lastName(), accountReady));
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves the logo for a station, using an in-memory cache.
     *
     * @param stationId the station ID
     * @return the logo, or empty if no logo is set
     */
    public Optional<StationLogo> getLogo(int stationId) {
        return logoCache.computeIfAbsent(stationId, stationRepository::findLogo);
    }

    /**
     * Sets the logo for a station.
     *
     * @param stationId   the station ID
     * @param data        the logo image data
     * @param contentType the MIME content type of the logo
     * @throws IllegalArgumentException if the data exceeds the maximum size
     */
    public void setLogo(int stationId, byte[] data, String contentType) {
        if (data.length > MAX_LOGO_SIZE) {
            throw new BadRequestResponse("Logo exceeds maximum size of 2 MB");
        }
        stationRepository.updateLogo(stationId, data, contentType);
        logoCache.put(stationId, Optional.of(new StationLogo(data, contentType)));
    }

    // -- Logo --

    /**
     * Removes the logo from a station and clears the cache.
     *
     * @param stationId the station ID
     */
    public void deleteLogo(int stationId) {
        stationRepository.deleteLogo(stationId);
        logoCache.put(stationId, Optional.empty());
    }

    /**
     * Transfers station ownership to another member who already has the MANAGER role.
     * Only the current owner can call this.
     */
    public boolean transferOwnership(int stationId, int currentMemberId, int newOwnerMemberId) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return false;
        if (station.ownerMemberId() == null || station.ownerMemberId() != currentMemberId) return false;

        // Verify the target has the MANAGER role
        Permission managerRole = memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElse(null);
        if (managerRole == null) return false;
        var targetRoles = memberRepository.findPermissions(newOwnerMemberId);
        if (targetRoles.stream().noneMatch(r -> r.id() == managerRole.id())) return false;

        stationRepository.setOwner(stationId, newOwnerMemberId);
        log.info("Station ownership transferred: station={}, from member {} to member {}", stationId, currentMemberId, newOwnerMemberId);
        return true;
    }

    /**
     * Checks whether a member is the owner of a station.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @return {@code true} if the member is the station owner
     */
    public boolean isOwner(int stationId, int memberId) {
        var station = stationRepository.findById(stationId).orElse(null);
        return station != null && station.ownerMemberId() != null && station.ownerMemberId() == memberId;
    }

    /**
     * Retrieves the set of disabled module names for a station.
     *
     * @param stationId the station ID
     * @return the set of disabled module names
     */
    public Set<StationModule> findDisabledModules(int stationId) {
        return stationRepository.findDisabledModules(stationId);
    }

    // -- Modules --

    /**
     * Replaces all disabled modules for a station with the given set.
     */
    public void setDisabledModules(int stationId, Set<StationModule> modules) {
        stationRepository.setDisabledModules(stationId, modules);
        log.info("Station modules updated: station={}, disabled={}", stationId, modules);
    }

    /**
     * Checks whether a module is enabled for a station.
     */
    public boolean isModuleEnabled(int stationId, StationModule module) {
        return !stationRepository.findDisabledModules(stationId).contains(module);
    }

    /**
     * Updates the discovery settings for a station.
     */
    public void updatePublicCalendarEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicCalendarEnabled(stationId, enabled);
    }

    public void updatePublicPagesEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicPagesEnabled(stationId, enabled);
    }

    public void updatePublicWaitlistEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicWaitlistEnabled(stationId, enabled);
    }

    public void updatePublicBlogEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicBlogEnabled(stationId, enabled);
    }

    public void updatePublicSlug(int stationId, String slug) {
        if (slug != null) {
            var existing = stationRepository.findBySlug(slug);
            if (existing.isPresent() && existing.get().id() != stationId) {
                throw new BadRequestResponse("Slug is already in use");
            }
        }
        stationRepository.updatePublicSlug(stationId, slug);
    }

    public void updateDiscoverySettings(
            int stationId, DiscoveryVisibility visibility, String description, boolean showKb) {
        stationRepository.updateDiscoverySettings(stationId, visibility, description, showKb);
    }

    /**
     * Finds all stations discoverable by the given station (instance-level visibility).
     */
    public List<Station> findWithPublicContent(int excludeStationId) {
        return stationRepository.findWithPublicContent(excludeStationId);
    }

    public List<Station> findDiscoverable(int excludeStationId) {
        return stationRepository.findDiscoverable(
                excludeStationId, DiscoveryVisibility.INSTANCE, DiscoveryVisibility.PUBLIC);
    }

    /**
     * Assigns the MANAGER role to the given email and sets them as station owner if no owner exists yet.
     */
    private void assignManager(int stationId, String managerEmail) {
        Permission managerRole = memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow(() -> new IllegalStateException("manager role not found"));

        // Find or invite account
        Optional<Account> accountOpt = accountRepository.findByEmail(managerEmail);
        Account account;
        if (accountOpt.isPresent()) {
            account = accountOpt.get();
        } else {
            var result = authService.createInvitedAccount(managerEmail, "", "", stationId);
            if (!result.success()) {
                throw new IllegalStateException("Failed to invite account: " + result.message());
            }
            account = result.account();
        }

        // Ensure membership
        StationMember member = memberRepository
                .findByStationAndAccount(stationId, account.id())
                .orElseGet(() -> memberRepository.create(stationId, account.id()));

        // Assign manager role
        List<Permission> currentRoles = memberRepository.findPermissions(member.id());
        if (currentRoles.stream().noneMatch(r -> r.id() == managerRole.id())) {
            memberRepository.grantPermission(member.id(), managerRole.id());
        }

        // Set as owner if no owner exists yet
        var station = stationRepository.findById(stationId).orElse(null);
        if (station != null && station.ownerMemberId() == null) {
            stationRepository.setOwner(stationId, member.id());
        }
    }
}
