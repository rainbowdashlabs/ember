/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class RequirementsService {
    private final FormService formService;
    private final QuizService quizService;
    private final ProfileFieldService profileFieldService;

    @Inject
    public RequirementsService(
            FormService formService, QuizService quizService, ProfileFieldService profileFieldService) {
        this.formService = formService;
        this.quizService = quizService;
        this.profileFieldService = profileFieldService;
    }

    public RequirementsResponse getRequirements(int memberId, int stationId, List<String> roleNames) {
        var forcedForms = formService.findForcedPending(stationId, memberId);
        var forcedQuizzes = quizService.findForcedPending(stationId, memberId);
        boolean profileIncomplete = !profileFieldService.isProfileComplete(memberId, stationId, roleNames);
        return new RequirementsResponse(forcedForms, forcedQuizzes, profileIncomplete);
    }

    public int countPending(int memberId, int stationId, List<String> roleNames) {
        int count = 0;
        count += formService.findForcedPending(stationId, memberId).size();
        count += quizService.findForcedPending(stationId, memberId).size();
        if (!profileFieldService.isProfileComplete(memberId, stationId, roleNames)) count++;
        return count;
    }

    public record RequirementItem(int id, String title) {}

    public record RequirementsResponse(
            List<RequirementItem> forcedForms, List<RequirementItem> forcedQuizzes, boolean profileIncomplete) {}
}
