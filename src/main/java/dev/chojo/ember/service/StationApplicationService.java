/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.entity.StationApplication;
import dev.chojo.ember.entity.TokenType;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationApplicationRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class StationApplicationService {
    private final StationApplicationRepository applicationRepository;
    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final EmailService emailService;

    @Inject
    public StationApplicationService(
            StationApplicationRepository applicationRepository,
            StationRepository stationRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            EmailService emailService) {
        this.applicationRepository = applicationRepository;
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.emailService = emailService;
    }

    public StationApplication submit(
            String firstName, String lastName, String email, String stationName, String introduction) {
        String token = UUID.randomUUID().toString();
        var application = applicationRepository.create(firstName, lastName, email, stationName, introduction, token);
        emailService.sendApplicationVerifyEmail(email, firstName, stationName, token, "de");
        return application;
    }

    public boolean verify(String token) {
        var application = applicationRepository.findByToken(token);
        if (application.isEmpty()) return false;
        return applicationRepository.verify(application.get().id());
    }

    public List<StationApplication> findAll() {
        return applicationRepository.findAll();
    }

    public List<StationApplication> findPending() {
        return applicationRepository.findByStatus("pending");
    }

    public Optional<StationApplication> findById(int id) {
        return applicationRepository.findById(id);
    }

    public StationApplication accept(int id) {
        var application = applicationRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!"pending".equals(application.status())) {
            throw new IllegalStateException("Application is not pending");
        }

        applicationRepository.accept(id);

        // Create the station
        var station = stationRepository.create(application.stationName());

        // Create account (pre-verified since admin approved)
        var account =
                accountRepository.create(application.email(), application.firstName(), application.lastName(), true);

        // Create station member
        var member = stationMemberRepository.create(station.id(), account.id());

        // Assign manager role
        var managerRole = stationMemberRepository
                .findRoleByName(Roles.MANAGER)
                .orElseThrow(() -> new IllegalStateException("Manager role not found"));
        stationMemberRepository.addRole(member.id(), managerRole.id());

        // Also assign login role
        var loginRole = stationMemberRepository
                .findRoleByName(Roles.LOGIN)
                .orElseThrow(() -> new IllegalStateException("Login role not found"));
        stationMemberRepository.addRole(member.id(), loginRole.id());

        // Send acceptance email and password setup email
        String token = UUID.randomUUID().toString();
        accountRepository.createToken(
                account.id(), token, TokenType.SET_PASSWORD, Instant.now().plus(72, ChronoUnit.HOURS));
        emailService.sendApplicationAcceptedEmail(
                application.email(), application.firstName(), application.stationName(), token, "de", null);

        return applicationRepository.findById(id).orElseThrow();
    }

    public StationApplication deny(int id, String reason) {
        var application = applicationRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!"pending".equals(application.status())) {
            throw new IllegalStateException("Application is not pending");
        }

        applicationRepository.deny(id, reason);

        // Send denial email
        emailService.sendApplicationDeniedEmail(
                application.email(), application.firstName(), application.stationName(), reason, "de");

        return applicationRepository.findById(id).orElseThrow();
    }
}
