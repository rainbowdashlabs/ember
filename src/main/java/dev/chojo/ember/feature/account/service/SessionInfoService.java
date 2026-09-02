/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.mail.service.MailChainService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.service.StationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles the aggregated session view served to the authenticated user: account identity,
 * permissions, managed members, groups, tags, profile completeness, disabled modules and the
 * resolved theming for the currently selected station.
 */
@Singleton
public class SessionInfoService {
    private final StationService stationService;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final ProfileFieldService profileFieldService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserTagRepository userTagRepository;
    private final MailChainService mailChainService;
    private final File config;

    @Inject
    public SessionInfoService(
            StationService stationService,
            StationMemberService memberService,
            MemberGroupService groupService,
            ProfileFieldService profileFieldService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            UserSettingsRepository userSettingsRepository,
            UserTagRepository userTagRepository,
            MailChainService mailChainService,
            File config) {
        this.stationService = stationService;
        this.memberService = memberService;
        this.groupService = groupService;
        this.profileFieldService = profileFieldService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userTagRepository = userTagRepository;
        this.mailChainService = mailChainService;
        this.config = config;
    }

    /**
     * Builds the full session response for the given session.
     *
     * @param session the authenticated session
     * @return the aggregated session information
     */
    public SessionInfo describe(UserSession session) {
        List<StationMember> managed = List.of();
        List<MemberGroup> groups = List.of();
        List<UserTag> tags = List.of();
        List<Integer> roleIds = List.of();
        List<Integer> groupIds = List.of();
        List<Integer> tagIds = List.of();
        MemberInfo memberInfo = null;

        if (session.member() != null) {
            managed = memberService.findManaged(session.member().id());
            groups = groupService.findGroupsForMember(session.member().id());
            tags = userTagRepository.findTagsForMember(session.member().id());
            roleIds = stationMemberRepository.findPermissions(session.member().id()).stream()
                    .map(Permission::id)
                    .toList();
            groupIds = groups.stream().map(MemberGroup::id).toList();
            tagIds = tags.stream().map(UserTag::id).toList();
            memberInfo = new MemberInfo(
                    session.member().id(),
                    session.stationUid() != null ? session.stationUid().toString() : null,
                    session.member().accountId(),
                    session.member().uid());
        }

        var roleNames = session.permissions().stream().map(Enum::name).sorted().toList();
        boolean profileComplete = true;
        if (session.member() != null && session.stationId() != null) {
            profileComplete =
                    profileFieldService.isProfileComplete(session.member().id());
        }

        var managedInfos = managed.stream().map(this::toManagedMemberInfo).toList();

        var disabledModules = session.stationId() != null
                ? stationService.findEffectiveDisabledModules(session.stationId())
                : Set.<StationModule>of();

        Station currentStation = session.stationId() != null
                ? stationService.findById(session.stationId()).orElse(null)
                : null;

        return new SessionInfo(
                new AccountInfo(
                        session.account().id(),
                        session.account().uid() != null
                                ? session.account().uid().toString()
                                : null,
                        session.account().email(),
                        session.account().username(),
                        session.account().firstName(),
                        session.account().lastName()),
                session.stationUid() != null ? session.stationUid().toString() : null,
                memberInfo,
                roleNames,
                session.userType(),
                session.instanceUserType(),
                managedInfos,
                groups,
                tags,
                roleIds,
                groupIds,
                tagIds,
                profileComplete,
                disabledModules,
                resolveTheme(session, currentStation),
                currentStation != null ? currentStation.publicKbMode() : null,
                currentStation != null ? currentStation.setupCompletedAt() : null,
                session.clusterUid() != null ? session.clusterUid().toString() : null,
                session.clusterUserType(),
                session.clusterPermissions().stream().map(Enum::name).sorted().toList(),
                ClusterPermission.atOwnStation(session.clusterPermissions()).stream()
                        .map(Enum::name)
                        .sorted()
                        .toList(),
                !mailChainService.forInstance().isEmpty());
    }

    private ManagedMemberInfo toManagedMemberInfo(StationMember member) {
        Account account = member.accountId() != null
                ? accountRepository.findById(member.accountId()).orElse(null)
                : null;
        String name = account != null
                ? (account.firstName() + " " + account.lastName()).trim()
                : (member.displayName() != null ? member.displayName() : "");
        String email = account != null ? account.email() : "";
        var managedStation = stationService.findById(member.stationId()).orElse(null);
        UUID managedStationUid = managedStation != null ? managedStation.uid() : null;
        return new ManagedMemberInfo(
                member.id(),
                managedStationUid,
                member.uid(),
                member.accountId() != null ? member.accountId() : 0,
                name,
                email);
    }

    /**
     * Resolves the effective theming for the session: instance defaults combined with the current
     * station's defaults and the member's personal preferences. Falls back to the instance defaults
     * when the session has no member or no resolvable station.
     */
    private ThemeInfo resolveTheme(UserSession session, Station currentStation) {
        var theming = config.theming();
        if (session.member() != null && session.stationId() != null) {
            var userSettings =
                    userSettingsRepository.findOrCreate(session.member().id());
            if (currentStation != null) {
                return new ThemeInfo(
                        theming.defaultTheme(),
                        theming.defaultFeel(),
                        theming.lockFeel(),
                        currentStation.defaultTheme(),
                        currentStation.defaultFeel(),
                        currentStation.allowUserTheme(),
                        currentStation.allowUserFeel(),
                        currentStation.customThemeColors(),
                        userSettings.theme(),
                        userSettings.darkMode(),
                        userSettings.feel());
            }
        }
        return new ThemeInfo(
                theming.defaultTheme(),
                theming.defaultFeel(),
                theming.lockFeel(),
                "ember",
                ThemeFeel.ROUNDED,
                true,
                true,
                null,
                null,
                null,
                null);
    }

    /**
     * Aggregated session information returned to the authenticated user.
     *
     * @param account           the account details
     * @param stationId         the currently selected station, or {@code null} if none
     * @param member            the station membership info, or {@code null} if not a member
     * @param permissions       sorted list of permission names for the current station
     * @param managedMembers    list of members managed by this account
     * @param groups            groups the current member belongs to
     * @param profileComplete   whether all required profile fields are filled
     * @param disabledModules   set of modules disabled for the current station
     * @param setupCompletedAt  timestamp at which the station setup wizard was finished, or
     *                          {@code null} while the wizard still applies. Drives the first-login
     *                          redirect to {@code /station/setup} for administrators
     * @param ownStationPermissions what the caller may do at the station their association owns, where its
     *                          knowledge base, news and calendar are kept. Those screens are the station's
     *                          own, so they ask what the reader may do at a station, and while one of them
     *                          is open on the association's side this is the answer
     * @param canSendMail       whether this instance has anywhere to send system mail through at all.
     *                          Invitations, setup links and password resets all leave through the
     *                          instance rather than through a station's own provider, so the answer is
     *                          the same everywhere and a screen offering to send one can ask it here.
     *                          It says nothing about whether a particular person can be reached, which
     *                          is a question about addresses and is answered per member
     */
    public record SessionInfo(
            AccountInfo account,
            String stationId,
            MemberInfo member,
            List<String> permissions,
            StationUserType userType,
            InstanceUserType instanceUserType,
            List<ManagedMemberInfo> managedMembers,
            List<MemberGroup> groups,
            List<UserTag> tags,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            boolean profileComplete,
            Set<StationModule> disabledModules,
            ThemeInfo theme,
            PublicKbMode publicKbMode,
            Instant setupCompletedAt,
            String clusterId,
            ClusterUserType clusterUserType,
            List<String> clusterPermissions,
            List<String> ownStationPermissions,
            boolean canSendMail) {}

    public record ThemeInfo(
            String instanceDefaultTheme,
            ThemeFeel instanceDefaultFeel,
            boolean instanceLockFeel,
            String defaultTheme,
            ThemeFeel defaultFeel,
            boolean allowUserTheme,
            boolean allowUserFeel,
            String customThemeColors,
            String userTheme,
            String userDarkMode,
            String userFeel) {}

    /**
     * Summary of a member managed by the current account.
     *
     * @param id        the member identifier
     * @param stationId the station identifier
     * @param accountId the member's account identifier, or 0 if none
     * @param name      the member's display name
     * @param email     the member's email, or empty string if unavailable
     */
    public record ManagedMemberInfo(int id, UUID stationId, UUID uid, int accountId, String name, String email) {}

    /**
     * Account information included in the session response.
     *
     * @param id        the account identifier
     * @param email     the email address
     * @param firstName the first name
     * @param lastName  the last name
     */
    /**
     * @param username the name this account signs in with, or null when its address is the only way in
     */
    public record AccountInfo(int id, String uid, String email, String username, String firstName, String lastName) {}

    /**
     * Minimal member information for the current session.
     *
     * @param id        the member identifier
     * @param stationId the station identifier
     * @param accountId the account identifier
     */
    public record MemberInfo(int id, String stationId, int accountId, UUID uid) {}
}
