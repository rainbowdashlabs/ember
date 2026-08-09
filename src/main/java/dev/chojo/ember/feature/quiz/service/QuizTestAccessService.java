/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;

/**
 * Decides who may take a test: the role, group and tag restrictions, the per-member access
 * grants that bypass them, and the test's own status and time window.
 */
@Singleton
public class QuizTestAccessService {
    private static final Logger log = LoggerFactory.getLogger(QuizTestAccessService.class);

    private final QuizTestRepository testRepository;
    private final RestrictionService restrictionService;

    @Inject
    public QuizTestAccessService(QuizTestRepository testRepository, RestrictionService restrictionService) {
        this.testRepository = testRepository;
        this.restrictionService = restrictionService;
    }

    /**
     * Checks whether a member may take a test right now: it has to be active, inside its
     * time window and either granted to the member directly or admitted by its restrictions.
     */
    public boolean isTestAccessible(QuizTest test, int memberId, Set<StationPermission> memberPermissions) {
        if (test.status() != TestStatus.ACTIVE) return false;
        Instant now = Instant.now();
        if (test.startAt() != null && now.isBefore(test.startAt())) return false;
        if (test.endAt() != null && now.isAfter(test.endAt())) return false;
        if (testRepository.hasMemberAccess(test.id(), memberId)) return true;
        return canMemberAccess(test.id(), memberId, memberPermissions);
    }

    /**
     * Checks if a member can access a quiz test based on its restrictions.
     * Delegates to the DB function which resolves the member's identity internally.
     */
    public boolean canMemberAccess(int testId, int memberId, Set<StationPermission> memberPermissions) {
        return restrictionService.checkRestriction(RestrictionType.QUIZ_TEST, testId, memberId, memberPermissions);
    }

    /**
     * Retrieves the restriction set for a test.
     */
    public RestrictionSet findRestrictions(int testId) {
        var test = testRepository.findById(testId).orElse(null);
        RestrictionMode mode = test != null ? test.restrictionMode() : RestrictionMode.OR;
        return restrictionService.findRestrictionSet(RestrictionType.QUIZ_TEST, testId, mode);
    }

    public void setRestrictions(int testId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.QUIZ_TEST, testId, selection);
        log.info("Updated restrictions for quiz test {}", testId);
    }

    public void updateRestrictionMode(int testId, RestrictionMode mode) {
        testRepository.updateRestrictionMode(testId, mode);
        log.info("Updated restriction mode for quiz test {} to {}", testId, mode);
    }

    public void grantMemberAccess(int testId, int memberId, Instant closesAt) {
        testRepository.grantMemberAccess(testId, memberId, closesAt);
        log.info("Granted access to quiz test {} for member {}", testId, memberId);
    }

    public void revokeMemberAccess(int testId, int memberId) {
        testRepository.revokeMemberAccess(testId, memberId);
        log.info("Revoked access to quiz test {} for member {}", testId, memberId);
    }
}
