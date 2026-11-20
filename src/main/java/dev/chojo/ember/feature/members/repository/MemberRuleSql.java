/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.ExpirySettings;
import dev.chojo.ember.feature.members.entity.ExpiryState;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.RuleUnit;
import dev.chojo.ember.feature.members.entity.RuleValue;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Turns the tree of a member rule into one SQL predicate over {@code station_member sm}, with every value
 * travelling as a bind. Blocks become {@code AND}, {@code OR} and {@code NOT (... OR ...)}; each check becomes
 * an {@code EXISTS} over the row it reads, or a comparison on the member row itself.
 *
 * <p>A stored date is never cast: its {@code YYYY-MM-DD} text is compared with the day it is measured against,
 * written the same way. A text that only looks like a date, such as a year 0000 or the 30th of February, then
 * sorts where its digits put it instead of failing the whole statement for every member of the station.
 *
 * <p>The fragments are constants of this class; nothing a user typed reaches the statement text. Bind names are
 * letters only, and no fragment contains a question mark, since the query layer reads both as placeholders.
 *
 * <p>Answers are read in every shape the field kernel has ever stored: a JSON string, number or boolean, an
 * array of member ids, or an empty object or {@code null} for no answer. A check whose field is gone (an
 * association field deleted by its owner, or a group or tag that did not travel with a station) makes the whole
 * rule match nobody, so a negation around it can never turn it into everybody.
 */
final class MemberRuleSql {
    private static final String TEXT_OF = """
            CASE WHEN jsonb_typeof(%1$s.value) IN ('string', 'number', 'boolean')
                      AND lower(btrim(%1$s.value #>> '{}')) NOT IN ('', 'null')
                 THEN btrim(%1$s.value #>> '{}') END""";
    private static final String DATE_OF = """
            (CASE WHEN (%1$s) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}' THEN substring((%1$s) FROM 1 FOR 10) END)""";
    private static final String NUMBER_OF = """
            (CASE WHEN replace((%1$s), ',', '.') ~ '^[+-]{0,1}([0-9]+([.][0-9]*){0,1}|[.][0-9]+)$'
                  THEN CAST(replace((%1$s), ',', '.') AS NUMERIC) END)""";

    private final List<UnaryOperator<Call>> binds = new ArrayList<>();
    private final LocalDate today;
    private int names;

    private MemberRuleSql(LocalDate today) {
        this.today = today;
    }

    /**
     * The compiled predicate of a rule and the binds it needs.
     *
     * @param predicate the SQL predicate over {@code station_member sm}
     * @param binds     applies every value the predicate names
     */
    record Compiled(String predicate, UnaryOperator<Call> binds) {}

    /**
     * Compiles a rule.
     *
     * @param root  the root block of the rule
     * @param today the current day, which a check counts from unless it names a fixed one
     */
    static Compiled compile(RuleNode root, LocalDate today) {
        if (root.lostATarget()) return new Compiled("FALSE", call -> call);
        var sql = new MemberRuleSql(today);
        String predicate = sql.node(root);
        List<UnaryOperator<Call>> all = List.copyOf(sql.binds);
        return new Compiled(predicate, call -> {
            Call bound = call;
            for (var bind : all) bound = bind.apply(bound);
            return bound;
        });
    }

    private String node(RuleNode node) {
        return switch (node.kind()) {
            case ALL -> joined(node.children(), " AND ", "TRUE");
            case ANY -> joined(node.children(), " OR ", "FALSE");
            case NONE -> "NOT " + joined(node.children(), " OR ", "FALSE");
            case CHECK -> check(node);
        };
    }

    private String joined(List<RuleNode> children, String joint, String whenEmpty) {
        if (children.isEmpty()) return "(" + whenEmpty + ")";
        return children.stream().map(this::node).collect(Collectors.joining(joint, "(", ")"));
    }

    private String check(RuleNode check) {
        var subject = Objects.requireNonNull(check.subject(), "a check names its subject");
        var operator = Objects.requireNonNull(check.operator(), "a check names its operator");
        var value = check.valueOrEmpty();
        return switch (subject) {
            case GROUP -> {
                Integer group = check.groupId();
                yield group == null ? "FALSE" : membership("member_group_entry", "group_id", group, operator);
            }
            case TAG -> {
                Integer tag = check.tagId();
                yield tag == null ? "FALSE" : membership("user_tag_entry", "tag_id", tag, operator);
            }
            case USER_TYPE -> userType(operator, value);
            case MEMBER_SINCE -> memberSince(operator, value, reference(check));
            case AGE -> age(check, operator, value);
            case FIELD, CLUSTER_FIELD -> field(check, operator, value);
        };
    }

    private String membership(String table, String column, int id, RuleOperator operator) {
        String exists = "EXISTS (SELECT 1 FROM %s e WHERE e.member_id = sm.id AND e.%s = %s)"
                .formatted(table, column, integer(id));
        return operator == RuleOperator.NOT_IN ? "NOT " + exists : exists;
    }

    private String userType(RuleOperator operator, RuleValue value) {
        var types = Objects.requireNonNullElse(value.userTypes(), List.<StationUserType>of()).stream()
                .map(Enum::name)
                .toList();
        String among = "sm.user_type = ANY(%s)".formatted(texts(types));
        return operator == RuleOperator.NONE_OF ? "NOT (" + among + ")" : among;
    }

    private String memberSince(RuleOperator operator, RuleValue value, String reference) {
        String bound = "(%s - %s)".formatted(reference, interval(value));
        return operator == RuleOperator.LESS_THAN ? "sm.join_date > " + bound : "sm.join_date <= " + bound;
    }

    private String age(RuleNode check, RuleOperator operator, RuleValue value) {
        String reference = reference(check);
        int from = Objects.requireNonNullElse(value.amount(), 0);
        String born = DATE_OF.formatted(TEXT_OF.formatted("v"));
        String condition =
                switch (operator) {
                    case LESS_THAN -> "%s > %s".formatted(born, yearsBefore(reference, from));
                    case BETWEEN ->
                        "%s <= %s AND %s > %s"
                                .formatted(
                                        born,
                                        yearsBefore(reference, from),
                                        born,
                                        yearsBefore(reference, Objects.requireNonNullElse(value.amountTo(), from) + 1));
                    default -> "%s <= %s".formatted(born, yearsBefore(reference, from));
                };
        return answered(check, condition);
    }

    private String field(RuleNode check, RuleOperator operator, RuleValue value) {
        String text = TEXT_OF.formatted("v");
        String date = DATE_OF.formatted(text);
        String number = NUMBER_OF.formatted(text);
        String has = "(%s IS NOT NULL OR (jsonb_typeof(v.value) = 'array' AND jsonb_array_length(v.value) > 0))"
                .formatted(text);
        return switch (operator) {
            case IS_YES -> answered(check, "lower(%s) IN ('true', '1')".formatted(text));
            case IS_NO -> answered(check, "lower(%s) IN ('false', '0')".formatted(text));
            case IS_NOT_YES -> unanswered(check, "lower(%s) IN ('true', '1')".formatted(text));
            case EMPTY -> unanswered(check, has);
            case NOT_EMPTY -> answered(check, has);
            case ONE_OF -> answered(check, "%s = ANY(%s)".formatted(text, answers(value)));
            case NONE_OF -> unanswered(check, "%s = ANY(%s)".formatted(text, answers(value)));
            case EQUALS -> answered(check, "lower(%s) = lower(btrim(%s))".formatted(text, text(value)));
            case CONTAINS -> answered(check, "strpos(lower(%s), lower(btrim(%s))) > 0".formatted(text, text(value)));
            case EQ -> answered(check, "%s = %s".formatted(number, number(value.number())));
            case LT -> answered(check, "%s < %s".formatted(number, number(value.number())));
            case LE -> answered(check, "%s <= %s".formatted(number, number(value.number())));
            case GT -> answered(check, "%s > %s".formatted(number, number(value.number())));
            case GE -> answered(check, "%s >= %s".formatted(number, number(value.number())));
            case BETWEEN ->
                answered(
                        check,
                        "%s BETWEEN %s AND %s".formatted(number, number(value.number()), number(value.numberTo())));
            case BEFORE -> answered(check, "%s < %s".formatted(date, day(date(value.date()))));
            case ON_OR_AFTER -> answered(check, "%s >= %s".formatted(date, day(date(value.date()))));
            case WITHIN_NEXT, EXPIRES_WITHIN -> {
                String reference = reference(check);
                yield answered(
                        check,
                        "%s BETWEEN %s AND %s"
                                .formatted(date, day(reference), day(reference + " + " + interval(value))));
            }
            case WITHIN_LAST -> {
                String reference = reference(check);
                yield answered(
                        check,
                        "%s BETWEEN %s AND %s"
                                .formatted(date, day(reference + " - " + interval(value)), day(reference)));
            }
            case EXPIRED -> answered(check, "%s < %s".formatted(date, day(reference(check))));
            case STATE_IN -> expiryState(check, value, date);
            case IN, NOT_IN, AT_LEAST, LESS_THAN -> "FALSE";
        };
    }

    private String expiryState(RuleNode check, RuleValue value, String date) {
        var states = Objects.requireNonNullElse(value.states(), List.<ExpiryState>of());
        var dated = new ArrayList<String>();
        if (states.contains(ExpiryState.EXPIRED)
                || states.contains(ExpiryState.EXPIRING)
                || states.contains(ExpiryState.VALID)) {
            String reference = reference(check);
            String warn = warnFromDays(check);
            String lastWarned = day("%s + make_interval(days => %s)".formatted(reference, warn));
            if (states.contains(ExpiryState.EXPIRED)) dated.add("%s < %s".formatted(date, day(reference)));
            if (states.contains(ExpiryState.EXPIRING)) {
                dated.add("(%s >= %s AND %s <= %s)".formatted(date, day(reference), date, lastWarned));
            }
            if (states.contains(ExpiryState.VALID)) dated.add("%s > %s".formatted(date, lastWarned));
        }
        var holds = new ArrayList<String>();
        if (!dated.isEmpty()) holds.add(answered(check, String.join(" OR ", dated)));
        if (states.contains(ExpiryState.EMPTY)) holds.add(unanswered(check, "%s IS NOT NULL".formatted(date)));
        return holds.isEmpty() ? "FALSE" : "(" + String.join(" OR ", holds) + ")";
    }

    private String warnFromDays(RuleNode check) {
        String fallback = String.valueOf(ExpirySettings.DEFAULT_WARN_FROM_DAYS);
        boolean station = check.subject() != RuleSubject.CLUSTER_FIELD && check.fieldId() != null;
        String table = station ? "profile_field" : "cluster_profile_field";
        String reads = """
                COALESCE((SELECT CASE WHEN (f.config ->> 'warnFromDays') ~ '^[0-9]+$'
                                      THEN CAST(f.config ->> 'warnFromDays' AS INTEGER) END
                          FROM %s f WHERE f.id = v.field_id), %s)""";
        return reads.formatted(table, fallback);
    }

    private String answered(RuleNode check, String condition) {
        return "EXISTS (%s AND (%s))".formatted(values(check), condition);
    }

    private String unanswered(RuleNode check, String condition) {
        return "NOT EXISTS (%s AND (%s))".formatted(values(check), condition);
    }

    private static boolean readsAField(RuleNode check) {
        if (check.subject() == RuleSubject.CLUSTER_FIELD) return check.clusterFieldId() != null;
        return check.fieldId() != null || check.clusterFieldId() != null;
    }

    private String values(RuleNode check) {
        var stationField = check.fieldId();
        if (check.subject() != RuleSubject.CLUSTER_FIELD && stationField != null) {
            return "SELECT 1 FROM profile_field_value v WHERE v.member_id = sm.id AND v.field_id = "
                    + integer(stationField);
        }
        int clusterField = Objects.requireNonNull(check.clusterFieldId(), "a check reads a field");
        return "SELECT 1 FROM cluster_profile_field_value v WHERE v.member_id = sm.id AND v.field_id = "
                + integer(clusterField);
    }

    private String reference(RuleNode check) {
        return date(Objects.requireNonNullElse(check.referenceDate(), today));
    }

    private String interval(RuleValue value) {
        int amount = Objects.requireNonNullElse(value.amount(), 0);
        var unit = Objects.requireNonNullElse(value.unit(), RuleUnit.DAYS);
        String part =
                switch (unit) {
                    case DAYS -> "days";
                    case MONTHS -> "months";
                    case YEARS -> "years";
                };
        return "make_interval(%s => %s)".formatted(part, integer(amount));
    }

    private String yearsBefore(String reference, int years) {
        return day("%s - make_interval(years => %s)".formatted(reference, integer(years)));
    }

    /** A day as the {@code YYYY-MM-DD} text a stored date is compared with. */
    private static String day(String expression) {
        return "to_char(CAST(%s AS DATE), 'YYYY-MM-DD')".formatted(expression);
    }

    private String integer(int value) {
        String name = name();
        binds.add(call -> call.bind(name, value));
        return ":" + name;
    }

    private String date(@Nullable LocalDate value) {
        String name = name();
        binds.add(call -> call.bind(name, value));
        return "CAST(:%s AS DATE)".formatted(name);
    }

    private String number(@Nullable BigDecimal value) {
        String name = name();
        binds.add(call -> call.bind(name, value));
        return "CAST(:%s AS NUMERIC)".formatted(name);
    }

    private String text(RuleValue value) {
        String name = name();
        String text = Objects.requireNonNullElse(value.text(), "");
        binds.add(call -> call.bind(name, text));
        return ":" + name;
    }

    private String answers(RuleValue value) {
        var answers = Objects.requireNonNullElse(value.answers(), List.<String>of()).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .toList();
        return texts(answers);
    }

    private String texts(List<String> values) {
        String name = name();
        List<String> bound = List.copyOf(values);
        binds.add(call -> call.bind(name, bound, PostgreSqlTypes.TEXT));
        return ":" + name;
    }

    private String name() {
        var letters = new StringBuilder();
        int index = names++;
        do {
            letters.insert(0, (char) ('a' + index % 26));
            index = index / 26 - 1;
        } while (index >= 0);
        return "rule_" + letters;
    }
}
