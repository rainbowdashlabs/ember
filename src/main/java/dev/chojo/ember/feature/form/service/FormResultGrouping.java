/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.service.FormRespondents.Respondent;
import dev.chojo.ember.feature.form.service.FormResultQuery.Grouping;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Splits the respondents of a form into the groups its results are counted by.
 *
 * <p>Groups are named after what they hold: a group's or tag's name, a user type, a profile answer,
 * or an age bracket like "14-17". Respondents who have no value to group by (in no group, without a
 * tag, of unknown age, without an answer) form a group of their own keyed {@link #NONE}, whose name
 * the reader's language supplies.
 *
 * <p>With nothing chosen, every group that holds a response is shown. With groups chosen, exactly
 * those are shown, in the order they are shown elsewhere, even when one of them is empty: comparing
 * group A with group B should not quietly turn into looking at A alone.
 */
@Singleton
public class FormResultGrouping {
    /** The key of the group of respondents who have no value for what the results are grouped by. */
    public static final String NONE = "none";

    /** Where age brackets start when the reader has not chosen: under 14, 14-17, 18-26, 27-39, 40-59, 60+. */
    static final List<Integer> DEFAULT_AGE_BOUNDS = List.of(14, 18, 27, 40, 60);

    private final MemberGroupRepository groups;
    private final UserTagRepository tags;
    private final ProfileFieldRepository profileFields;

    @Inject
    public FormResultGrouping(
            MemberGroupRepository groups, UserTagRepository tags, ProfileFieldRepository profileFields) {
        this.groups = groups;
        this.tags = tags;
        this.profileFields = profileFields;
    }

    /**
     * One group of respondents.
     *
     * @param key   stable identifier of the group within this result
     * @param label what the group is called; empty for {@link #NONE}
     * @param ids   the respondents in it
     */
    public record Bucket(String key, String label, Set<Integer> ids) {}

    private record Category(String key, String label) {}

    /**
     * Whether a respondent can be in more than one group of this grouping, so the groups can add up
     * to more responses than there are.
     */
    public static boolean overlaps(Grouping grouping) {
        return grouping != null
                && (grouping.by() == FormResultQuery.Dimension.GROUP || grouping.by() == FormResultQuery.Dimension.TAG);
    }

    /**
     * Splits the respondents into the groups of the grouping.
     *
     * @param stationId   the station whose groups, tags and profile fields name the groups
     * @param respondents the respondents to split
     * @param grouping    what to split them by
     * @return the groups, in display order
     */
    public List<Bucket> split(int stationId, List<Respondent> respondents, Grouping grouping) {
        if (grouping == null || grouping.by() == null) throw new BadRequestResponse("Nothing to group by");
        var buckets =
                switch (grouping.by()) {
                    case USER_TYPE ->
                        categorical(
                                respondents,
                                Arrays.stream(StationUserType.values())
                                        .map(type -> new Category(type.name(), type.name()))
                                        .toList(),
                                respondent -> respondent.userType() == null
                                        ? Set.of()
                                        : Set.of(respondent.userType().name()));
                    case GROUP ->
                        categorical(
                                respondents,
                                groups.findByStation(stationId).stream()
                                        .map(group -> new Category(String.valueOf(group.id()), group.name()))
                                        .toList(),
                                respondent -> keysOf(respondent.groupIds()));
                    case TAG ->
                        categorical(
                                respondents,
                                tags.findByStation(stationId).stream()
                                        .map(tag -> new Category(String.valueOf(tag.id()), tag.name()))
                                        .toList(),
                                respondent -> keysOf(respondent.tagIds()));
                    case FIELD -> byField(stationId, respondents, grouping);
                    case AGE ->
                        brackets(
                                respondents,
                                boundsOr(grouping.bounds(), DEFAULT_AGE_BOUNDS),
                                respondent -> respondent.age() == null
                                        ? null
                                        : respondent.age().doubleValue());
                };
        return limited(buckets, grouping.only());
    }

    private List<Bucket> byField(int stationId, List<Respondent> respondents, Grouping grouping) {
        var field = FormRespondents.groupableFields(profileFields.findByStation(stationId)).stream()
                .filter(candidate -> grouping.fieldId() != null && candidate.id() == grouping.fieldId())
                .findFirst()
                .orElseThrow(() -> new BadRequestResponse("Unknown profile field to group by"));
        Function<Respondent, String> answer =
                respondent -> respondent.fieldValues().get(field.id());
        if (field.fieldType() == ProfileFieldType.NUMBER && FormResultQuery.notEmpty(grouping.bounds())) {
            return brackets(respondents, grouping.bounds(), respondent -> {
                var value = answer.apply(respondent);
                return value == null ? null : FormResultQuery.numberOf(value);
            });
        }
        return categorical(respondents, categoriesOf(field, respondents), respondent -> {
            var value = answer.apply(respondent);
            return value == null ? Set.of() : Set.of(value);
        });
    }

    private static List<Category> categoriesOf(ProfileField field, List<Respondent> respondents) {
        return switch (field.fieldType()) {
            case ENUM -> {
                var options = field.config() == null || field.config().options() == null
                        ? List.<String>of()
                        : field.config().options();
                yield options.stream()
                        .map(option -> new Category(option, option))
                        .toList();
            }
            case BOOLEAN -> List.of(new Category("true", "true"), new Category("false", "false"));
            default ->
                respondents.stream()
                        .map(respondent -> respondent.fieldValues().get(field.id()))
                        .filter(value -> value != null && FormResultQuery.numberOf(value) != null)
                        .distinct()
                        .sorted(Comparator.comparing(FormResultQuery::numberOf))
                        .map(value -> new Category(value, value))
                        .toList();
        };
    }

    /**
     * Groups by a value a respondent has zero, one or several of. A value no category names, like a
     * choice that has since been taken off a profile field, still gets a group of its own rather
     * than being lost.
     */
    private static List<Bucket> categorical(
            List<Respondent> respondents, List<Category> categories, Function<Respondent, Set<String>> keysOf) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (var category : categories) {
            buckets.put(category.key(), new Bucket(category.key(), category.label(), new LinkedHashSet<>()));
        }
        var none = new Bucket(NONE, "", new LinkedHashSet<>());
        for (var respondent : respondents) {
            var keys = keysOf.apply(respondent);
            if (keys.isEmpty()) {
                none.ids().add(respondent.id());
                continue;
            }
            for (var key : keys) {
                buckets.computeIfAbsent(key, _ -> new Bucket(key, key, new LinkedHashSet<>()))
                        .ids()
                        .add(respondent.id());
            }
        }
        var ordered = new ArrayList<>(buckets.values());
        ordered.add(none);
        return ordered;
    }

    /**
     * Groups by a number into brackets that start at each bound: bounds 14 and 18 make "< 14",
     * "14-17" and "18+". A bracket ends one below the next bound, which is what a reader means by
     * "14 to 17" for ages.
     */
    private static List<Bucket> brackets(
            List<Respondent> respondents, List<Integer> bounds, Function<Respondent, Double> numberOf) {
        var starts = bounds.stream().distinct().sorted().toList();
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(new Bucket("< " + starts.getFirst(), "< " + starts.getFirst(), new LinkedHashSet<>()));
        for (int i = 0; i < starts.size(); i++) {
            var label = i + 1 < starts.size() ? starts.get(i) + "-" + (starts.get(i + 1) - 1) : starts.get(i) + "+";
            buckets.add(new Bucket(label, label, new LinkedHashSet<>()));
        }
        var none = new Bucket(NONE, "", new LinkedHashSet<>());
        for (var respondent : respondents) {
            var number = numberOf.apply(respondent);
            if (number == null) {
                none.ids().add(respondent.id());
                continue;
            }
            int index = (int) starts.stream().filter(start -> number >= start).count();
            buckets.get(index).ids().add(respondent.id());
        }
        buckets.add(none);
        return buckets;
    }

    private static List<Bucket> limited(List<Bucket> buckets, List<String> only) {
        if (!FormResultQuery.notEmpty(only)) {
            return buckets.stream().filter(bucket -> !bucket.ids().isEmpty()).toList();
        }
        var chosen = Set.copyOf(only);
        return buckets.stream().filter(bucket -> chosen.contains(bucket.key())).toList();
    }

    private static List<Integer> boundsOr(List<Integer> bounds, List<Integer> fallback) {
        return FormResultQuery.notEmpty(bounds) ? bounds : fallback;
    }

    private static Set<String> keysOf(Set<Integer> ids) {
        Set<String> keys = new LinkedHashSet<>();
        for (int id : ids) keys.add(String.valueOf(id));
        return keys;
    }
}
