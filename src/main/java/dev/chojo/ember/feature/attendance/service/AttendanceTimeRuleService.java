/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.ExtraTime;
import dev.chojo.ember.feature.attendance.entity.ExtraTimeCredit;
import dev.chojo.ember.feature.attendance.entity.TimeRule;
import dev.chojo.ember.feature.attendance.entity.TimeRuleInput;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceTimeRuleRepository;
import dev.chojo.ember.feature.members.service.PrivateTags;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final RestrictionService restrictionService;

    @Inject
    public AttendanceTimeRuleService(
            AttendanceTimeRuleRepository repository,
            PrivateTags privateTags,
            AttendanceService attendanceService,
            AttendanceRepository attendanceRepository,
            RestrictionService restrictionService) {
        this.repository = repository;
        this.privateTags = privateTags;
        this.attendanceService = attendanceService;
        this.attendanceRepository = attendanceRepository;
        this.restrictionService = restrictionService;
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

    /**
     * The rules of a sheet, in their order: what it copied from its template, corrected since.
     *
     * @param sessionId the sheet
     * @return its rules, empty where it has none
     */
    public List<TimeRule> sessionRules(int sessionId) {
        return repository.findSessionRules(sessionId);
    }

    /**
     * Replaces every rule of a sheet, which is how one evening is corrected without touching its
     * template. Refused once the sheet is closed, like every other write to it.
     *
     * @param stationId the station the sheet belongs to
     * @param sessionId the sheet
     * @param rules     the rules it carries from now on, in their order
     * @return the sheet's rules as stored
     * @throws io.javalin.http.HttpResponseException where the sheet is closed or a rule is refused
     */
    public List<TimeRule> replaceSessionRules(int stationId, int sessionId, List<TimeRuleInput> rules) {
        attendanceService.requireSessionOpen(sessionId);
        requireWritable(stationId, rules);
        repository.replaceSessionRules(sessionId, rules);
        log.info("Replaced the time rules of attendance session {} with {} rule(s)", sessionId, rules.size());
        return repository.findSessionRules(sessionId);
    }

    /**
     * Who on a sheet its rules credit with preparation or follow-up, and how much, matched the way the
     * report matches them. Everybody on the sheet is listed, whatever their status; the report counts
     * the minutes only for somebody present who was not marked as not having done the work.
     *
     * @param sessionId the sheet
     * @return one line per person the rules credit, in the sheet's order; nobody where it has no rules
     */
    public List<ExtraTimeCredit> sessionCredits(int sessionId) {
        List<TimeRule> rules = repository.findSessionRules(sessionId);
        if (rules.isEmpty()) return List.of();
        List<AttendanceEntry> entries = attendanceRepository.findEntries(sessionId);
        Map<Integer, RestrictionMember> members = restrictionService.membersOf(
                entries.stream().map(AttendanceEntry::memberId).toList());
        var credits = new ArrayList<ExtraTimeCredit>();
        for (AttendanceEntry entry : entries) {
            RestrictionMember member = members.get(entry.memberId());
            if (member == null) continue;
            ExtraTime extra = ExtraTime.of(rules, member);
            if (extra.totalMinutes() == 0) continue;
            credits.add(new ExtraTimeCredit(
                    entry.id(),
                    entry.memberId(),
                    extra.preparationMinutes(),
                    extra.followUpMinutes(),
                    entry.skipExtraTime()));
        }
        return credits;
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
