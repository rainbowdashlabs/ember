/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DateFormat;
import dev.chojo.ember.feature.generator.entity.DateKind;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.PlaceholderKey;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.ResolvedValues;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.PronounSet;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GenderFields;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.ProfileFieldCore;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionText;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.StoredDay;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fills the placeholders a template names with what is known about one member, at the moment the
 * document is generated.
 *
 * <p>Names are official names: the register's first name and surname, for the member, the guardians,
 * the issuer and whoever generates the document. The issuer is named only while they are a current
 * member of the station the document is drawn at; otherwise their name is missing. Only {@code member.calledName} reads the name a member is called
 * by, and only an informal template may name it. Profile answers are written the way exports write
 * them ({@link QuestionText#format}), so a date reads as a day and yes as a word of the template's
 * language. The pronouns follow the member's answer to the station's gender field ({@link GenderFields}),
 * in the template's language.
 *
 * <p>A date prints in the format its key names after a bar ({@link PlaceholderKey}, {@link DateFormat}),
 * the names of months and weekdays in the template's language. Without one a day prints as
 * {@code 03.10.2026}, and the start and end of an appointment add the time of day. A format that cannot
 * print the value, which saving a template refuses, leaves it missing.
 *
 * <p>Values are always read at the member's own station, whoever keeps the template: {@code station.*}
 * names that station and {@code association.*} the association it belongs to, so one template of an
 * association names the right station for every member. An answer to a question of the association is
 * read where the question reaches the station; where it does not, the value is missing.
 *
 * <p>The guardians are taken in the order the member page sets: the one marked first is
 * {@code guardian1}. A guardian whose data a template names is one of the people the document is
 * about, and is listed among its data subjects. The values of a second guardian the member does not
 * have are empty and not missing; a member without any guardian misses the first guardian's values.
 */
@Singleton
public class PlaceholderResolver {
    private final StationRepository stations;
    private final StationMemberRepository members;
    private final MemberNameResolver names;
    private final ProfileFieldRepository profileFields;
    private final ProfileFieldCore fieldOwners;
    private final GenderFields genders;
    private final ClusterService clusters;
    private final Clock clock;

    @Inject
    public PlaceholderResolver(
            StationRepository stations,
            StationMemberRepository members,
            MemberNameResolver names,
            ProfileFieldRepository profileFields,
            ProfileFieldCore fieldOwners,
            GenderFields genders,
            ClusterService clusters) {
        this(stations, members, names, profileFields, fieldOwners, genders, clusters, Clock.systemUTC());
    }

    /**
     * @param clock what today is read from, which a test moves
     */
    public PlaceholderResolver(
            StationRepository stations,
            StationMemberRepository members,
            MemberNameResolver names,
            ProfileFieldRepository profileFields,
            ProfileFieldCore fieldOwners,
            GenderFields genders,
            ClusterService clusters,
            Clock clock) {
        this.stations = stations;
        this.members = members;
        this.names = names;
        this.profileFields = profileFields;
        this.fieldOwners = fieldOwners;
        this.genders = genders;
        this.clusters = clusters;
        this.clock = clock;
    }

    /**
     * The values of the given keys for one member.
     *
     * @param stationId the station that files the document
     * @param memberId  the member it is about
     * @param keys      the keys the template names
     * @param language  the language the template writes in, which picks the pronouns and how dates and
     *                  answers are written
     * @param context   who generates it and for which appointment
     * @return the values, the keys without one, and whose data went in
     */
    public ResolvedValues resolve(
            int stationId, int memberId, Set<String> keys, DocumentLanguage language, GenerationContext context) {
        var station = stations.findById(stationId).orElse(null);
        var association = clusters.findByStation(stationId).orElse(null);
        var reading = new Reading(new Subject(station, association, memberId, language, context));
        var values = new LinkedHashMap<String, String>();
        var missing = new ArrayList<String>();
        for (String key : keys) {
            if (reading.ofAbsentSecondGuardian(key)) {
                values.put(key, "");
                continue;
            }
            String value = reading.value(key).map(String::strip).orElse("");
            if (value.isEmpty()) {
                missing.add(key);
            } else {
                values.put(key, value);
            }
        }
        return new ResolvedValues(values, missing, reading.subjects(keys));
    }

    /**
     * @param memberId a member
     * @return the station the member belongs to, where every document about them is drawn and filed, or
     *         empty where there is no such member
     */
    public Optional<Integer> stationOf(int memberId) {
        return members.findById(memberId).map(StationMember::stationId);
    }

    /**
     * @param memberId a member
     * @return how many guardians the member has
     */
    public int guardians(int memberId) {
        return members.findManagers(memberId).size();
    }

    /**
     * The values a template can only show by their labels, for a preview without a member.
     *
     * <p>A station's template shows the station's own data and its association's; an association's
     * template shows the association's only, since it is generated at each of its stations.
     *
     * @param owner    the station or the association that keeps the template
     * @param language the language the template writes in
     * @param keys     the keys the template names, a date with the format it names
     * @return today's date and the data of the station and the association, which need no member, by
     *         the keys that name them
     */
    public Map<String, String> withoutMember(Owner owner, DocumentLanguage language, Set<String> keys) {
        var station = owner instanceof Owner.Station own
                ? stations.findById(own.stationId()).orElse(null)
                : null;
        var association =
                switch (owner) {
                    case Owner.Station own -> clusters.findByStation(own.stationId());
                    case Owner.Association own -> clusters.findById(own.clusterId());
                    case Owner.Instance ignored -> Optional.<Cluster>empty();
                };
        var reading =
                new Reading(new Subject(station, association.orElse(null), 0, language, GenerationContext.NOBODY));
        var values = new HashMap<String, String>();
        for (String key : keys) {
            boolean needsNoMember = BuiltInPlaceholder.of(key)
                    .filter(BuiltInPlaceholder::needsNoMember)
                    .isPresent();
            if (needsNoMember) reading.value(key).ifPresent(value -> values.put(key, value));
        }
        return values;
    }

    /**
     * Whom values are read for. It reaches {@link Reading} as one record because the null check
     * misplaces a nullable parameter on the constructor of an inner class.
     *
     * @param station     the station that files the document, or null where it is gone or not known yet
     * @param association the association of that station, or null where it belongs to none
     * @param memberId    the member the document is about, 0 for none
     * @param language    the language the template writes in
     * @param context     who generates it and for which appointment
     */
    private record Subject(
            @Nullable Station station,
            @Nullable Cluster association,
            int memberId,
            DocumentLanguage language,
            GenerationContext context) {}

    /**
     * Everything read for one member while their values are filled in, each piece read once.
     */
    private final class Reading {
        private final @Nullable Station station;
        private final @Nullable Cluster association;
        private final int memberId;
        private final GenerationContext context;
        private final DocumentLanguage documentLanguage;
        private final String language;
        private final ZoneId zone;
        private final Map<Integer, Optional<StationMember>> memberships = new HashMap<>();
        private final Map<Integer, Optional<ProfileField>> fields = new HashMap<>();
        private final Map<Integer, Optional<OwnedProfileField>> associationFields = new HashMap<>();
        private @Nullable List<StationMember> guardians;
        private @Nullable Optional<PronounSet> pronouns;

        Reading(Subject subject) {
            this.station = subject.station();
            this.association = subject.association();
            this.memberId = subject.memberId();
            this.context = subject.context();
            this.documentLanguage = subject.language();
            this.language = documentLanguage.code();
            this.zone = StationFormat.timezoneOf(station);
        }

        Optional<String> value(String written) {
            var key = PlaceholderKey.parse(written);
            String format = key.format();
            if (format != null) {
                return DateFormat.of(format)
                        .flatMap(printed -> dated(key.base()).flatMap(date -> printed.format(date, documentLanguage)));
            }
            var builtIn = BuiltInPlaceholder.of(key.base());
            if (builtIn.isPresent()) return Optional.ofNullable(builtIn(builtIn.get()));
            var pronoun = PronounKey.parse(key.base());
            if (pronoun.isPresent()) return Optional.ofNullable(pronoun(pronoun.get()));
            return answerOf(key.base()).map(this::written);
        }

        /**
         * The date a key reads, a day or a day with a time of day in the station's time zone: a date of the
         * member, today, the start or end of the appointment, or an answer to a profile question of a date
         * type.
         */
        private Optional<TemporalAccessor> dated(String key) {
            var builtIn = BuiltInPlaceholder.of(key);
            if (builtIn.isPresent()) return dated(builtIn.get());
            return answerOf(key)
                    .filter(answer -> DateKind.of(answer.type()) != null)
                    .flatMap(answer -> StoredDay.of(QuestionValues.read(answer.stored())));
        }

        private Optional<TemporalAccessor> dated(BuiltInPlaceholder placeholder) {
            var event = Optional.ofNullable(context.event());
            return switch (placeholder) {
                case MEMBER_BIRTH_DATE -> birthDate().map(day -> day);
                case MEMBER_JOIN_DATE -> membership(memberId).map(StationMember::joinDate);
                case TODAY -> Optional.of(today());
                case EVENT_START -> event.map(facts -> facts.start().atZone(zone));
                case EVENT_END -> event.map(facts -> facts.end().atZone(zone));
                default -> Optional.empty();
            };
        }

        /** A date of a built-in placeholder in the format its kind prints in where the key names none. */
        private @Nullable String standard(BuiltInPlaceholder placeholder) {
            var kind = Objects.requireNonNull(placeholder.dateKind(), "only a date is printed as one");
            return dated(placeholder)
                    .flatMap(date -> DateFormat.of(kind.standard()).format(date, documentLanguage))
                    .orElse(null);
        }

        /**
         * The answer a key reads: the member's or a guardian's to a question of the station or the
         * association, empty where the guardian, the question or the answer is not there.
         */
        private Optional<Answer> answerOf(String key) {
            int whose = memberId;
            String asked = key;
            for (int index = 0; index < PlaceholderCatalogue.GUARDIANS.size(); index++) {
                String prefix = PlaceholderCatalogue.GUARDIANS.get(index);
                if (!key.startsWith(prefix)) continue;
                var guardian = guardian(index);
                if (guardian.isEmpty()) return Optional.empty();
                whose = guardian.get().id();
                asked = key.substring(prefix.length());
                break;
            }
            if (asked.startsWith(PlaceholderCatalogue.PROFILE)) {
                return answer(whose, asked.substring(PlaceholderCatalogue.PROFILE.length()));
            }
            if (asked.startsWith(PlaceholderCatalogue.ASSOCIATION_PROFILE)) {
                return associationAnswer(whose, asked.substring(PlaceholderCatalogue.ASSOCIATION_PROFILE.length()));
            }
            return Optional.empty();
        }

        private @Nullable String builtIn(BuiltInPlaceholder placeholder) {
            return switch (placeholder) {
                case MEMBER_FIRST_NAME -> firstName(memberId);
                case MEMBER_LAST_NAME -> names.parts(memberId).lastName();
                case MEMBER_FULL_NAME -> names.official(memberId);
                case MEMBER_CALLED_NAME -> names.called(memberId);
                case MEMBER_BIRTH_DATE, MEMBER_JOIN_DATE, EVENT_START, EVENT_END, TODAY -> standard(placeholder);
                case MEMBER_AGE ->
                    birthDate()
                            .map(born ->
                                    String.valueOf(Period.between(born, today()).getYears()))
                            .orElse(null);
                case MEMBER_USER_TYPE ->
                    membership(memberId)
                            .map(member ->
                                    DocumentWord.forUserType(member.userType().name(), language))
                            .orElse(null);
                case GUARDIAN1_FULL_NAME -> guardianName(0, names::official);
                case GUARDIAN1_FIRST_NAME -> guardianName(0, this::firstName);
                case GUARDIAN1_LAST_NAME ->
                    guardianName(0, id -> names.parts(id).lastName());
                case GUARDIAN2_FULL_NAME -> guardianName(1, names::official);
                case GUARDIAN2_FIRST_NAME -> guardianName(1, this::firstName);
                case GUARDIAN2_LAST_NAME ->
                    guardianName(1, id -> names.parts(id).lastName());
                case STATION_NAME -> station == null ? null : station.name();
                case STATION_ADDRESS -> station == null ? null : station.addressLine();
                case STATION_POSTAL_CODE -> station == null ? null : station.postalCode();
                case STATION_CITY -> station == null ? null : station.city();
                case ASSOCIATION_NAME -> association == null ? null : association.name();
                case ASSOCIATION_ADDRESS -> associationAddress();
                case EVENT_NAME -> event(GenerationContext.EventFacts::name);
                case EVENT_LOCATION -> event(GenerationContext.EventFacts::location);
                case ISSUER_FULL_NAME -> issuer().map(names::official).orElse(null);
                case ISSUER_FUNCTION -> context.issuer().function();
                case GENERATED_BY -> {
                    Integer by = context.generatedBy();
                    yield by == null ? null : names.official(by);
                }
            };
        }

        /**
         * The official first name, which is the whole name a member who has left still carries.
         */
        private @Nullable String firstName(int id) {
            NameParts parts = names.parts(id);
            String first = parts.firstName();
            return first != null ? first : parts.frozen();
        }

        /**
         * The word for a pronoun, from the member's answer to the station's gender field in the template's
         * language, or the first name where that gives none.
         */
        private @Nullable String pronoun(PronounKey pronoun) {
            String first = firstName(memberId);
            if (first == null) return null;
            if (pronouns == null) pronouns = genderPronouns();
            return Pronouns.of(pronoun, pronouns.orElse(null), first, language);
        }

        private Optional<PronounSet> genderPronouns() {
            return genders.askedAt(stationId()).flatMap(field -> field.config()
                    .pronounsOf(genders.answerOf(memberId, field).orElse(null), language));
        }

        private Optional<LocalDate> birthDate() {
            return profileFields.findAllByStationAndType(stationId(), FieldType.BIRTH_DATE).stream()
                    .findFirst()
                    .map(field -> storedAnswer(memberId, field.id()))
                    .flatMap(StoredDay::of);
        }

        private Optional<Answer> answer(int whose, String fieldKey) {
            return fieldIdOf(fieldKey).flatMap(fieldId -> field(fieldId).flatMap(field -> profileFields
                    .findValue(whose, fieldId)
                    .map(value -> new Answer(field.fieldType(), value.value()))));
        }

        /**
         * An answer to a question of the association, where the question reaches the station: the
         * association's adapter holds both the question and the answers.
         */
        private Optional<Answer> associationAnswer(int whose, String fieldKey) {
            return fieldIdOf(fieldKey).flatMap(fieldId -> associationField(fieldId)
                    .flatMap(field -> fieldOwners.owner(FieldOrigin.CLUSTER).answersOf(whose).stream()
                            .filter(value -> value.fieldId() == fieldId)
                            .findFirst()
                            .map(value -> new Answer(field.type(), value.value()))));
        }

        /** An answer as exports print it. */
        private String written(Answer answer) {
            return QuestionText.format(
                    answer.type(), answer.stored(), memberNames(answer.type(), answer.stored()), language);
        }

        private static Optional<Integer> fieldIdOf(String fieldKey) {
            try {
                return Optional.of(Integer.parseInt(fieldKey));
            } catch (NumberFormatException notAField) {
                return Optional.empty();
            }
        }

        private Optional<OwnedProfileField> associationField(int fieldId) {
            return associationFields.computeIfAbsent(fieldId, id -> fieldOwners
                    .owner(FieldOrigin.CLUSTER)
                    .askedAt(stationId(), id)
                    .filter(field -> field.type().holdsValue()));
        }

        /**
         * Where the association is, which is where its home station is: the street, then the postal code
         * and the town, on one line.
         */
        private @Nullable String associationAddress() {
            if (association == null) return null;
            return stations.findById(association.homeStationId())
                    .map(home -> Stream.of(
                                    home.addressLine(),
                                    Stream.of(home.postalCode(), home.city())
                                            .filter(part -> part != null && !part.isBlank())
                                            .collect(Collectors.joining(" ")))
                            .filter(part -> part != null && !part.isBlank())
                            .collect(Collectors.joining(", ")))
                    .orElse(null);
        }

        /** The answer of a question as plain text, as a pronoun mapping or a date reads it. */
        private @Nullable String storedAnswer(int whose, int fieldId) {
            return profileFields
                    .findValue(whose, fieldId)
                    .map(value -> QuestionValues.read(value.value()))
                    .orElse(null);
        }

        private Map<Integer, String> memberNames(FieldType type, String stored) {
            if (!type.namesMembers()) return Map.of();
            return QuestionValues.memberIds(QuestionValues.read(stored)).stream()
                    .distinct()
                    .collect(Collectors.toMap(
                            Function.identity(), id -> Objects.requireNonNullElse(names.official(id), "")));
        }

        private Optional<ProfileField> field(int fieldId) {
            return fields.computeIfAbsent(fieldId, id -> profileFields
                    .findById(id)
                    .filter(field -> field.stationId() == stationId())
                    .filter(field -> field.fieldType().holdsValue()));
        }

        private Optional<StationMember> membership(int id) {
            return memberships.computeIfAbsent(id, members::findById);
        }

        /**
         * The issuer, where they are still a current member of the station the document is drawn at; one
         * who left or belongs elsewhere issues nothing there.
         */
        private Optional<Integer> issuer() {
            Integer id = context.issuer().memberId();
            if (id == null) return Optional.empty();
            return membership(id)
                    .filter(member -> member.stationId() == stationId() && !member.former())
                    .map(StationMember::id);
        }

        private Optional<StationMember> guardian(int index) {
            if (guardians == null) guardians = members.findManagers(memberId);
            return index < guardians.size() ? Optional.of(guardians.get(index)) : Optional.empty();
        }

        /**
         * Whether a key reads the second guardian of a member who has none, which prints empty rather than
         * counting as missing: a member with one guardian is complete.
         */
        boolean ofAbsentSecondGuardian(String key) {
            return key.startsWith(PlaceholderCatalogue.GUARDIANS.get(1))
                    && guardian(1).isEmpty();
        }

        private @Nullable String guardianName(int index, Function<Integer, @Nullable String> name) {
            return guardian(index).map(guardian -> name.apply(guardian.id())).orElse(null);
        }

        private @Nullable String event(Function<GenerationContext.EventFacts, @Nullable String> read) {
            var event = context.event();
            return event == null ? null : read.apply(event);
        }

        /**
         * The member, and every guardian whose data one of the keys reads.
         */
        List<DataSubject> subjects(Set<String> keys) {
            var subjects = new ArrayList<DataSubject>();
            subjects.add(new DataSubject(memberId, SubjectRole.MEMBER));
            for (int index = 0; index < PlaceholderCatalogue.GUARDIANS.size(); index++) {
                String prefix = PlaceholderCatalogue.GUARDIANS.get(index);
                if (keys.stream().noneMatch(key -> key.startsWith(prefix))) continue;
                guardian(index)
                        .ifPresent(guardian -> subjects.add(new DataSubject(guardian.id(), SubjectRole.GUARDIAN)));
            }
            return subjects;
        }

        private int stationId() {
            return station == null ? 0 : station.id();
        }

        private LocalDate today() {
            return LocalDate.now(clock.withZone(zone));
        }
    }

    /**
     * An answer to a profile question as it is stored.
     *
     * @param type   the type of the question
     * @param stored the answer
     */
    private record Answer(FieldType type, String stored) {}
}
