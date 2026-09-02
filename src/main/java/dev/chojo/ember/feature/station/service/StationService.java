/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.ManagerInfo;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.SlugGenerator;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Service for station management including CRUD, manager assignment, ownership transfer,
 * and module configuration.
 */
@Singleton
public class StationService {
    private static final Logger log = LoggerFactory.getLogger(StationService.class);

    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final FederationService federationService;
    private final StationMemberInviteService inviteService;
    private final ClusterRepository clusterRepository;

    @Inject
    public StationService(
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            FederationService federationService,
            StationMemberInviteService inviteService,
            ClusterRepository clusterRepository) {
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.federationService = federationService;
        this.inviteService = inviteService;
        this.clusterRepository = clusterRepository;
    }

    /**
     * The stations of the instance.
     *
     * <p>A cluster's own station is not one of them. It exists so a cluster has somewhere to keep its
     * things and it is not a station anybody runs: offering it here would offer somebody the chance to
     * rename or delete the identity a cluster is built on.
     *
     * @return the stations somebody actually runs
     */
    public List<Station> findAll() {
        return stationRepository.findAllRegular();
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
        boolean updated = stationRepository.updatePublicKbMode(stationId, mode);
        if (updated) log.info("Station {} set its public knowledge base to {}", stationId, mode);
        else log.warn("Public knowledge base mode of station {} affected zero rows", stationId);
        return updated;
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
            log.info("Station renamed: id={}, name='{}'", id, name);
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
            log.info("Station {} now keeps time in {}", id, timezone);
            return stationRepository.findById(id);
        }
        log.warn("Timezone update for station {} affected zero rows", id);
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
            log.info("Station {} now speaks {}", id, locale);
            return stationRepository.findById(id);
        }
        log.warn("Locale update for station {} affected zero rows", id);
        return Optional.empty();
    }

    public void updateThemeSettings(
            int id,
            String defaultTheme,
            boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean allowUserFeel) {
        Station current = stationRepository.findById(id).orElseThrow(NotFoundResponse::new);
        Locks locks = lookAndFeelLocks(id);
        // A locked setting keeps whatever the cluster last wrote, whatever the station sent. Refusing the
        // whole save instead would stop a station changing the parts it may still change, which are on the
        // same screen and in the same request.
        stationRepository.updateThemeSettings(
                id,
                locks.theme() ? current.defaultTheme() : defaultTheme,
                allowUserTheme,
                locks.colors() ? current.customThemeColors() : customThemeColors,
                locks.feel() ? current.defaultFeel() : defaultFeel,
                allowUserFeel);
        log.info(
                "Station {} changed its look, with theme={}, colors={} and feel={} locked by its cluster",
                id,
                locks.theme(),
                locks.colors(),
                locks.feel());
    }

    /**
     * What a station's cluster has taken out of its hands.
     *
     * <p>Whether members may pick their own theme is not among them: that is a question about the people at
     * the station rather than about how the cluster wants to look, and it stays the station's either way.
     *
     * @param stationId the station
     * @return what is locked, all false when the station answers to no cluster
     */
    public Locks lookAndFeelLocks(int stationId) {
        return clusterRepository
                .findByStation(stationId)
                .map(cluster -> new Locks(
                        cluster.themeLocked(), cluster.colorsLocked(), cluster.feelLocked(), cluster.logoLocked()))
                .orElseGet(() -> new Locks(false, false, false, false));
    }

    /**
     * The name of the cluster a station answers to, for a screen that has to say who locked something.
     *
     * @param stationId the station
     * @return the cluster's name, or empty when it answers to nobody
     */
    public Optional<String> clusterNameOf(int stationId) {
        return clusterRepository.findByStation(stationId).map(Cluster::name);
    }

    /**
     * The look-and-feel settings a station may not change itself.
     *
     * @param theme  the colour theme
     * @param colors the colour set
     * @param feel   the interface feel
     * @param logo   the station's logo
     */
    public record Locks(boolean theme, boolean colors, boolean feel, boolean logo) {}

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
        log.info("Station updated with manager: id={}, name='{}'", id, name);
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
     * <p>A station can hold several administrators, and naming a new manager leaves the previous
     * one's role in place. The owner is therefore the answer, and any other administrator only
     * stands in while the station has none: reading the first administrator the station happens to
     * list would name whoever was there longest and never the person just put in charge.
     *
     * @param stationId the station ID
     * @return the manager info, or empty if no manager is found
     */
    public Optional<ManagerInfo> findManagerInfo(int stationId) {
        Permission managerRole = memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElse(null);
        if (managerRole == null) return Optional.empty();

        Integer ownerMemberId = stationRepository
                .findById(stationId)
                .map(Station::ownerMemberId)
                .orElse(null);
        List<StationMember> administrators = memberRepository.findByStation(stationId).stream()
                .filter(member -> holdsRole(member.id(), managerRole))
                .toList();

        return Stream.concat(
                        administrators.stream().filter(member -> isSameMember(member, ownerMemberId)),
                        administrators.stream().filter(member -> !isSameMember(member, ownerMemberId)))
                .map(this::managerInfoOf)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static boolean isSameMember(StationMember member, Integer memberId) {
        return memberId != null && member.id() == memberId;
    }

    /**
     * Reads what a screen shows about a manager, or nothing at all for a member who has no account
     * behind them any more. A former member keeps their row after being decoupled from their
     * account, and a decoupled member who still holds the administrator role would otherwise be
     * asked for an account that is not there.
     */
    private Optional<ManagerInfo> managerInfoOf(StationMember member) {
        if (member.accountId() == null) return Optional.empty();
        Account account = accountRepository.findById(member.accountId()).orElse(null);
        if (account == null) return Optional.empty();
        Optional<AccountCredential> credential = accountRepository.findCredential(account.id());
        boolean accountReady = credential
                .map(c -> !c.forcePasswordChange() && account.emailVerified())
                .orElse(false);
        return Optional.of(new ManagerInfo(account.email(), account.firstName(), account.lastName(), accountReady));
    }

    /**
     * Transfers station ownership to another member who already has the MANAGER role.
     * Only the current owner can call this.
     */
    public boolean transferOwnership(int stationId, int currentMemberId, int newOwnerMemberId) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return false;
        if (station.ownerMemberId() == null || station.ownerMemberId() != currentMemberId) return false;

        Permission managerRole = memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElse(null);
        if (managerRole == null) return false;
        if (!holdsRole(newOwnerMemberId, managerRole)) return false;

        stationRepository.setOwner(stationId, newOwnerMemberId);
        log.info(
                "Station ownership transferred: station={}, from member {} to member {}",
                stationId,
                currentMemberId,
                newOwnerMemberId);
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

    /**
     * Every module the station does not have, whoever switched it off.
     *
     * <p>What the shell has to go by. A module its cluster denied is as gone as one the station switched
     * off itself: leaving it out of this list left the sidebar offering a page that refuses whoever follows
     * it. The management screen still asks for the two lists apart, because there it matters who decided.
     *
     * @param stationId the station
     * @return the station's own set and its cluster's, together
     */
    public Set<StationModule> findEffectiveDisabledModules(int stationId) {
        Set<StationModule> disabled = EnumSet.noneOf(StationModule.class);
        disabled.addAll(stationRepository.findDisabledModules(stationId));
        disabled.addAll(findClusterDeniedModules(stationId));
        return disabled;
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
        // A cluster's denial outranks the station's own answer, whichever way that answer went
        if (clusterRepository.isModuleDeniedForStation(stationId, module)) return false;
        return !stationRepository.findDisabledModules(stationId).contains(module);
    }

    /**
     * The modules the station's cluster has switched off, which it cannot turn back on.
     *
     * <p>Separate from {@link #isModuleEnabled(int, StationModule)} because the station's own screen has to
     * show them differently: locked with the cluster named, rather than simply off.
     *
     * @param stationId the station
     * @return what its cluster denies, empty when it answers to no cluster
     */
    public Set<StationModule> findClusterDeniedModules(int stationId) {
        return clusterRepository.findDeniedModulesForStation(stationId);
    }

    /**
     * Updates the discovery settings for a station.
     */
    public void updatePublicCalendarEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicCalendarEnabled(stationId, enabled);
        log.info("Station {} turned its public calendar {}", stationId, enabled ? "on" : "off");
    }

    public void updatePublicPagesEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicPagesEnabled(stationId, enabled);
        log.info("Station {} turned its public pages {}", stationId, enabled ? "on" : "off");
    }

    public void updatePublicWaitlistEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicWaitlistEnabled(stationId, enabled);
        log.info("Station {} turned its public waiting list {}", stationId, enabled ? "on" : "off");
    }

    public void updatePublicBlogEnabled(int stationId, boolean enabled) {
        stationRepository.updatePublicBlogEnabled(stationId, enabled);
        log.info("Station {} turned its public blog {}", stationId, enabled ? "on" : "off");
    }

    public void updatePublicSlug(int stationId, String slug) {
        if (slug != null) {
            var existing = stationRepository.findBySlug(slug);
            if (existing.isPresent() && existing.get().id() != stationId) {
                throw new BadRequestResponse("Slug is already in use");
            }
        }
        stationRepository.updatePublicSlug(stationId, slug);
        log.info("Station {} is reached at the slug '{}'", stationId, slug);
    }

    public void updateDiscoverySettings(
            int stationId, DiscoveryVisibility visibility, String description, boolean showKb) {
        stationRepository.updateDiscoverySettings(stationId, visibility, description, showKb);
        log.info(
                "Station {} is discoverable as {} and {} its knowledge base",
                stationId,
                visibility,
                showKb ? "shows" : "hides");
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
     * Finds stations discoverable without being signed in - only stations that opted into
     * public visibility.
     */
    public List<Station> findPubliclyDiscoverable(int excludeStationId) {
        return stationRepository.findDiscoverable(
                excludeStationId, DiscoveryVisibility.PUBLIC, DiscoveryVisibility.PUBLIC);
    }

    /**
     * Assigns the MANAGER role to the given email and hands the station to them. The account and
     * membership are provisioned immediately when missing; a new account receives a password-setup
     * email.
     *
     * <p>Naming somebody the manager of a station that already has one moves the ownership, because
     * that is what naming a manager means: only the owner may hand the station on again or take it
     * into a cluster, so a station whose ownership stayed behind cannot be handed over at all. The
     * previous manager keeps their administrator role and their membership, so nobody loses their
     * way into a station by somebody else being named.
     */
    private void assignManager(int stationId, String managerEmail) {
        Permission managerRole = memberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow(() -> new IllegalStateException("manager role not found"));

        var provisioned = inviteService.provision(
                stationId, managerEmail, "", "", StationUserType.MANAGER, null, SetupMail.SEND_NOW);
        int memberId = provisioned.memberId();

        if (!holdsRole(memberId, managerRole)) {
            memberRepository.grantPermission(memberId, managerRole.id());
        }

        stationRepository.setOwner(stationId, memberId);
    }

    private boolean holdsRole(int memberId, Permission role) {
        return memberRepository.findPermissions(memberId).stream().anyMatch(r -> r.id() == role.id());
    }
}
