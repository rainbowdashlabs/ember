/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.SetupService.CompletionResult;
import dev.chojo.ember.feature.station.service.SetupService.SetupStatus;
import dev.chojo.ember.feature.station.service.SetupService.StepState;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SetupServiceTest extends RepositoryTestBase {
    private static SetupService setupService;

    private Station station;
    private int inviterMemberId;

    @BeforeAll
    static void setupServices() {
        StationService stationService = new StationService(
                stationRepo,
                stationMemberRepo,
                accountRepo,
                mock(FederationService.class),
                new StationMemberInviteService(
                        stationMemberRepo,
                        memberGroupRepo,
                        new AccountInviteService(accountRepo, mock(AuthService.class))),
                clusterRepo);
        setupService = new SetupService(
                stationRepo,
                stationMailProviderRepo,
                memberGroupRepo,
                eventRepo,
                knowledgeBaseRepo,
                stationMemberRepo,
                stationService);
    }

    @BeforeEach
    void freshStation() {
        station = stationRepo.create("Setup Station " + System.nanoTime());
        Account account = accountRepo.create("setup-" + System.nanoTime() + "@test.com", "Setup", "User");
        inviterMemberId = stationMemberRepo.create(station.id(), account.id()).id();
    }

    @Test
    void fresh_station_has_only_modules_complete_among_required_steps() {
        SetupStatus status = setupService.getStatus(station.id());
        assertNull(status.completedAt());

        Map<String, Boolean> required = byId(status.requiredSteps());
        assertEquals(
                Map.of(
                        SetupService.STEP_ADDRESS, false,
                        SetupService.STEP_MODULES, true,
                        SetupService.STEP_MEMBER_TYPES, true),
                required);

        Map<String, Boolean> optional = byId(status.optionalSteps());
        assertEquals(
                Map.of(
                        SetupService.STEP_GROUPS, false,
                        SetupService.STEP_MAIL, false,
                        SetupService.STEP_BRANDING, false,
                        SetupService.STEP_FIRST_EVENT, false,
                        SetupService.STEP_KB_SEED, false,
                        SetupService.STEP_FEDERATION, false,
                        SetupService.STEP_INVITES, false),
                optional);
    }

    @Test
    void address_step_completes_only_with_all_postal_fields_and_coordinates() {
        stationRepo.updateLocation(station.id(), "Main St 1", "12345", "Berlin", "DE", null, null);
        assertFalse(stepComplete(SetupService.STEP_ADDRESS));

        stationRepo.updateLocation(
                station.id(), "Main St 1", "12345", "Berlin", "DE", BigDecimal.valueOf(52.5), BigDecimal.valueOf(13.4));
        assertTrue(stepComplete(SetupService.STEP_ADDRESS));
    }

    @Test
    void member_types_step_is_always_complete_because_enum_defaults_apply() {
        assertTrue(stepComplete(SetupService.STEP_MEMBER_TYPES));

        var loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        stationMemberRepo.setUserTypePermissions(station.id(), StationUserType.MEMBER, List.of(loginPerm.id()));

        assertTrue(stepComplete(SetupService.STEP_MEMBER_TYPES));
    }

    @Test
    void groups_step_completes_when_at_least_one_group_exists() {
        assertFalse(optionalComplete(SetupService.STEP_GROUPS));
        memberGroupRepo.create(station.id(), "All members");
        assertTrue(optionalComplete(SetupService.STEP_GROUPS));
    }

    @Test
    void mail_step_completes_when_the_station_lists_a_provider() {
        assertFalse(optionalComplete(SetupService.STEP_MAIL));

        stationMailProviderRepo.replace(
                station.id(),
                List.of(new MailChainEntry(
                        0,
                        MailProviderType.SMTP,
                        "smtp.example.com",
                        587,
                        false,
                        "user",
                        "pw",
                        "",
                        "hello@example.com",
                        "Test",
                        2,
                        0,
                        "",
                        "")));

        assertTrue(optionalComplete(SetupService.STEP_MAIL));
    }

    @Test
    void branding_step_completes_when_logo_or_custom_theme_present() {
        assertFalse(optionalComplete(SetupService.STEP_BRANDING));

        stationRepo.updateLogo(station.id(), new byte[] {1, 2, 3}, "image/png");
        assertTrue(optionalComplete(SetupService.STEP_BRANDING));
    }

    @Test
    void first_event_step_not_applicable_when_events_module_disabled() {
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.EVENTS));
        StepState step = findStep(setupService.getStatus(station.id()).optionalSteps(), SetupService.STEP_FIRST_EVENT);
        assertFalse(step.applicable());
        assertFalse(step.complete());
    }

    @Test
    void kb_seed_step_not_applicable_when_kb_module_disabled() {
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.KNOWLEDGE_BASE));
        StepState step = findStep(setupService.getStatus(station.id()).optionalSteps(), SetupService.STEP_KB_SEED);
        assertFalse(step.applicable());
        assertFalse(step.complete());
    }

    @Test
    void invites_step_completes_when_another_member_exists() {
        assertFalse(optionalComplete(SetupService.STEP_INVITES));
        Account invited = accountRepo.create("invited-" + System.nanoTime() + "@test.com", "Invited", "User");
        stationMemberRepo.create(station.id(), invited.id());
        assertTrue(optionalComplete(SetupService.STEP_INVITES));
    }

    @Test
    void federation_step_completes_when_discovery_description_is_set() {
        assertFalse(optionalComplete(SetupService.STEP_FEDERATION));
        stationRepo.updateDiscoverySettings(station.id(), DiscoveryVisibility.PUBLIC, "Welcome to our station", false);
        assertTrue(optionalComplete(SetupService.STEP_FEDERATION));
    }

    @Test
    void complete_returns_missing_when_required_steps_are_open() {
        assertEquals(CompletionResult.MISSING_REQUIRED_STEPS, setupService.complete(station.id()));
        assertNull(setupService.getStatus(station.id()).completedAt());
        List<String> missing = setupService.findMissingRequiredSteps(station.id());
        assertTrue(missing.contains(SetupService.STEP_ADDRESS));
        assertFalse(missing.contains(SetupService.STEP_MEMBER_TYPES));
        assertFalse(missing.contains(SetupService.STEP_GROUPS));
        assertFalse(missing.contains(SetupService.STEP_MODULES));
    }

    @Test
    void complete_marks_timestamp_once_required_steps_pass_and_is_idempotent() {
        satisfyAllRequired();

        CompletionResult first = setupService.complete(station.id());
        assertEquals(CompletionResult.COMPLETED, first);

        Instant stamped = setupService.getStatus(station.id()).completedAt();
        assertNotNull(stamped);

        CompletionResult second = setupService.complete(station.id());
        assertEquals(CompletionResult.ALREADY_COMPLETE, second);
        assertEquals(stamped, setupService.getStatus(station.id()).completedAt());

        assertTrue(setupService.findMissingRequiredSteps(station.id()).isEmpty());
    }

    @Test
    void unknown_station_throws() {
        assertThrows(NoSuchElementException.class, () -> setupService.getStatus(999_999));
    }

    private boolean stepComplete(String id) {
        return findStep(setupService.getStatus(station.id()).requiredSteps(), id)
                .complete();
    }

    private boolean optionalComplete(String id) {
        return findStep(setupService.getStatus(station.id()).optionalSteps(), id)
                .complete();
    }

    private static StepState findStep(List<StepState> steps, String id) {
        return steps.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static Map<String, Boolean> byId(List<StepState> steps) {
        return steps.stream().collect(Collectors.toMap(StepState::id, StepState::complete));
    }

    private void satisfyAllRequired() {
        stationRepo.updateLocation(
                station.id(), "Main St 1", "12345", "Berlin", "DE", BigDecimal.valueOf(52.5), BigDecimal.valueOf(13.4));
        var loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        stationMemberRepo.setUserTypePermissions(station.id(), StationUserType.MEMBER, List.of(loginPerm.id()));
        memberGroupRepo.create(station.id(), "All members");
    }
}
