/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imports station data from a previously exported JSON structure.
 * All IDs are remapped to avoid conflicts with existing data.
 */
@Singleton
public class StationImportService {
    private static final Logger log = LoggerFactory.getLogger(StationImportService.class);
    /**
     * Tables processed during import, in dependency order.
     * "station" is handled synchronously before the async phase.
     */
    private static final List<String> IMPORT_TABLES = List.of(
            "disabledModules",
            "members",
            "memberRoles",
            "groups",
            "groupMembers",
            "tags",
            "tagMembers",
            "managerRelations",
            "profileFields",
            "profileFieldValues",
            "eventCategories",
            "attendanceTemplates",
            "attendanceTemplateFields",
            "events",
            "inventories",
            "inventorySizes",
            "inventoryItems",
            "forms",
            "formQuestions");

    private static final int PAGE_SIZE = 500;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final ConcurrentHashMap<Integer, ImportProgress> activeImports = new ConcurrentHashMap<>();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "station-import");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public StationImportService(
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    private static String str(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static int intVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean boolVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    /**
     * Starts an asynchronous import from a remote Ember instance.
     * Fetches tables one by one via the token-authenticated export API.
     */
    @SuppressWarnings("unchecked")
    public ImportResult startRemoteImport(String sourceUrl, String token) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        var mapper = JsonMapper.builder().build();
        var httpClient = HttpClient.newHttpClient();

        // Fetch station table synchronously to create the station first
        Map<String, Object> stationTableData =
                fetchRemotePage(httpClient, mapper, baseUrl, token, "station", 0, PAGE_SIZE);
        var stationData = (Map<String, Object>) stationTableData.get("station");
        if (stationData == null) {
            throw new IllegalArgumentException("Remote station table missing 'station' field");
        }

        String stationName = str(stationData, "name", "Imported Station");
        var station = stationRepository.create(stationName);
        int stationId = station.id();

        if (stationData.containsKey("timezone")) {
            stationRepository.updateTimezone(stationId, str(stationData, "timezone", "Europe/Berlin"));
        }
        if (stationData.containsKey("locale")) {
            stationRepository.updateLocale(stationId, str(stationData, "locale", "de-DE"));
        }
        // Preserve the original station UUID so federation pairing codes still work
        if (stationData.containsKey("uid")) {
            try {
                stationRepository.updateUid(stationId, java.util.UUID.fromString(str(stationData, "uid", "")));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid station UID in import data, skipping UUID preservation");
            }
        }

        var progress = new ImportProgress(stationId, stationName, IMPORT_TABLES.size());
        activeImports.put(stationId, progress);

        importExecutor.submit(() -> {
            try {
                runRemoteImport(stationId, baseUrl, token, httpClient, mapper, progress);
            } catch (Exception e) {
                log.error("Remote import failed for station {}", stationId, e);
                progress.fail(e.getMessage());
            }
        });

        return new ImportResult(stationId, stationName, 0);
    }

    /**
     * Imports remote station data INTO an existing station, overwriting its data.
     * Links members to existing accounts by email when possible.
     */
    @SuppressWarnings("unchecked")
    public ImportResult startRemoteImportInto(int stationId, String sourceUrl, String token) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        var mapper = JsonMapper.builder().build();
        var httpClient = HttpClient.newHttpClient();

        // Fetch station metadata to apply settings
        Map<String, Object> stationTableData =
                fetchRemotePage(httpClient, mapper, baseUrl, token, "station", 0, PAGE_SIZE);
        var stationData = (Map<String, Object>) stationTableData.get("station");
        if (stationData != null) {
            if (stationData.containsKey("timezone")) {
                stationRepository.updateTimezone(stationId, str(stationData, "timezone", "Europe/Berlin"));
            }
            if (stationData.containsKey("locale")) {
                stationRepository.updateLocale(stationId, str(stationData, "locale", "de-DE"));
            }
        }

        String stationName =
                stationRepository.findById(stationId).map(Station::name).orElse("Station");
        var progress = new ImportProgress(stationId, stationName, IMPORT_TABLES.size());
        activeImports.put(stationId, progress);

        importExecutor.submit(() -> {
            try {
                runRemoteImport(stationId, baseUrl, token, httpClient, mapper, progress);
            } catch (Exception e) {
                log.error("Import-into failed for station {}", stationId, e);
                progress.fail(e.getMessage());
            }
        });

        return new ImportResult(stationId, stationName, 0);
    }

    /**
     * Returns the current progress for an active or recently completed import, or null if not found.
     */
    public ImportProgress getProgress(int stationId) {
        return activeImports.get(stationId);
    }

    /**
     * Synchronous full import. Used by tests and the legacy code path.
     */
    @SuppressWarnings("unchecked")
    public ImportResult importStation(Map<String, Object> data) {
        var idMap = new IdRemapper();
        int totalEntities = 0;

        // 1. Create station
        var stationData = (Map<String, Object>) data.get("station");
        String stationName = str(stationData, "name", "Imported Station");
        var station = stationRepository.create(stationName);
        int stationId = station.id();

        if (stationData.containsKey("timezone")) {
            stationRepository.updateTimezone(stationId, str(stationData, "timezone", "Europe/Berlin"));
        }
        if (stationData.containsKey("locale")) {
            stationRepository.updateLocale(stationId, str(stationData, "locale", "de-DE"));
        }
        totalEntities++;

        for (String table : IMPORT_TABLES) {
            totalEntities += importSingleTable(stationId, table, data, idMap);
        }

        log.info("Imported station '{}' (id={}) with {} entities", stationName, stationId, totalEntities);
        return new ImportResult(stationId, stationName, totalEntities);
    }

    /**
     * Exposed for testing: import a single table into an existing station.
     */
    public int importSingleTableForTest(int stationId, String tableName, Map<String, Object> data, IdRemapper idMap) {
        return importSingleTable(stationId, tableName, data, idMap);
    }

    private void runRemoteImport(
            int stationId,
            String baseUrl,
            String token,
            HttpClient httpClient,
            ObjectMapper mapper,
            ImportProgress progress) {
        var idMap = new IdRemapper();

        for (int i = 0; i < IMPORT_TABLES.size(); i++) {
            String table = IMPORT_TABLES.get(i);
            progress.startTable(i, table);
            fetchAndImportTablePaginated(stationId, table, baseUrl, token, httpClient, mapper, idMap);
            progress.completeTable();
        }

        progress.complete();
        log.info("Remote import completed for station '{}' (id={})", progress.stationName(), stationId);
    }

    @SuppressWarnings("unchecked")
    private void fetchAndImportTablePaginated(
            int stationId,
            String table,
            String baseUrl,
            String token,
            HttpClient httpClient,
            ObjectMapper mapper,
            IdRemapper idMap) {
        // "station" and "disabledModules" are small, single-page tables
        if ("station".equals(table) || "disabledModules".equals(table)) {
            var data = fetchRemotePage(httpClient, mapper, baseUrl, token, table, 0, PAGE_SIZE);
            importSingleTable(stationId, table, data, idMap);
            return;
        }

        int offset = 0;
        while (true) {
            var pageData = fetchRemotePage(httpClient, mapper, baseUrl, token, table, offset, PAGE_SIZE);
            int imported = importSingleTable(stationId, table, pageData, idMap);
            if (imported < PAGE_SIZE) break;
            offset += PAGE_SIZE;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRemotePage(
            HttpClient httpClient,
            ObjectMapper mapper,
            String baseUrl,
            String token,
            String table,
            int offset,
            int limit) {
        try {
            var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/" + table + "?offset=" + offset
                    + "&limit=" + limit);
            var request = HttpRequest.newBuilder().uri(uri).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to fetch table '" + table + "' from remote: HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), Map.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch table '" + table + "' from remote", e);
        }
    }

    @SuppressWarnings("unchecked")
    private int importSingleTable(int stationId, String tableName, Map<String, Object> data, IdRemapper idMap) {
        return switch (tableName) {
            case "disabledModules" -> {
                var moduleNames = (List<String>) data.getOrDefault("disabledModules", List.of());
                var modules =
                        moduleNames.stream().map(StationModule::valueOf).collect(java.util.stream.Collectors.toSet());
                stationRepository.setDisabledModules(stationId, modules);
                yield modules.size();
            }
            case "members" -> importMembers(stationId, data, idMap);
            case "memberRoles" -> importMemberRoles(stationId, data, idMap);
            case "groups" -> importGroups(stationId, data, idMap);
            case "groupMembers" -> importGroupMembers(data, idMap);
            case "tags" -> importTags(stationId, data, idMap);
            case "tagMembers" -> importTagMembers(data, idMap);
            case "managerRelations" -> importManagerRelations(data, idMap);
            case "profileFields" -> importProfileFields(stationId, data, idMap);
            case "profileFieldValues" -> importProfileFieldValues(data, idMap);
            case "eventCategories" -> importEventCategories(stationId, data, idMap);
            case "attendanceTemplates" -> importAttendanceTemplates(stationId, data, idMap);
            case "attendanceTemplateFields" -> importAttendanceTemplateFields(data, idMap);
            case "events" -> importEvents(stationId, data, idMap);
            case "inventories" -> importInventories(stationId, data, idMap);
            case "inventorySizes" -> importInventorySizes(data, idMap);
            case "inventoryItems" -> importInventoryItems(data, idMap);
            case "forms" -> importForms(stationId, data, idMap);
            case "formQuestions" -> importFormQuestions(data, idMap);
            case "logo" -> importLogo(stationId, data);
            case "kbFolders" -> importKbFolders(stationId, data, idMap);
            case "kbFiles" -> importKbFiles(stationId, data, idMap);
            case "kbFileContent" -> importKbFileContent(data, idMap);
            case "kbFileVersions" -> importKbFileVersions(data, idMap);
            default -> {
                log.warn("Unknown table for import: {}", tableName);
                yield 0;
            }
        };
    }

    private int insertReturningId(String sql, Call call) {
        return Query.query(sql)
                .single(call)
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    // -- Per-table import methods --

    @SuppressWarnings("unchecked")
    private int importMembers(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var members = (List<Map<String, Object>>) data.getOrDefault("members", List.of());
        for (var member : members) {
            int oldId = intVal(member, "id");
            String displayName = str(member, "display_name", "");
            boolean former = boolVal(member, "former");
            String email = str(member, "account_email", null);

            Integer accountId = null;
            if (email != null && !email.isBlank()) {
                // Look up existing account by email — if found, link to it (don't update password)
                var existing = accountRepository.findByEmail(email);
                if (existing.isPresent()) {
                    accountId = existing.get().id();
                } else {
                    // Create a new account (email-verified, no password — they'll need to reset)
                    var newAccount = accountRepository.create(
                            email, str(member, "account_first_name", ""), str(member, "account_last_name", ""), true);
                    accountId = newAccount.id();
                }
            }

            int newId;
            if (accountId != null) {
                // Check if this account already has a membership at this station
                var existingMember = stationMemberRepository.findByStationAndAccount(stationId, accountId);
                if (existingMember.isPresent()) {
                    newId = existingMember.get().id();
                } else {
                    newId = insertReturningId(
                            "INSERT INTO station_member(station_id, account_id, display_name, former) VALUES(:station_id, :account_id, :display_name, :former) RETURNING id;",
                            Call.of()
                                    .bind("station_id", stationId)
                                    .bind("account_id", accountId)
                                    .bind("display_name", displayName)
                                    .bind("former", former));
                }
            } else {
                newId = insertReturningId(
                        "INSERT INTO station_member(station_id, display_name, former) VALUES(:station_id, :display_name, :former) RETURNING id;",
                        Call.of()
                                .bind("station_id", stationId)
                                .bind("display_name", displayName)
                                .bind("former", former));
            }
            idMap.put("member", oldId, newId);
        }
        return members.size();
    }

    @SuppressWarnings("unchecked")
    private int importMemberRoles(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var roleNameToId = new HashMap<String, Integer>();
        for (var role : stationMemberRepository.findAllRoles())
            roleNameToId.put(role.role().name(), role.id());

        var memberRoles = (List<Map<String, Object>>) data.getOrDefault("memberRoles", List.of());
        int count = 0;
        Integer firstManagerMemberId = null;
        for (var mr : memberRoles) {
            int memberId = idMap.get("member", intVal(mr, "member_id"));
            String roleName = str(mr, "role_name", "");
            Integer roleId = roleNameToId.get(roleName);
            if (memberId > 0 && roleId != null) {
                stationMemberRepository.addRole(memberId, roleId);
                if ("MANAGER".equals(roleName) && firstManagerMemberId == null) {
                    firstManagerMemberId = memberId;
                }
                count++;
            }
        }
        // Set station owner to the first imported manager if no owner exists
        if (firstManagerMemberId != null) {
            var station = stationRepository.findById(stationId).orElse(null);
            if (station != null && station.ownerMemberId() == null) {
                stationRepository.setOwner(stationId, firstManagerMemberId);
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importGroups(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var groups = (List<Map<String, Object>>) data.getOrDefault("groups", List.of());
        for (var group : groups) {
            int oldId = intVal(group, "id");
            int newId = insertReturningId(
                    "INSERT INTO member_group(station_id, name) VALUES(:station_id, :name) RETURNING id;",
                    Call.of().bind("station_id", stationId).bind("name", str(group, "name", "")));
            idMap.put("group", oldId, newId);
        }
        return groups.size();
    }

    @SuppressWarnings("unchecked")
    private int importGroupMembers(Map<String, Object> data, IdRemapper idMap) {
        var groupMembers = (List<Map<String, Object>>) data.getOrDefault("groupMembers", List.of());
        int count = 0;
        for (var gm : groupMembers) {
            int groupId = idMap.get("group", intVal(gm, "group_id"));
            int memberId = idMap.get("member", intVal(gm, "member_id"));
            if (groupId > 0 && memberId > 0) {
                Query.query(
                                "INSERT INTO member_group_entry(group_id, member_id) VALUES(:group_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(Call.of().bind("group_id", groupId).bind("member_id", memberId))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importTags(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var tags = (List<Map<String, Object>>) data.getOrDefault("tags", List.of());
        for (var tag : tags) {
            int oldId = intVal(tag, "id");
            int newId = insertReturningId(
                    "INSERT INTO user_tag(station_id, name) VALUES(:station_id, :name) RETURNING id;",
                    Call.of().bind("station_id", stationId).bind("name", str(tag, "name", "")));
            idMap.put("tag", oldId, newId);
        }
        return tags.size();
    }

    @SuppressWarnings("unchecked")
    private int importTagMembers(Map<String, Object> data, IdRemapper idMap) {
        var tagMembers = (List<Map<String, Object>>) data.getOrDefault("tagMembers", List.of());
        int count = 0;
        for (var tm : tagMembers) {
            int tagId = idMap.get("tag", intVal(tm, "tag_id"));
            int memberId = idMap.get("member", intVal(tm, "member_id"));
            if (tagId > 0 && memberId > 0) {
                Query.query(
                                "INSERT INTO user_tag_entry(tag_id, member_id) VALUES(:tag_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(Call.of().bind("tag_id", tagId).bind("member_id", memberId))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importManagerRelations(Map<String, Object> data, IdRemapper idMap) {
        var relations = (List<Map<String, Object>>) data.getOrDefault("managerRelations", List.of());
        int count = 0;
        for (var rel : relations) {
            int managerId = idMap.get("member", intVal(rel, "manager_id"));
            int managedId = idMap.get("member", intVal(rel, "managed_id"));
            if (managerId > 0 && managedId > 0) {
                stationMemberRepository.addManager(managerId, managedId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importProfileFields(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("profileFields", List.of());
        for (var pf : fields) {
            int oldId = intVal(pf, "id");
            int newId = insertReturningId(
                    "INSERT INTO profile_field(station_id, name, field_type, config, position, scope, keep_on_archive) VALUES(:station_id, :name, :field_type, :config::jsonb, :position, :scope, :keep) RETURNING id;",
                    Call.of()
                            .bind("station_id", stationId)
                            .bind("name", str(pf, "name", ""))
                            .bind("field_type", str(pf, "field_type", "text"))
                            .bind("config", str(pf, "config", "{}"))
                            .bind("position", intVal(pf, "position"))
                            .bind("scope", str(pf, "scope", "MEMBER"))
                            .bind("keep", boolVal(pf, "keep_on_archive")));
            idMap.put("profileField", oldId, newId);
        }
        return fields.size();
    }

    @SuppressWarnings("unchecked")
    private int importProfileFieldValues(Map<String, Object> data, IdRemapper idMap) {
        var values = (List<Map<String, Object>>) data.getOrDefault("profileFieldValues", List.of());
        int count = 0;
        for (var pfv : values) {
            int memberId = idMap.get("member", intVal(pfv, "member_id"));
            int fieldId = idMap.get("profileField", intVal(pfv, "field_id"));
            if (memberId > 0 && fieldId > 0) {
                Query.query(
                                "INSERT INTO profile_field_value(member_id, field_id, value) VALUES(:member_id, :field_id, :value::JSONB) ON CONFLICT DO NOTHING;")
                        .single(Call.of()
                                .bind("member_id", memberId)
                                .bind("field_id", fieldId)
                                .bind("value", str(pfv, "value", "{}")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEventCategories(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var categories = (List<Map<String, Object>>) data.getOrDefault("eventCategories", List.of());
        for (var cat : categories) {
            int oldId = intVal(cat, "id");
            int newId = insertReturningId(
                    "INSERT INTO event_category(station_id, name, position) VALUES(:station_id, :name, :position) RETURNING id;",
                    Call.of()
                            .bind("station_id", stationId)
                            .bind("name", str(cat, "name", ""))
                            .bind("position", intVal(cat, "position")));
            idMap.put("eventCategory", oldId, newId);
        }
        return categories.size();
    }

    @SuppressWarnings("unchecked")
    private int importAttendanceTemplates(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var templates = (List<Map<String, Object>>) data.getOrDefault("attendanceTemplates", List.of());
        for (var tmpl : templates) {
            int oldId = intVal(tmpl, "id");
            int newId = insertReturningId(
                    "INSERT INTO attendance_template(station_id, name) VALUES(:station_id, :name) RETURNING id;",
                    Call.of().bind("station_id", stationId).bind("name", str(tmpl, "name", "")));
            idMap.put("attendanceTemplate", oldId, newId);
        }
        return templates.size();
    }

    @SuppressWarnings("unchecked")
    private int importAttendanceTemplateFields(Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("attendanceTemplateFields", List.of());
        int count = 0;
        for (var field : fields) {
            int oldId = intVal(field, "id");
            int templateId = idMap.get("attendanceTemplate", intVal(field, "template_id"));
            if (templateId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO attendance_template_field(template_id, name, field_type, config, position) VALUES(:template_id, :name, :field_type, :config::jsonb, :position) RETURNING id;",
                        Call.of()
                                .bind("template_id", templateId)
                                .bind("name", str(field, "name", ""))
                                .bind("field_type", str(field, "field_type", ""))
                                .bind("config", str(field, "config", "{}"))
                                .bind("position", intVal(field, "position")));
                idMap.put("attendanceTemplateField", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEvents(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var events = (List<Map<String, Object>>) data.getOrDefault("events", List.of());
        for (var event : events) {
            int oldId = intVal(event, "id");
            Integer templateId = event.get("template_id") != null
                    ? idMap.get("attendanceTemplate", intVal(event, "template_id"))
                    : null;
            Integer categoryId =
                    event.get("category_id") != null ? idMap.get("eventCategory", intVal(event, "category_id")) : null;
            int newId = insertReturningId(
                    "INSERT INTO station_event(station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id) VALUES(:station_id, :name, :desc, :event_type, :dow, :start::timestamptz, :end::timestamptz, :tmpl, :reg, :deadline::timestamp, :confirm, :cat) RETURNING id;",
                    Call.of()
                            .bind("station_id", stationId)
                            .bind("name", str(event, "name", ""))
                            .bind("desc", str(event, "description", null))
                            .bind("event_type", str(event, "event_type", "ONE_TIME"))
                            .bind("dow", event.get("day_of_week") instanceof Number n ? n.intValue() : null)
                            .bind("start", str(event, "start_time", null))
                            .bind("end", str(event, "end_time", null))
                            .bind("tmpl", templateId != null && templateId > 0 ? templateId : null)
                            .bind("reg", boolVal(event, "requires_registration"))
                            .bind("deadline", str(event, "registration_deadline", null))
                            .bind("confirm", boolVal(event, "requires_confirmation"))
                            .bind("cat", categoryId != null && categoryId > 0 ? categoryId : null));
            idMap.put("event", oldId, newId);
        }
        return events.size();
    }

    @SuppressWarnings("unchecked")
    private int importInventories(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var inventories = (List<Map<String, Object>>) data.getOrDefault("inventories", List.of());
        for (var inv : inventories) {
            int oldId = intVal(inv, "id");
            int newId = insertReturningId(
                    "INSERT INTO inventory(station_id, name, inventory_type, has_sizes) VALUES(:station_id, :name, :type, :has_sizes) RETURNING id;",
                    Call.of()
                            .bind("station_id", stationId)
                            .bind("name", str(inv, "name", ""))
                            .bind("type", str(inv, "inventory_type", "INTERNAL"))
                            .bind("has_sizes", boolVal(inv, "has_sizes")));
            idMap.put("inventory", oldId, newId);
        }
        return inventories.size();
    }

    @SuppressWarnings("unchecked")
    private int importInventorySizes(Map<String, Object> data, IdRemapper idMap) {
        var sizes = (List<Map<String, Object>>) data.getOrDefault("inventorySizes", List.of());
        int count = 0;
        for (var size : sizes) {
            int oldId = intVal(size, "id");
            int inventoryId = idMap.get("inventory", intVal(size, "inventory_id"));
            if (inventoryId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO inventory_size(inventory_id, label, position, note) VALUES(:inv, :label, :pos, :note) RETURNING id;",
                        Call.of()
                                .bind("inv", inventoryId)
                                .bind("label", str(size, "label", ""))
                                .bind("pos", intVal(size, "position"))
                                .bind("note", str(size, "note", "")));
                idMap.put("inventorySize", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importInventoryItems(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("inventoryItems", List.of());
        int count = 0;
        for (var item : items) {
            int inventoryId = idMap.get("inventory", intVal(item, "inventory_id"));
            Integer sizeId = item.get("size_id") != null ? idMap.get("inventorySize", intVal(item, "size_id")) : null;
            Integer assignedTo =
                    item.get("assigned_to") != null ? idMap.get("member", intVal(item, "assigned_to")) : null;
            if (inventoryId > 0) {
                Query.query(
                                "INSERT INTO inventory_item(inventory_id, internal_id, name, size_id, metadata, assigned_to, item_source) VALUES(:inv, :iid, :name, :sid, :meta::JSONB, :at, :src);")
                        .single(Call.of()
                                .bind("inv", inventoryId)
                                .bind("iid", str(item, "internal_id", null))
                                .bind("name", str(item, "name", ""))
                                .bind("sid", sizeId != null && sizeId > 0 ? sizeId : null)
                                .bind("meta", str(item, "metadata", "{}"))
                                .bind("at", assignedTo != null && assignedTo > 0 ? assignedTo : null)
                                .bind("src", str(item, "item_source", null)))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importForms(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var forms = (List<Map<String, Object>>) data.getOrDefault("forms", List.of());
        for (var form : forms) {
            int oldId = intVal(form, "id");
            int createdBy = idMap.get("member", intVal(form, "created_by"));
            if (createdBy <= 0 && !idMap.maps.getOrDefault("member", Map.of()).isEmpty()) {
                createdBy = idMap.maps.get("member").values().iterator().next();
            }
            int newId = insertReturningId(
                    "INSERT INTO form(station_id, title, description, status, shuffle_questions, allow_edit, created_by) VALUES(:sid, :title, :desc, :status, :shuffle, :edit, :by) RETURNING id;",
                    Call.of()
                            .bind("sid", stationId)
                            .bind("title", str(form, "title", ""))
                            .bind("desc", str(form, "description", ""))
                            .bind("status", str(form, "status", "DRAFT"))
                            .bind("shuffle", boolVal(form, "shuffle_questions"))
                            .bind("edit", boolVal(form, "allow_edit"))
                            .bind("by", createdBy));
            idMap.put("form", oldId, newId);
        }
        return forms.size();
    }

    @SuppressWarnings("unchecked")
    private int importFormQuestions(Map<String, Object> data, IdRemapper idMap) {
        var questions = (List<Map<String, Object>>) data.getOrDefault("formQuestions", List.of());
        int count = 0;
        for (var q : questions) {
            int formId = idMap.get("form", intVal(q, "form_id"));
            if (formId > 0) {
                Query.query(
                                "INSERT INTO form_question(form_id, position, question_type, title, description, required, shuffle, config) VALUES(:fid, :pos, :qt, :title, :desc, :req, :shuf, :cfg::JSONB);")
                        .single(Call.of()
                                .bind("fid", formId)
                                .bind("pos", intVal(q, "position"))
                                .bind("qt", str(q, "question_type", "TEXT"))
                                .bind("title", str(q, "title", ""))
                                .bind("desc", str(q, "description", ""))
                                .bind("req", boolVal(q, "required"))
                                .bind("shuf", boolVal(q, "shuffle"))
                                .bind("cfg", str(q, "config", "{}")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- Inner classes --

    /**
     * Maps old entity IDs from the source station to new IDs in the target station during import.
     */
    public static class IdRemapper {
        final Map<String, Map<Integer, Integer>> maps = new HashMap<>();

        void put(String type, int oldId, int newId) {
            maps.computeIfAbsent(type, _ -> new HashMap<>()).put(oldId, newId);
        }

        int get(String type, int oldId) {
            return maps.getOrDefault(type, Map.of()).getOrDefault(oldId, 0);
        }
    }

    /**
     * Tracks the progress of an asynchronous station import, using volatile fields for thread safety.
     */
    public static class ImportProgress {
        private final int stationId;
        private final String stationName;
        private final int totalTables;

        private volatile String status = "IN_PROGRESS";
        private volatile int currentTableIndex;
        private volatile String currentTable;
        private volatile int completedTables;
        private volatile String error;

        public ImportProgress(int stationId, String stationName, int totalTables) {
            this.stationId = stationId;
            this.stationName = stationName;
            this.totalTables = totalTables;
        }

        public int stationId() {
            return stationId;
        }

        public String stationName() {
            return stationName;
        }

        public String status() {
            return status;
        }

        public int totalTables() {
            return totalTables;
        }

        public int completedTables() {
            return completedTables;
        }

        public String currentTable() {
            return currentTable;
        }

        public String error() {
            return error;
        }

        void startTable(int index, String table) {
            this.currentTableIndex = index;
            this.currentTable = table;
        }

        void completeTable() {
            this.completedTables++;
        }

        void complete() {
            this.status = "COMPLETED";
            this.currentTable = null;
        }

        void fail(String error) {
            this.status = "FAILED";
            this.error = error;
        }
    }

    // -- KB + Logo import methods --

    @SuppressWarnings("unchecked")
    private int importLogo(int stationId, Map<String, Object> data) {
        var logo = (Map<String, Object>) data.get("logo");
        if (logo == null) return 0;
        String b64 = (String) logo.get("data");
        String contentType = (String) logo.get("contentType");
        if (b64 != null && contentType != null) {
            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
            stationRepository.updateLogo(stationId, bytes, contentType);
            return 1;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private int importKbFolders(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var folders = (List<Map<String, Object>>) data.getOrDefault("kbFolders", List.of());
        for (var folder : folders) {
            int oldId = intVal(folder, "id");
            Integer oldParentId = folder.get("parent_id") != null ? intVal(folder, "parent_id") : null;
            Integer parentId = oldParentId != null ? idMap.get("kbFolder", oldParentId) : null;
            int newId = insertReturningId(
                    "INSERT INTO kb_folder(station_id, parent_id, name, description, position, restricted, restriction_mode) VALUES(:station_id, :parent_id, :name, :description, :position, :restricted, :restriction_mode) RETURNING id;",
                    Call.of()
                            .bind("station_id", stationId)
                            .bind("parent_id", parentId)
                            .bind("name", str(folder, "name", ""))
                            .bind("description", str(folder, "description", ""))
                            .bind("position", intVal(folder, "position"))
                            .bind("restricted", boolVal(folder, "restricted"))
                            .bind("restriction_mode", str(folder, "restriction_mode", "AND")));
            idMap.put("kbFolder", oldId, newId);
        }
        return folders.size();
    }

    @SuppressWarnings("unchecked")
    private int importKbFiles(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var files = (List<Map<String, Object>>) data.getOrDefault("kbFiles", List.of());
        for (var file : files) {
            int oldId = intVal(file, "id");
            Integer oldFolderId = file.get("folder_id") != null ? intVal(file, "folder_id") : null;
            Integer folderId = oldFolderId != null ? idMap.get("kbFolder", oldFolderId) : null;
            int newId = insertReturningId(
                    "INSERT INTO kb_file(station_id, folder_id, name, description, file_type, position, restricted, restriction_mode) VALUES(:station_id, :folder_id, :name, :description, :file_type, :position, :restricted, :restriction_mode) RETURNING id;",
                    Call.of()
                            .bind("station_id", stationId)
                            .bind("folder_id", folderId)
                            .bind("name", str(file, "name", ""))
                            .bind("description", str(file, "description", ""))
                            .bind("file_type", str(file, "file_type", "MARKDOWN"))
                            .bind("position", intVal(file, "position"))
                            .bind("restricted", boolVal(file, "restricted"))
                            .bind("restriction_mode", str(file, "restriction_mode", "AND")));
            idMap.put("kbFile", oldId, newId);
        }
        return files.size();
    }

    @SuppressWarnings("unchecked")
    private int importKbFileContent(Map<String, Object> data, IdRemapper idMap) {
        var contents = (List<Map<String, Object>>) data.getOrDefault("kbFileContent", List.of());
        for (var content : contents) {
            int oldFileId = intVal(content, "file_id");
            Integer newFileId = idMap.get("kbFile", oldFileId);
            if (newFileId == null) continue;
            String text = (String) content.get("text_content");
            Query.query(
                            "INSERT INTO kb_file_content(file_id, text_content) VALUES(:file_id, :text_content) ON CONFLICT(file_id) DO UPDATE SET text_content = :text_content;")
                    .single(Call.of().bind("file_id", newFileId).bind("text_content", text))
                    .insert();
        }
        return contents.size();
    }

    @SuppressWarnings("unchecked")
    private int importKbFileVersions(Map<String, Object> data, IdRemapper idMap) {
        var versions = (List<Map<String, Object>>) data.getOrDefault("kbFileVersions", List.of());
        for (var version : versions) {
            int oldFileId = intVal(version, "file_id");
            Integer newFileId = idMap.get("kbFile", oldFileId);
            if (newFileId == null) continue;
            Query.query(
                            "INSERT INTO kb_file_version(file_id, patch, is_full, version, created_at) VALUES(:file_id, :patch, :is_full, :version, now());")
                    .single(Call.of()
                            .bind("file_id", newFileId)
                            .bind("patch", (String) version.get("patch"))
                            .bind("is_full", boolVal(version, "is_full"))
                            .bind("version", intVal(version, "version")))
                    .insert();
        }
        return versions.size();
    }

    /**
     * Result of a station import containing the new station ID, name, and total imported entities.
     */
    public record ImportResult(int stationId, String stationName, int totalEntities) {}
}
