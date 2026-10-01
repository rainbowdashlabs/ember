/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionKind;
import dev.chojo.ember.feature.question.QuestionValues;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for importing members from CSV data with configurable column mappings.
 * Supports creating accounts with invitation emails and assigning groups.
 */
@Singleton
public class MemberImportService {
    private static final Logger log = LoggerFactory.getLogger(MemberImportService.class);
    private static final DateTimeFormatter DE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final GroupMembershipService groupMemberships;
    private final ProfileFieldRepository profileFieldRepository;
    private final AccountInviteService accountInviteService;

    @Inject
    public MemberImportService(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            GroupMembershipService groupMemberships,
            ProfileFieldRepository profileFieldRepository,
            AccountInviteService accountInviteService) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.groupMemberships = groupMemberships;
        this.profileFieldRepository = profileFieldRepository;
        this.accountInviteService = accountInviteService;
    }

    // -- API records --

    /**
     * Parses a CSV string into headers and rows using the specified separator.
     *
     * @param csv       the CSV content
     * @param separator the column separator (defaults to ";")
     * @return the parsed headers and data rows
     */
    public ParseResult parseCsv(String csv, String separator) {
        String sep = separator != null && !separator.isBlank() ? separator : ";";
        var lines = csv.split("\n");
        if (lines.length < 1) return new ParseResult(List.of(), List.of());

        var headers = parseLine(lines[0], sep);
        var rows = new ArrayList<List<String>>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            rows.add(parseLine(lines[i], sep));
        }
        return new ParseResult(headers, rows);
    }

    /**
     * The fields of a station that take an answer, which are the only ones a column can be mapped
     * onto. A heading between fields is not something a spreadsheet has a column for, and an age is
     * counted from a date rather than read from a cell.
     */
    private List<ProfileField> valueFields(int stationId) {
        return profileFieldRepository.findByStation(stationId).stream()
                .filter(field -> field.fieldType().holdsValue())
                .toList();
    }

    /**
     * Generates a preview of what the import would create without persisting anything.
     *
     * @param stationId the target station
     * @param csv       the CSV content
     * @param separator the column separator
     * @param mappings  the column-to-field mappings
     * @param ignored   the rows the reader has struck out, by their place in the file
     * @return the preview with member entries and warnings
     */
    public PreviewResult preview(
            int stationId, String csv, String separator, List<ColumnMapping> mappings, List<Integer> ignored) {
        var parsed = parseCsv(csv, separator);
        var profileFields = valueFields(stationId);
        var struckOut = struckOut(ignored);
        var warnings = new ArrayList<String>();
        var members = new ArrayList<MemberPreview>();

        for (int i = 0; i < parsed.rows().size(); i++) {
            var row = mapRow(parsed.headers(), parsed.rows().get(i));
            var mapped = applyMappings(row, mappings, profileFields);
            if (mapped.firstName().isEmpty() && mapped.lastName().isEmpty()) {
                warnings.add("Zeile " + (i + 2) + ": Kein Name, übersprungen");
                continue;
            }
            warnAboutRefusedCells(mapped, profileFields, i + 2, warnings);
            members.add(mapped.at(i, struckOut.contains(i)));
        }

        return new PreviewResult(members, warnings);
    }

    /**
     * The rows the reader struck out, as a set to ask.
     *
     * <p>A list nobody sent is nobody struck out, which is the ordinary case and must not be read as
     * "every row" or as an error.
     */
    private Set<Integer> struckOut(List<Integer> ignored) {
        return ignored == null ? Set.of() : Set.copyOf(ignored);
    }

    /**
     * Imports members from CSV data, creating accounts, assigning roles and groups,
     * setting profile fields, and linking guardian/manager contacts.
     *
     * @param stationId the target station
     * @param csv       the CSV content
     * @param separator the column separator
     * @param mappings  the column-to-field mappings
     * @param ignored   the rows the reader struck out in the preview, by their place in the file
     * @param setupMail whether the setup mails leave with the accounts or wait to be sent by hand
     * @return the import result with counts and warnings
     */
    public ImportResult importMembers(
            int stationId,
            String csv,
            String separator,
            List<ColumnMapping> mappings,
            List<Integer> ignored,
            SetupMail setupMail) {
        var parsed = parseCsv(csv, separator);
        var struckOut = struckOut(ignored);
        var profileFields = valueFields(stationId);
        var groups = new ArrayList<>(memberGroupRepository.findByStation(stationId));
        var loginRole = stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow();
        var memberRole = stationMemberRepository
                .findPermissionByName(StationPermission.USER)
                .orElseThrow();
        var guardianRole = stationMemberRepository
                .findPermissionByName(StationPermission.MEMBER_GUARDIAN)
                .orElseThrow();
        var warnings = new ArrayList<String>();
        var managerCache = new HashMap<String, StationMember>();
        int membersCreated = 0, managersCreated = 0, managersLinked = 0, groupsAssigned = 0, profileFieldsSet = 0;

        for (int i = 0; i < parsed.rows().size(); i++) {
            if (struckOut.contains(i)) continue;
            var row = mapRow(parsed.headers(), parsed.rows().get(i));
            var mapped = applyMappings(row, mappings, profileFields);
            if (mapped.firstName().isEmpty() && mapped.lastName().isEmpty()) continue;

            var already = whoIsAlreadyHere(stationId, mapped);
            if (already.isPresent()) {
                warnings.add("Zeile " + (i + 2) + ": " + already.get() + " ist bereits an der Wache, übersprungen");
                continue;
            }

            String email = mapped.email().trim();

            AccountInviteService.Invited invited;
            try {
                invited = email.isBlank()
                        ? accountInviteService.createWithoutAddress(stationId, mapped.firstName(), mapped.lastName())
                        : accountInviteService.resolveOrCreate(
                                stationId, email, mapped.firstName(), mapped.lastName(), setupMail);
            } catch (AccountInviteService.EmailInUseException e) {
                warnings.add("Zeile " + (i + 2) + ": " + email + " gehört bereits jemand anderem, übersprungen");
                continue;
            }
            var member =
                    stationMemberRepository.create(stationId, invited.account().id());
            stationMemberRepository.setUserType(member.id(), StationUserType.MEMBER);
            stationMemberRepository.grantPermission(member.id(), memberRole.id());
            membersCreated++;

            if (assignGroup(groups, stationId, member.id(), mapped.group(), i + 2, warnings)) {
                groupsAssigned++;
            }

            profileFieldsSet += storeAnswers(member.id(), mapped, profileFields, i + 2, warnings);

            for (var contact : mapped.contacts()) {
                if (contact.name().isBlank()) continue;
                String mgrKey =
                        contact.name().toLowerCase() + "|" + contact.email().toLowerCase();
                var manager = managerCache.get(mgrKey);

                if (manager == null && !contact.email().isBlank()) {
                    var existing = accountRepository.findByEmail(contact.email());
                    if (existing.isPresent()) {
                        var existingMember = stationMemberRepository.findByStationAndAccount(
                                stationId, existing.get().id());
                        if (existingMember.isPresent()) manager = existingMember.get();
                    }
                }

                if (manager == null) {
                    String mgrFirst = contact.firstName().isBlank() ? contact.name() : contact.firstName();
                    String mgrLast = contact.lastName().isBlank() ? mapped.lastName() : contact.lastName();
                    String mgrEmail = contact.email().trim();

                    if (!mgrEmail.isBlank()) {
                        var mgrExisting = accountRepository.findByEmail(mgrEmail);
                        if (mgrExisting.isPresent()) {
                            var mgrMember = stationMemberRepository.findByStationAndAccount(
                                    stationId, mgrExisting.get().id());
                            if (mgrMember.isPresent()) manager = mgrMember.get();
                        }
                    }
                    if (manager == null) {
                        manager = stationMemberRepository
                                .findByStationAndName(stationId, mgrFirst, mgrLast)
                                .orElse(null);
                    }

                    if (manager == null) {
                        AccountInviteService.Invited mgrInvited;
                        try {
                            mgrInvited = mgrEmail.isBlank()
                                    ? accountInviteService.createWithoutAddress(stationId, mgrFirst, mgrLast)
                                    : accountInviteService.resolveOrCreate(
                                            stationId, mgrEmail, mgrFirst, mgrLast, setupMail);
                        } catch (AccountInviteService.EmailInUseException e) {
                            warnings.add("Zeile " + (i + 2) + ": " + mgrEmail
                                    + " gehört bereits jemand anderem, Kontaktperson übersprungen");
                            continue;
                        }
                        manager = stationMemberRepository.create(
                                stationId, mgrInvited.account().id());
                        stationMemberRepository.setUserType(manager.id(), StationUserType.GUARDIAN);
                        stationMemberRepository.grantPermission(manager.id(), loginRole.id());
                        stationMemberRepository.grantPermission(manager.id(), guardianRole.id());
                        managersCreated++;

                        if (!contact.phone().isBlank()) {
                            int mgrId = manager.id();
                            int line = i + 2;
                            // A question is written once now, so the guardians' mobile number is the
                            // station's mobile number: there is no second copy to tell it apart from.
                            profileFields.stream()
                                    .filter(f -> f.name().equals("Mobilnummer"))
                                    .findFirst()
                                    .ifPresent(f -> storeAnswer(mgrId, f, contact.phone(), line, warnings));
                        }
                    }
                    managerCache.put(mgrKey, manager);
                }

                stationMemberRepository.addManager(manager.id(), member.id());
                managersLinked++;
            }
        }

        log.info(
                "Member import completed: station={}, membersCreated={}, managersCreated={}, managersLinked={}, "
                        + "groupsAssigned={}, profileFieldsSet={}, warnings={}",
                stationId,
                membersCreated,
                managersCreated,
                managersLinked,
                groupsAssigned,
                profileFieldsSet,
                warnings.size());
        return new ImportResult(
                membersCreated, managersCreated, managersLinked, groupsAssigned, profileFieldsSet, warnings);
    }

    /**
     * Imports team members (adults) from CSV data with TEAM role instead of MEMBER role.
     *
     * @param stationId the target station
     * @param csv       the CSV content
     * @param separator the column separator
     * @param mappings  the column-to-field mappings
     * @param setupMail whether the setup mails leave with the accounts or wait to be sent by hand
     * @return the team import result with counts and warnings
     */
    public TeamImportResult importTeamMembers(
            int stationId,
            String csv,
            String separator,
            List<ColumnMapping> mappings,
            List<Integer> ignored,
            SetupMail setupMail) {
        var parsed = parseCsv(csv, separator);
        var profileFields = valueFields(stationId);
        var struckOut = struckOut(ignored);
        var groups = new ArrayList<>(memberGroupRepository.findByStation(stationId));
        var loginRole = stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow();
        var warnings = new ArrayList<String>();
        int membersCreated = 0, groupsAssigned = 0, profileFieldsSet = 0;

        for (int i = 0; i < parsed.rows().size(); i++) {
            if (struckOut.contains(i)) continue;
            var row = mapRow(parsed.headers(), parsed.rows().get(i));
            var mapped = applyMappings(row, mappings, profileFields);
            if (mapped.firstName().isEmpty() && mapped.lastName().isEmpty()) continue;

            var already = whoIsAlreadyHere(stationId, mapped);
            if (already.isPresent()) {
                warnings.add("Zeile " + (i + 2) + ": " + already.get() + " ist bereits an der Wache, übersprungen");
                continue;
            }

            String email = mapped.email().trim();

            AccountInviteService.Invited invited;
            try {
                invited = email.isBlank()
                        ? accountInviteService.createWithoutAddress(stationId, mapped.firstName(), mapped.lastName())
                        : accountInviteService.resolveOrCreate(
                                stationId, email, mapped.firstName(), mapped.lastName(), setupMail);
            } catch (AccountInviteService.EmailInUseException e) {
                warnings.add("Zeile " + (i + 2) + ": " + email + " gehört bereits jemand anderem, übersprungen");
                continue;
            }
            var member =
                    stationMemberRepository.create(stationId, invited.account().id());
            stationMemberRepository.setUserType(member.id(), StationUserType.TEAM);
            stationMemberRepository.grantPermission(member.id(), loginRole.id());
            membersCreated++;

            if (assignGroup(groups, stationId, member.id(), mapped.group(), i + 2, warnings)) {
                groupsAssigned++;
            }

            profileFieldsSet += storeAnswers(member.id(), mapped, profileFields, i + 2, warnings);
        }

        log.info(
                "Team member import completed: station={}, membersCreated={}, groupsAssigned={}, "
                        + "profileFieldsSet={}, warnings={}",
                stationId,
                membersCreated,
                groupsAssigned,
                profileFieldsSet,
                warnings.size());
        return new TeamImportResult(membersCreated, groupsAssigned, profileFieldsSet, warnings);
    }

    private MemberPreview applyMappings(
            Map<String, String> row, List<ColumnMapping> mappings, List<ProfileField> fields) {
        // Group mappings by target, sorted by mergeOrder for merging
        var byTarget = new LinkedHashMap<String, List<ColumnMapping>>();
        for (var m : mappings) {
            if ("skip".equals(m.target())) continue;
            byTarget.computeIfAbsent(m.target(), _ -> new ArrayList<>()).add(m);
        }
        // Sort each group by mergeOrder
        byTarget.values().forEach(list -> list.sort(Comparator.comparingInt(ColumnMapping::mergeOrder)));

        String firstName = "", lastName = "", email = "", group = "";
        var profileFieldValues = new LinkedHashMap<String, String>();
        var managerData = new HashMap<Integer, String[]>();

        for (var entry : byTarget.entrySet()) {
            String target = entry.getKey();
            String val = buildMergedValue(row, entry.getValue());
            if (val.isEmpty()) continue;

            switch (target) {
                case "firstName" -> firstName = val;
                case "lastName" -> lastName = val;
                case "email" -> email = val;
                case "group" -> group = val;
                default -> {
                    if (target.startsWith("field:")) {
                        profileFieldValues.put(target.substring(6), val);
                    } else if (target.startsWith("manager:")) {
                        var parts = target.split(":", 3);
                        if (parts.length == 3) {
                            int idx = Integer.parseInt(parts[1]);
                            var arr = managerData.computeIfAbsent(idx, _ -> new String[] {"", "", "", ""});
                            switch (parts[2]) {
                                case "firstName" -> arr[0] = val;
                                case "lastName" -> arr[1] = val;
                                case "phone" -> arr[2] = val;
                                case "email" -> arr[3] = val;
                            }
                        }
                    }
                }
            }
        }

        var contacts = new ArrayList<ContactPreview>();
        managerData.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String[] named = splitContactName(entry.getValue()[0], entry.getValue()[1]);
            String mgrPhone = entry.getValue()[2];
            String mgrEmail = entry.getValue()[3];
            String mgrName = (named[0] + " " + named[1]).trim();
            if (!mgrName.isBlank()) {
                contacts.add(new ContactPreview(mgrName, named[0], named[1], mgrPhone, mgrEmail));
            }
        });

        return new MemberPreview(firstName, lastName, email, group, profileFieldValues, contacts);
    }

    /**
     * The given name and surname of a contact, out of however many columns the file spends on them.
     *
     * <p>A youth list usually spends one, headed "Kontakt 1" and holding a whole name. Pointed at the
     * given name, as the wizard does by itself, it left the surname empty and the parent was written
     * down as "Rita Sommer Sommer", the child's surname standing in for the missing one. The last word
     * of a whole name is the surname it already carries, so it is read as one. A file that does spend
     * two columns is left exactly as it is, and so is a name of one word.
     *
     * @param first what was pointed at the given name
     * @param last  what was pointed at the surname, often nothing
     * @return the given name and the surname, in that order
     */
    private String[] splitContactName(String first, String last) {
        if (!last.isBlank()) return new String[] {first, last};
        int lastSpace = first.trim().lastIndexOf(' ');
        if (lastSpace < 0) return new String[] {first, last};
        return new String[] {
            first.trim().substring(0, lastSpace).trim(), first.trim().substring(lastSpace + 1)
        };
    }

    private String buildMergedValue(Map<String, String> row, List<ColumnMapping> mappingsForTarget) {
        var parts = new ArrayList<String>();
        String separator = " ";
        for (var m : mappingsForTarget) {
            String raw = row.getOrDefault(m.csvColumn(), "").trim();
            if (raw.isEmpty()) continue;

            // Apply split if configured
            if (m.splitChar() != null && !m.splitChar().isEmpty()) {
                String[] splitParts = raw.split(Pattern.quote(m.splitChar()), -1);
                int idx = m.splitIndex();
                if (idx < 0) idx = splitParts.length + idx; // negative index from end
                if (idx >= 0 && idx < splitParts.length) {
                    raw = splitParts[idx].trim();
                } else {
                    raw = "";
                }
                if (raw.isEmpty()) continue;
            }

            // Apply value mapping if present
            if (m.valueMap() != null && !m.valueMap().isEmpty()) {
                String mapped = m.valueMap().get(raw);
                if (mapped == null) {
                    for (var e : m.valueMap().entrySet()) {
                        if (e.getKey().equalsIgnoreCase(raw)) {
                            mapped = e.getValue();
                            break;
                        }
                    }
                }
                if (mapped != null) raw = mapped;
            }

            parts.add(raw);
            if (m.mergeSeparator() != null && !m.mergeSeparator().isEmpty()) {
                separator = m.mergeSeparator();
            }
        }
        return String.join(separator, parts);
    }

    private List<String> parseLine(String line, String sep) {
        var result = new ArrayList<String>();
        for (String col : line.split(Pattern.quote(sep), -1)) {
            result.add(col.trim());
        }
        return result;
    }

    // -- Parse CSV headers --

    private Map<String, String> mapRow(List<String> headers, List<String> cols) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < headers.size() && i < cols.size(); i++) {
            String header = headers.get(i);
            // Handle duplicate headers by appending index
            if (map.containsKey(header)) {
                int suffix = 2;
                while (map.containsKey(header + " (" + suffix + ")")) suffix++;
                header = header + " (" + suffix + ")";
            }
            map.put(header, cols.get(i));
        }
        return map;
    }

    // -- Preview with mapping --

    /**
     * Whether this row is about somebody the station already has, and what to call them if so.
     *
     * <p>The address decides where the row carries one, because that is the one thing about a person
     * that is theirs alone. Where it carries none, the name decides, and only within this station:
     * two people of that name here cannot be told apart by anything in the row, and the same list
     * imported twice should leave one of each rather than a second copy of everybody.
     *
     * <p>Trimmed and without regard to case on both sides, so a stray space in a spreadsheet does not
     * read as a different person.
     *
     * <p>Contact persons are matched the same way, for the same reason: one with no address of their
     * own is the one of that name here, or a list read twice leaves two of them.
     *
     * @param stationId the station being imported into
     * @param mapped    the row as the mappings read it
     * @return what to call the person already here, or empty where this row is new
     */
    private Optional<String> whoIsAlreadyHere(int stationId, MemberPreview mapped) {
        if (!mapped.email().isBlank()) {
            String email = mapped.email().trim();
            var account = accountRepository.findByEmail(email);
            if (account.isPresent()) return Optional.of(email);
        }
        return stationMemberRepository
                .findByStationAndName(stationId, mapped.firstName(), mapped.lastName())
                .map(member ->
                        NameParts.of(mapped.firstName(), mapped.lastName()).official());
    }

    /**
     * The cells of a row that answer one of the station's questions, each with its question, leaving
     * out empty cells and columns pointed at a question the station no longer has.
     */
    private static Map<ProfileField, String> answeredCells(MemberPreview mapped, List<ProfileField> fields) {
        var cells = new LinkedHashMap<ProfileField, String>();
        for (var entry : mapped.profileFields().entrySet()) {
            if (entry.getValue().isBlank()) continue;
            fields.stream()
                    .filter(field -> String.valueOf(field.id()).equals(entry.getKey()))
                    .findFirst()
                    .ifPresent(field -> cells.put(field, entry.getValue()));
        }
        return cells;
    }

    /**
     * Writes the answers of one row into a person's profile.
     *
     * @return how many answers were stored
     */
    private int storeAnswers(
            int memberId, MemberPreview mapped, List<ProfileField> fields, int line, List<String> warnings) {
        int stored = 0;
        for (var cell : answeredCells(mapped, fields).entrySet()) {
            if (storeAnswer(memberId, cell.getKey(), cell.getValue(), line, warnings)) stored++;
        }
        return stored;
    }

    /**
     * Warns about every cell of a row its question will not take, so the preview says it before the
     * import leaves it out.
     */
    private static void warnAboutRefusedCells(
            MemberPreview mapped, List<ProfileField> fields, int line, List<String> warnings) {
        for (var cell : answeredCells(mapped, fields).entrySet()) {
            if (acceptedAnswer(cell.getKey(), cell.getValue()).isEmpty()) {
                warnings.add(refusedCell(line, cell.getKey(), cell.getValue()));
            }
        }
    }

    /**
     * Writes one cell of the file into the answer a person gives to one of the station's questions.
     *
     * <p>The one way the import stores an answer, and it exists to be the only one. An answer is held
     * as JSON, so a cell never reaches the database as it stands: a telephone number reads as a
     * number with a leading zero, which JSON does not have, and the database refuses the entire
     * reading over the one cell. That went unnoticed twice because two places wrote answers.
     *
     * <p>The cell is measured against the question as an answer typed on the profile would be, and
     * kept in the same shape.
     *
     * @param memberId the person the answer belongs to, who may be the member or a guardian of theirs
     * @param field    the question being answered
     * @param cell     the cell as it stands in the file
     * @param line     the cell's line in the file, for the warning
     * @param warnings where a cell the question does not take is reported
     * @return whether an answer was stored
     */
    private boolean storeAnswer(int memberId, ProfileField field, String cell, int line, List<String> warnings) {
        var answer = acceptedAnswer(field, cell);
        if (answer.isEmpty()) {
            warnings.add(refusedCell(line, field, cell));
            return false;
        }
        JsonNode kept = QuestionValues.write(field.fieldType(), answer.get());
        if (kept == null) return false;
        profileFieldRepository.setValue(memberId, field.id(), kept);
        return true;
    }

    /**
     * The answer a cell gives, where the question takes it.
     *
     * <p>A cell the question does not take is left out rather than kept as text under a question that
     * cannot read it: a shirt size the station does not offer or a day that is not a day would sit
     * on the profile looking answered, and the row's warning is the one place it gets noticed.
     *
     * @param field the question the cell answers
     * @param cell  the cell as it stands in the file
     * @return the answer as plain text, or nothing where the question refuses it
     */
    private static Optional<String> acceptedAnswer(ProfileField field, String cell) {
        String answer = asAnswer(cell.trim(), field.fieldType());
        boolean refused = field.question()
                .flatMap(question -> QuestionCheck.answerIfGiven(question, answer))
                .isPresent();
        return refused ? Optional.empty() : Optional.of(answer);
    }

    /** What a row's warning says about a cell its question does not take. */
    private static String refusedCell(int line, ProfileField field, String cell) {
        return "Zeile " + line + ": \"" + cell.trim() + "\" passt nicht zum Feld " + field.name()
                + ", nicht übernommen";
    }

    /**
     * Turns a cell into the answer a question of its kind reads.
     *
     * <p>A spreadsheet writes a day the German way and yes and no in words; both are turned into
     * what the question takes. Everything else already is the answer, and what still does not read
     * as one is refused by the check rather than guessed at here.
     *
     * @param value     the cell, already trimmed
     * @param fieldType the kind of question it answers
     * @return the answer as plain text
     */
    private static String asAnswer(String value, FieldType fieldType) {
        var kind = fieldType.kind().orElse(null);
        if (kind == QuestionKind.DATE) return asIsoDate(value);
        if (kind == QuestionKind.BOOLEAN) return asBoolean(value);
        return value;
    }

    /** A German date as an ISO one, or the cell unchanged where it is not one. */
    private static String asIsoDate(String value) {
        try {
            return LocalDate.parse(value, DE_DATE).toString();
        } catch (DateTimeParseException notGerman) {
            return value;
        }
    }

    /** The words a spreadsheet says yes and no with, in both languages a station is likely to use. */
    private static String asBoolean(String value) {
        String said = value.toLowerCase();
        if (Set.of("ja", "yes", "true", "wahr", "x", "1").contains(said)) return "true";
        if (Set.of("nein", "no", "false", "falsch", "0").contains(said)) return "false";
        return value;
    }

    /**
     * Puts an imported member into the group their row names, creating the group where the station
     * has none by that name. A group that does not take them, because of its binding or its set, is
     * left out with a warning on the row rather than failing the import.
     *
     * @return {@code true} where the member is in the group afterwards
     */
    private boolean assignGroup(
            List<MemberGroup> groups, int stationId, int memberId, String groupName, int line, List<String> warnings) {
        if (groupName.isBlank()) return false;
        var group = findOrCreateGroup(groups, stationId, groupName);
        if (groupMemberships.joinAutomatically(group.id(), memberId)) return true;
        warnings.add("Zeile " + line + ": Die Gruppe " + group.name()
                + " nimmt dieses Mitglied nicht auf, nicht zugeordnet");
        return false;
    }

    private MemberGroup findOrCreateGroup(List<MemberGroup> groups, int stationId, String name) {
        for (var g : groups) {
            if (g.name().equalsIgnoreCase(name)) return g;
        }
        var created = memberGroupRepository.create(stationId, name);
        groups.add(created);
        return created;
    }

    // -- Team Import --

    // -- Mapping logic --

    // -- Helpers --

    /**
     * Maps a CSV column to a target field with optional value transformation, merging, and splitting.
     *
     * <p>What a column can be pointed at: {@code skip}, {@code firstName}, {@code lastName},
     * {@code email}, {@code group}, {@code field:<fieldId>} for one of the station's questions, and
     * {@code manager:<n>:firstName|lastName|phone|email} for the nth guardian on the row.
     */
    public record ColumnMapping(
            String csvColumn,
            String target,
            int mergeOrder,
            String mergeSeparator,
            Map<String, String> valueMap,
            String splitChar,
            int splitIndex) {}

    /**
     * Result of parsing CSV content into headers and data rows.
     */
    public record ParseResult(List<String> headers, List<List<String>> rows) {}

    /**
     * Preview of a single member to be imported, including mapped profile fields and contacts.
     */
    public record MemberPreview(
            String firstName,
            String lastName,
            String email,
            String group,
            /**
             * The answers this row carries, by the identifier of the question they answer. By
             * identifier and not by name, because the import writes them and looks the question up by
             * that: keyed by name, every mapped column was quietly dropped on the way in.
             */
            Map<String, String> profileFields,
            List<ContactPreview> contacts,
            /** Where this came from in the file, so a reader can strike out that one row. */
            int row,
            /** Whether the reader has struck it out, in which case the import walks past it. */
            boolean ignored) {
        public MemberPreview(
                String firstName,
                String lastName,
                String email,
                String group,
                Map<String, String> profileFields,
                List<ContactPreview> contacts) {
            this(firstName, lastName, email, group, profileFields, contacts, -1, false);
        }

        /** The same row, told where it came from and whether it was struck out. */
        public MemberPreview at(int row, boolean ignored) {
            return new MemberPreview(firstName, lastName, email, group, profileFields, contacts, row, ignored);
        }
    }

    /**
     * Preview of a guardian/contact extracted from the CSV row.
     */
    public record ContactPreview(String name, String firstName, String lastName, String phone, String email) {}

    /**
     * Result of a member import preview with member entries and any warnings.
     */
    public record PreviewResult(List<MemberPreview> members, List<String> warnings) {}

    /**
     * Result of a member import operation with counts and warnings.
     */
    public record ImportResult(
            int membersCreated,
            int managersCreated,
            int managersLinked,
            int groupsAssigned,
            int profileFieldsSet,
            List<String> warnings) {}

    /**
     * Result of a team member import operation with counts and warnings.
     */
    public record TeamImportResult(
            int membersCreated, int groupsAssigned, int profileFieldsSet, List<String> warnings) {}
}
