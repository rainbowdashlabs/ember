/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.ProfileField;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.MemberGroupRepository;
import dev.chojo.ember.repository.ProfileFieldRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Singleton
public class MemberImportService {
    private static final Logger log = LoggerFactory.getLogger(MemberImportService.class);
    private static final DateTimeFormatter DE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final PasswordHasher passwordHasher;

    @Inject
    public MemberImportService(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            ProfileFieldRepository profileFieldRepository,
            PasswordHasher passwordHasher) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.passwordHasher = passwordHasher;
    }

    // -- API records --

    public record ColumnMapping(
            String csvColumn,
            String target,
            int mergeOrder,
            String mergeSeparator,
            Map<String, String> valueMap,
            String splitChar,
            int splitIndex) {}
    // target values: "skip", "firstName", "lastName", "email", "group",
    //   "contact1Name", "contact1Phone", "contact1Email",
    //   "contact2Name", "contact2Phone", "contact2Email",
    //   "field:<fieldId>" (profile field by id)

    public record ParseResult(List<String> headers, List<List<String>> rows) {}

    public record MemberPreview(
            String firstName,
            String lastName,
            String email,
            String group,
            Map<String, String> profileFields,
            List<ContactPreview> contacts) {}

    public record ContactPreview(String name, String firstName, String lastName, String phone, String email) {}

    public record PreviewResult(List<MemberPreview> members, List<String> warnings) {}

    public record ImportResult(
            int membersCreated,
            int managersCreated,
            int managersLinked,
            int groupsAssigned,
            int profileFieldsSet,
            List<String> warnings) {}

    public record TeamImportResult(
            int membersCreated, int groupsAssigned, int profileFieldsSet, List<String> warnings) {}

    // -- Parse CSV headers --

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

    // -- Preview with mapping --

    public PreviewResult preview(int stationId, String csv, String separator, List<ColumnMapping> mappings) {
        var parsed = parseCsv(csv, separator);
        var profileFields = profileFieldRepository.findByStation(stationId);
        var warnings = new ArrayList<String>();
        var members = new ArrayList<MemberPreview>();

        for (int i = 0; i < parsed.rows().size(); i++) {
            var row = mapRow(parsed.headers(), parsed.rows().get(i));
            var mapped = applyMappings(row, mappings, profileFields);
            if (mapped.firstName().isEmpty() && mapped.lastName().isEmpty()) {
                warnings.add("Zeile " + (i + 2) + ": Kein Name, übersprungen");
                continue;
            }
            members.add(mapped);
        }

        return new PreviewResult(members, warnings);
    }

    // -- Import with mapping --

    public ImportResult importMembers(int stationId, String csv, String separator, List<ColumnMapping> mappings) {
        var parsed = parseCsv(csv, separator);
        var profileFields = profileFieldRepository.findByStation(stationId);
        var groups = new ArrayList<>(memberGroupRepository.findByStation(stationId));
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        var memberRole = stationMemberRepository.findRoleByName(Roles.MEMBER).orElseThrow();
        var memberManagerRole =
                stationMemberRepository.findRoleByName(Roles.MEMBER_MANAGER).orElseThrow();
        var warnings = new ArrayList<String>();
        var managerCache = new HashMap<String, StationMember>();
        int membersCreated = 0, managersCreated = 0, managersLinked = 0, groupsAssigned = 0, profileFieldsSet = 0;

        for (int i = 0; i < parsed.rows().size(); i++) {
            var row = mapRow(parsed.headers(), parsed.rows().get(i));
            var mapped = applyMappings(row, mappings, profileFields);
            if (mapped.firstName().isEmpty() && mapped.lastName().isEmpty()) continue;

            String email =
                    mapped.email().isBlank() ? generateEmail(mapped.firstName(), mapped.lastName()) : mapped.email();

            if (accountRepository.findByEmail(email).isPresent()) {
                warnings.add("Zeile " + (i + 2) + ": " + email + " existiert bereits, übersprungen");
                continue;
            }

            var account = accountRepository.create(email, mapped.firstName(), mapped.lastName(), true);
            accountRepository.createCredential(account.id(), passwordHasher.hash(generatePassword()));
            var member = stationMemberRepository.create(stationId, account.id());
            stationMemberRepository.addRole(member.id(), loginRole.id());
            stationMemberRepository.addRole(member.id(), memberRole.id());
            membersCreated++;

            // Group
            if (!mapped.group().isBlank()) {
                var group = findOrCreateGroup(groups, stationId, mapped.group());
                memberGroupRepository.addMember(group.id(), member.id());
                groupsAssigned++;
            }

            // Profile fields
            for (var entry : mapped.profileFields().entrySet()) {
                var field = profileFields.stream()
                        .filter(f -> String.valueOf(f.id()).equals(entry.getKey()))
                        .findFirst();
                if (field.isPresent() && !entry.getValue().isBlank()) {
                    String value =
                            maybeConvertDate(entry.getValue(), field.get().fieldType());
                    profileFieldRepository.setValue(member.id(), field.get().id(), value);
                    profileFieldsSet++;
                }
            }

            // Contacts → managers
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
                    String mgrEmail = contact.email().isBlank() ? generateEmail(mgrFirst, mgrLast) : contact.email();

                    var mgrExisting = accountRepository.findByEmail(mgrEmail);
                    if (mgrExisting.isPresent()) {
                        var mgrMember = stationMemberRepository.findByStationAndAccount(
                                stationId, mgrExisting.get().id());
                        if (mgrMember.isPresent()) manager = mgrMember.get();
                    }

                    if (manager == null) {
                        var mgrAccount = accountRepository.create(mgrEmail, mgrFirst, mgrLast, true);
                        accountRepository.createCredential(mgrAccount.id(), passwordHasher.hash(generatePassword()));
                        manager = stationMemberRepository.create(stationId, mgrAccount.id());
                        stationMemberRepository.addRole(manager.id(), loginRole.id());
                        stationMemberRepository.addRole(manager.id(), memberManagerRole.id());
                        managersCreated++;

                        if (!contact.phone().isBlank()) {
                            int mgrId = manager.id();
                            profileFields.stream()
                                    .filter(f -> f.name().equals("Mobilnummer")
                                            && f.scope().name().equals("MEMBER_MANAGER"))
                                    .findFirst()
                                    .ifPresent(f -> profileFieldRepository.setValue(mgrId, f.id(), contact.phone()));
                        }
                    }
                    managerCache.put(mgrKey, manager);
                }

                stationMemberRepository.addManager(manager.id(), member.id());
                managersLinked++;
            }
        }

        return new ImportResult(
                membersCreated, managersCreated, managersLinked, groupsAssigned, profileFieldsSet, warnings);
    }

    // -- Team Import --

    public TeamImportResult importTeamMembers(
            int stationId, String csv, String separator, List<ColumnMapping> mappings) {
        var parsed = parseCsv(csv, separator);
        var profileFields = profileFieldRepository.findByStation(stationId);
        var groups = new ArrayList<>(memberGroupRepository.findByStation(stationId));
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        var teamRole = stationMemberRepository.findRoleByName(Roles.TEAM).orElseThrow();
        var warnings = new ArrayList<String>();
        int membersCreated = 0, groupsAssigned = 0, profileFieldsSet = 0;

        for (int i = 0; i < parsed.rows().size(); i++) {
            var row = mapRow(parsed.headers(), parsed.rows().get(i));
            var mapped = applyMappings(row, mappings, profileFields);
            if (mapped.firstName().isEmpty() && mapped.lastName().isEmpty()) continue;

            String email =
                    mapped.email().isBlank() ? generateEmail(mapped.firstName(), mapped.lastName()) : mapped.email();

            if (accountRepository.findByEmail(email).isPresent()) {
                warnings.add("Zeile " + (i + 2) + ": " + email + " existiert bereits, übersprungen");
                continue;
            }

            var account = accountRepository.create(email, mapped.firstName(), mapped.lastName(), true);
            accountRepository.createCredential(account.id(), passwordHasher.hash(generatePassword()));
            var member = stationMemberRepository.create(stationId, account.id());
            stationMemberRepository.addRole(member.id(), loginRole.id());
            stationMemberRepository.addRole(member.id(), teamRole.id());
            membersCreated++;

            // Group
            if (!mapped.group().isBlank()) {
                var group = findOrCreateGroup(groups, stationId, mapped.group());
                memberGroupRepository.addMember(group.id(), member.id());
                groupsAssigned++;
            }

            // Profile fields
            for (var entry : mapped.profileFields().entrySet()) {
                var field = profileFields.stream()
                        .filter(f -> String.valueOf(f.id()).equals(entry.getKey()))
                        .findFirst();
                if (field.isPresent() && !entry.getValue().isBlank()) {
                    String value =
                            maybeConvertDate(entry.getValue(), field.get().fieldType());
                    profileFieldRepository.setValue(member.id(), field.get().id(), value);
                    profileFieldsSet++;
                }
            }
        }

        return new TeamImportResult(membersCreated, groupsAssigned, profileFieldsSet, warnings);
    }

    // -- Mapping logic --

    private MemberPreview applyMappings(
            Map<String, String> row, List<ColumnMapping> mappings, List<ProfileField> fields) {
        // Group mappings by target, sorted by mergeOrder for merging
        var byTarget = new LinkedHashMap<String, List<ColumnMapping>>();
        for (var m : mappings) {
            if ("skip".equals(m.target())) continue;
            byTarget.computeIfAbsent(m.target(), k -> new ArrayList<>()).add(m);
        }
        // Sort each group by mergeOrder
        byTarget.values().forEach(list -> list.sort((a, b) -> Integer.compare(a.mergeOrder(), b.mergeOrder())));

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
                            var arr = managerData.computeIfAbsent(idx, k -> new String[] {"", "", "", ""});
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

        // Resolve profile field names for preview
        var namedFields = new LinkedHashMap<String, String>();
        for (var entry : profileFieldValues.entrySet()) {
            var field = fields.stream()
                    .filter(f -> String.valueOf(f.id()).equals(entry.getKey()))
                    .findFirst();
            String name = field.map(ProfileField::name).orElse("Feld #" + entry.getKey());
            namedFields.put(name, entry.getValue());
        }

        // Build contacts — skip if no name or no email (managers need email to log in)
        var contacts = new ArrayList<ContactPreview>();
        managerData.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String mgrFirst = entry.getValue()[0];
            String mgrLast = entry.getValue()[1];
            String mgrPhone = entry.getValue()[2];
            String mgrEmail = entry.getValue()[3];
            String mgrName = (mgrFirst + " " + mgrLast).trim();
            if (!mgrName.isBlank() && !mgrEmail.isBlank()) {
                contacts.add(new ContactPreview(mgrName, mgrFirst, mgrLast, mgrPhone, mgrEmail));
            }
        });

        return new MemberPreview(firstName, lastName, email, group, namedFields, contacts);
    }

    // -- Helpers --

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

    private String maybeConvertDate(String value, String fieldType) {
        if (!"date".equals(fieldType)) return value;
        try {
            return LocalDate.parse(value, DE_DATE).toString();
        } catch (Exception e) {
            // Try ISO format already
            try {
                LocalDate.parse(value);
                return value;
            } catch (Exception ignored) {
            }
            return value;
        }
    }

    private MemberGroup findOrCreateGroup(List<MemberGroup> groups, int stationId, String name) {
        for (var g : groups) {
            if (g.name().equalsIgnoreCase(name)) return g;
        }
        var created = memberGroupRepository.create(stationId, name);
        groups.add(created);
        return created;
    }

    private String generateEmail(String firstName, String lastName) {
        return (firstName + "." + lastName)
                        .toLowerCase()
                        .replace("ä", "ae")
                        .replace("ö", "oe")
                        .replace("ü", "ue")
                        .replace("ß", "ss")
                        .replaceAll("[^a-z0-9.@]", "")
                + "@import.local";
    }

    private String generatePassword() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
