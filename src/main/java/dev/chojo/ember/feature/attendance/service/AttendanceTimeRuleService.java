/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.feature.attendance.entity.TimeRule;
import dev.chojo.ember.feature.attendance.entity.TimeRuleInput;
import dev.chojo.ember.feature.attendance.repository.AttendanceTimeRuleRepository;
import dev.chojo.ember.feature.members.service.PrivateTags;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The preparation and follow-up rules of attendance templates and sheets: who is credited with time
 * before and after an appointment, and how much.
 *
 * <p>A list is always written as a whole and checked as a whole first: every rule has to name
 * somebody, its minutes have to fit a day, it may not choose people by a private tag, and every group,
 * tag and member it names has to be one of the station's. A rule that lost a group or tag reads back
 * naming nobody, so it can only be saved again once somebody names its people anew.
 */
@Singleton
public class AttendanceTimeRuleService {
    /** The most minutes a rule credits before or after an appointment: one day. */
    public static final int MAX_MINUTES = 1440;

    private static final Logger log = LoggerFactory.getLogger(AttendanceTimeRuleService.class);

    private final AttendanceTimeRuleRepository repository;
    private final PrivateTags privateTags;

    @Inject
    public AttendanceTimeRuleService(AttendanceTimeRuleRepository repository, PrivateTags privateTags) {
        this.repository = repository;
        this.privateTags = privateTags;
    }

    /**
     * The rules of a template, in their order.
     *
     * @param templateId the template
     * @return its rules, empty where it has none
     */
    public List<TimeRule> templateRules(int templateId) {
        return repository.findTemplateRules(templateId);
    }

    /**
     * Replaces every rule of a template. Sheets made from it before keep the rules they copied.
     *
     * @param stationId  the station the template belongs to
     * @param templateId the template
     * @param rules      the rules it carries from now on, in their order
     * @return the template's rules as stored
     * @throws io.javalin.http.HttpResponseException where a rule is refused, see the class description
     */
    public List<TimeRule> replaceTemplateRules(int stationId, int templateId, List<TimeRuleInput> rules) {
        requireWritable(stationId, rules);
        repository.replaceTemplateRules(templateId, rules);
        log.info("Replaced the time rules of attendance template {} with {} rule(s)", templateId, rules.size());
        return repository.findTemplateRules(templateId);
    }

    private void requireWritable(int stationId, List<TimeRuleInput> rules) {
        for (TimeRuleInput rule : rules) {
            if (rule == null || rule.audience() == null) {
                throw AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY.raise();
            }
            RestrictionSelection selection = rule.audience().toSelection();
            if (containsNull(selection)) throw AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN.raise();
            if (rule.audience().namesNobody()) throw AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_NOBODY.raise();
            if (outOfRange(rule.preparationMinutes()) || outOfRange(rule.followUpMinutes())) {
                throw AttendanceRefusal.ATTENDANCE_TIME_RULE_MINUTES_OUT_OF_RANGE.raise();
            }
            if (!repository.namesOnlyOwn(stationId, selection)) {
                throw AttendanceRefusal.ATTENDANCE_TIME_RULE_NAMES_UNKNOWN.raise();
            }
            privateTags.requireChoosable(selection.tagIds());
        }
    }

    private static boolean containsNull(RestrictionSelection selection) {
        return Stream.of(selection.userTypes(), selection.groupIds(), selection.tagIds(), selection.memberIds())
                .flatMap(List::stream)
                .anyMatch(Objects::isNull);
    }

    private static boolean outOfRange(int minutes) {
        return minutes < 0 || minutes > MAX_MINUTES;
    }
}
