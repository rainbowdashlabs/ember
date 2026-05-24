/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.StationApplication;
import dev.chojo.ember.feature.station.repository.StationApplicationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing station applications, handling the full lifecycle from submission
 * through email verification to admin acceptance or denial.
 */
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

    /**
     * Submits a new station application and sends a verification email to the applicant.
     *
     * @param firstName    the applicant's first name
     * @param lastName     the applicant's last name
     * @param email        the applicant's email address
     * @param stationName  the desired station name
     * @param introduction optional introduction text
     * @return the created application
     */
    public StationApplication submit(
            String firstName, String lastName, String email, String stationName, String introduction) {
        String token = UUID.randomUUID().toString();
        var application = applicationRepository.create(firstName, lastName, email, stationName, introduction, token);
        emailService.sendApplicationVerifyEmail(email, firstName, stationName, token, "de", null);
        return application;
    }

    /**
     * Verifies an application using the email verification token.
     *
     * @param token the verification token
     * @return {@code true} if the application was successfully verified
     */
    public boolean verify(String token) {
        var application = applicationRepository.findByToken(token);
        return application
                .filter(app -> applicationRepository.verify(app.id()))
                .map(app -> {
                    emailService.sendApplicationReceivedEmail(
                            app.email(), app.firstName(), app.stationName(), "de", null);
                    return true;
                })
                .isPresent();
    }

    /**
     * Retrieves all applications.
     *
     * @return a list of all applications
     */
    public List<StationApplication> findAll() {
        return applicationRepository.findAll();
    }

    /**
     * Retrieves all pending applications.
     *
     * @return a list of pending applications
     */
    public List<StationApplication> findPending() {
        return applicationRepository.findByStatus("pending");
    }

    /**
     * Finds an application by its ID.
     *
     * @param id the application ID
     * @return the application, or empty if not found
     */
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
                application.email(), application.firstName(), application.stationName(), reason, "de", null);

        return applicationRepository.findById(id).orElseThrow();
    }
}
