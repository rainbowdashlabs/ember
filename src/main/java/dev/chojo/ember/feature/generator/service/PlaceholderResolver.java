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
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.ResolvedValues;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * <p>Names are official names: the register's first name and surname, for the member, the guardians
 * and whoever generates the document. Only {@code member.calledName} reads the name a member is called
 * by, and only an informal template may name it. Profile answers are written the way exports write
 * them ({@link QuestionText#format}), so a date reads as a day and yes as a word of the template's
 * language. The pronouns follow the member's answer to the station's gender field ({@link GenderFields}),
 * in the template's language.
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
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
     * @return today's date and the data of the station and the association, which need no member
     */
    public Map<String, String> withoutMember(Owner owner, DocumentLanguage language) {
        var station = owner instanceof Owner.Station own
                ? stations.findById(own.stationId()).orElse(null)
                : null;
        var association =
                switch (owner) {
                    case Owner.Station own -> clusters.findByStation(own.stationId());
                    case Owner.Association own -> clusters.findById(own.clusterId());
                    case Owner.Instance ignored -> Optional.<Cluster>empty();
                };
        var reading = new Reading(
                new Subject(station, association.orElse(null), 0, language, new GenerationContext(null, null)));
        var values = new HashMap<String, String>();
        for (var placeholder : List.of(
                BuiltInPlaceholder.STATION_NAME,
                BuiltInPlaceholder.STATION_ADDRESS,
                BuiltInPlaceholder.STATION_POSTAL_CODE,
                BuiltInPlaceholder.STATION_CITY,
                BuiltInPlaceholder.ASSOCIATION_NAME,
                BuiltInPlaceholder.ASSOCIATION_ADDRESS,
                BuiltInPlaceholder.TODAY,
                BuiltInPlaceholder.TODAY_LONG)) {
            reading.value(placeholder.key()).ifPresent(value -> values.put(placeholder.key(), value));
        }
        return values;
    }

    /**
     * Whom values are read for.
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
        private final String language;
        private final ZoneId zone;
        private final Map<Integer, Optional<StationMember>> memberships = new HashMap<>();
        private final Map<Integer, Optional<ProfileField>> fields = new HashMap<>();
        private final Map<Integer, Optional<OwnedProfileField>> associationFields = new HashMap<>();
        private @Nullable List<StationMember> guardians;
        private boolean pronounsRead;
        private @Nullable PronounSet pronouns;

        Reading(Subject subject) {
            this.station = subject.station();
            this.association = subject.association();
            this.memberId = subject.memberId();
            this.context = subject.context();
            this.language = subject.language().code();
            this.zone = StationFormat.timezoneOf(station);
        }

        Optional<String> value(String key) {
            var builtIn = BuiltInPlaceholder.of(key);
            if (builtIn.isPresent()) return Optional.ofNullable(builtIn(builtIn.get()));
            var pronoun = PronounKey.parse(key);
            if (pronoun.isPresent()) return Optional.ofNullable(pronoun(pronoun.get()));
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
                case MEMBER_BIRTH_DATE -> birthDate().map(DAY::format).orElse(null);
                case MEMBER_AGE ->
                    birthDate()
                            .map(born ->
                                    String.valueOf(Period.between(born, today()).getYears()))
                            .orElse(null);
                case MEMBER_JOIN_DATE ->
                    membership(memberId)
                            .map(member -> DAY.format(member.joinDate()))
                            .orElse(null);
                case MEMBER_JOIN_MONTH ->
                    membership(memberId)
                            .map(member -> monthYear(member.joinDate()))
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
                case EVENT_START -> event(facts -> clock(facts.start()));
                case EVENT_END -> event(facts -> clock(facts.end()));
                case EVENT_LOCATION -> event(GenerationContext.EventFacts::location);
                case TODAY -> DAY.format(today());
                case TODAY_LONG -> longDay(today());
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
            if (!pronounsRead) {
                pronouns = genderPronouns().orElse(null);
                pronounsRead = true;
            }
            return Pronouns.of(pronoun, pronouns, first, language);
        }

        private Optional<PronounSet> genderPronouns() {
            return genders.askedAt(stationId()).flatMap(field -> field.config()
                    .pronounsOf(genders.answerOf(memberId, field).orElse(null), language));
        }

        private Optional<LocalDate> birthDate() {
            return profileFields.findAllByStationAndType(stationId(), FieldType.BIRTH_DATE).stream()
                    .findFirst()
                    .map(field -> storedAnswer(memberId, field.id()))
                    .flatMap(Reading::day);
        }

        private static Optional<LocalDate> day(@Nullable String answer) {
            if (answer == null || answer.isBlank()) return Optional.empty();
            String day = answer.length() > 10 ? answer.substring(0, 10) : answer;
            try {
                return Optional.of(LocalDate.parse(day));
            } catch (DateTimeParseException notADay) {
                return Optional.empty();
            }
        }

        private Optional<String> answer(int whose, String fieldKey) {
            return fieldIdOf(fieldKey).flatMap(fieldId -> field(fieldId)
                    .flatMap(field -> formatted(
                            field.fieldType(),
                            profileFields.findValue(whose, fieldId).map(ProfileFieldValue::value))));
        }

        /**
         * An answer to a question of the association, where the question reaches the station: the
         * association's adapter holds both the question and the answers.
         */
        private Optional<String> associationAnswer(int whose, String fieldKey) {
            return fieldIdOf(fieldKey).flatMap(fieldId -> associationField(fieldId)
                    .flatMap(field -> formatted(
                            field.type(),
                            fieldOwners.owner(FieldOrigin.CLUSTER).answersOf(whose).stream()
                                    .filter(value -> value.fieldId() == fieldId)
                                    .findFirst()
                                    .map(ProfileFieldValue::value))));
        }

        private Optional<String> formatted(FieldType type, Optional<String> stored) {
            return stored.map(value -> QuestionText.format(type, value, memberNames(type, value), language));
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

        private String clock(Instant instant) {
            return CLOCK.format(instant.atZone(zone));
        }

        private String monthYear(LocalDate date) {
            return DateTimeFormatter.ofPattern("MMMM yyyy", locale()).format(date);
        }

        private String longDay(LocalDate date) {
            String pattern = "en".equals(language) ? "MMMM d, yyyy" : "d. MMMM yyyy";
            return DateTimeFormatter.ofPattern(pattern, locale()).format(date);
        }

        private Locale locale() {
            return "en".equals(language) ? Locale.ENGLISH : Locale.GERMAN;
        }
    }
}
