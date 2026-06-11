/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.api.roles.StationUserType;
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

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

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
            "memberUserTypes",
            "memberPermissions",
            "groups",
            "groupMembers",
            "tags",
            "tagMembers",
            "managerRelations",
            "memberAbsences",
            "profileFields",
            "profileFieldValues",
            "eventCategories",
            "attendanceTemplates",
            "attendanceTemplateFields",
            "attendanceTemplateGroups",
            "attendanceSessions",
            "attendanceSessionFields",
            "attendanceEntries",
            "attendanceReportPresets",
            "events",
            "eventRegistrations",
            "eventComments",
            "eventFields",
            "eventTemplates",
            "eventBreaks",
            "inventories",
            "inventorySizes",
            "inventoryItems",
            "forms",
            "formQuestions",
            "kbFolders",
            "kbFiles",
            "kbFileContent",
            "kbFileVersions",
            "news",
            "newsComments",
            "boards",
            "boardLanes",
            "boardFields",
            "boardLabels",
            "boardTickets",
            "boardTicketComments",
            "boardTicketLabels",
            "boardTicketChecklist",
            "boardTicketLinks",
            "boardTicketWeblinks",
            "boardViewAccess",
            "boardEditAccess",
            "lostAndFound",
            "waitingLists",
            "waitingListFields",
            "waitingListEntries",
            "entityNotes",
            "entityNoteVersions",
            "equipmentExchangeRequests",
            "equipmentExchangeLogs",
            "equipmentProcurements",
            "formResponses",
            "formAnswers",
            "formRestrictions",
            "eventRestrictions",
            "eventLayouts",
            "eventLayoutFields",
            "eventTemplateFields",
            "eventTemplateRestrictions",
            "kbTags",
            "kbFileTags",
            "kbFolderTags",
            "kbAccessRestrictions",
            "kbComments",
            "inventoryChecks",
            "inventoryCheckItems",
            "inventoryItemHistory",
            "inventoryRequirements",
            "newsRestrictions",
            "userSettings",
            "userNotificationSettings");

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
            case "memberUserTypes" -> importMemberUserTypes(stationId, data, idMap);
            case "memberPermissions" -> importMemberPermissions(stationId, data, idMap);
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
            case "attendanceTemplateGroups" -> importAttendanceTemplateGroups(data, idMap);
            case "attendanceSessions" -> importAttendanceSessions(data, idMap);
            case "attendanceSessionFields" -> importAttendanceSessionFields(data, idMap);
            case "attendanceEntries" -> importAttendanceEntries(data, idMap);
            case "attendanceReportPresets" -> importAttendanceReportPresets(stationId, data, idMap);
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
            case "memberAbsences" -> importMemberAbsences(data, idMap);
            case "eventRegistrations" -> importEventRegistrations(data, idMap);
            case "eventComments" -> importEventComments(data, idMap);
            case "eventFields" -> importEventFields(data, idMap);
            case "eventTemplates" -> importEventTemplates(stationId, data, idMap);
            case "eventBreaks" -> importEventBreaks(stationId, data);
            case "news" -> importNews(stationId, data, idMap);
            case "newsComments" -> importNewsComments(data, idMap);
            case "boards" -> importBoards(stationId, data, idMap);
            case "boardLanes" -> importBoardLanes(data, idMap);
            case "boardFields" -> importBoardFields(data, idMap);
            case "boardLabels" -> importBoardLabels(data, idMap);
            case "boardTickets" -> importBoardTickets(data, idMap);
            case "boardTicketComments" -> importBoardTicketComments(data, idMap);
            case "boardTicketLabels" -> importBoardTicketLabels(data, idMap);
            case "boardTicketChecklist" -> importBoardTicketChecklist(data, idMap);
            case "boardTicketLinks" -> importBoardTicketLinks(data, idMap);
            case "boardTicketWeblinks" -> importBoardTicketWeblinks(data, idMap);
            case "boardViewAccess" -> importBoardViewAccess(data, idMap);
            case "boardEditAccess" -> importBoardEditAccess(data, idMap);
            case "lostAndFound" -> importLostAndFound(stationId, data, idMap);
            case "waitingLists" -> importWaitingLists(stationId, data, idMap);
            case "waitingListFields" -> importWaitingListFields(data, idMap);
            case "waitingListEntries" -> importWaitingListEntries(data, idMap);
            case "entityNotes" -> importEntityNotes(stationId, data, idMap);
            case "entityNoteVersions" -> importEntityNoteVersions(data, idMap);
            case "equipmentExchangeRequests" -> importEquipmentExchangeRequests(stationId, data, idMap);
            case "equipmentExchangeLogs" -> importEquipmentExchangeLogs(data, idMap);
            case "equipmentProcurements" -> importEquipmentProcurements(stationId, data, idMap);
            case "formResponses" -> importFormResponses(data, idMap);
            case "formAnswers" -> importFormAnswers(data, idMap);
            case "formRestrictions" -> importFormRestrictions(data, idMap);
            case "eventRestrictions" -> importEventRestrictions(data, idMap);
            case "eventLayouts" -> importEventLayouts(stationId, data, idMap);
            case "eventLayoutFields" -> importEventLayoutFields(data, idMap);
            case "eventTemplateFields" -> importEventTemplateFields(data, idMap);
            case "eventTemplateRestrictions" -> importEventTemplateRestrictions(data, idMap);
            case "kbTags" -> importKbTags(stationId, data, idMap);
            case "kbFileTags" -> importKbFileTags(data, idMap);
            case "kbFolderTags" -> importKbFolderTags(data, idMap);
            case "kbAccessRestrictions" -> importKbAccessRestrictions(data, idMap);
            case "kbComments" -> importKbComments(data, idMap);
            case "inventoryChecks" -> importInventoryChecks(stationId, data, idMap);
            case "inventoryCheckItems" -> importInventoryCheckItems(data, idMap);
            case "inventoryItemHistory" -> importInventoryItemHistory(data, idMap);
            case "inventoryRequirements" -> importInventoryRequirements(data, idMap);
            case "newsRestrictions" -> importNewsRestrictions(data, idMap);
            case "userSettings" -> importUserSettings(data, idMap);
            case "userNotificationSettings" -> importUserNotificationSettings(data, idMap);
            default -> {
                log.warn("Unknown table for import: {}", tableName);
                yield 0;
            }
        };
    }

    private int insertReturningId(String sql, Call call) {
        return query(sql).single(call).map(row -> row.getInt("id")).first().orElseThrow();
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
                            call().bind("station_id", stationId)
                                    .bind("account_id", accountId)
                                    .bind("display_name", displayName)
                                    .bind("former", former));
                }
            } else {
                newId = insertReturningId(
                        "INSERT INTO station_member(station_id, display_name, former) VALUES(:station_id, :display_name, :former) RETURNING id;",
                        call().bind("station_id", stationId)
                                .bind("display_name", displayName)
                                .bind("former", former));
            }
            idMap.put("member", oldId, newId);
        }
        return members.size();
    }

    @SuppressWarnings("unchecked")
    private int importMemberUserTypes(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var entries = (List<Map<String, Object>>) data.getOrDefault("memberUserTypes", List.of());
        int count = 0;
        Integer firstManagerMemberId = null;
        for (var entry : entries) {
            int memberId = idMap.get("member", intVal(entry, "member_id"));
            String userTypeName = str(entry, "user_type", "MEMBER");
            if (memberId > 0) {
                try {
                    var userType = StationUserType.valueOf(userTypeName);
                    stationMemberRepository.setUserType(memberId, userType);
                    if (userType == StationUserType.MANAGER && firstManagerMemberId == null) {
                        firstManagerMemberId = memberId;
                    }
                    count++;
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown user type '{}' for member {}, skipping", userTypeName, memberId);
                }
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
    private int importMemberPermissions(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var permNameToId = new HashMap<String, Integer>();
        for (var perm : stationMemberRepository.findAllPermissions())
            permNameToId.put(perm.permission().name(), perm.id());

        var entries = (List<Map<String, Object>>) data.getOrDefault("memberPermissions", List.of());
        int count = 0;
        for (var entry : entries) {
            int memberId = idMap.get("member", intVal(entry, "member_id"));
            String permName = str(entry, "permission_name", "");
            Integer permId = permNameToId.get(permName);
            if (memberId > 0 && permId != null) {
                stationMemberRepository.grantPermission(memberId, permId);
                count++;
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
                    "INSERT INTO member_group(station_id, name, color, position) VALUES(:station_id, :name, :color, :position) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("name", str(group, "name", ""))
                            .bind("color", str(group, "color", null))
                            .bind("position", intVal(group, "position")));
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
                query(
                                "INSERT INTO member_group_entry(group_id, member_id) VALUES(:group_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("group_id", groupId).bind("member_id", memberId))
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
                    "INSERT INTO user_tag(station_id, name, color, visible, position) VALUES(:station_id, :name, :color, :visible, :position) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("name", str(tag, "name", ""))
                            .bind("color", str(tag, "color", null))
                            .bind("visible", boolVal(tag, "visible"))
                            .bind("position", intVal(tag, "position")));
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
                query(
                                "INSERT INTO user_tag_entry(tag_id, member_id) VALUES(:tag_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("tag_id", tagId).bind("member_id", memberId))
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
                    call().bind("station_id", stationId)
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
                query(
                                "INSERT INTO profile_field_value(member_id, field_id, value) VALUES(:member_id, :field_id, :value::JSONB) ON CONFLICT DO NOTHING;")
                        .single(call().bind("member_id", memberId)
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
                    "INSERT INTO event_category(station_id, name, position, public, max_shown_events) VALUES(:station_id, :name, :position, :public, :max_shown) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("name", str(cat, "name", ""))
                            .bind("position", intVal(cat, "position"))
                            .bind("public", boolVal(cat, "public"))
                            .bind(
                                    "max_shown",
                                    cat.get("max_shown_events") != null ? intVal(cat, "max_shown_events") : null));
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
                    call().bind("station_id", stationId).bind("name", str(tmpl, "name", "")));
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
                        call().bind("template_id", templateId)
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
    private int importAttendanceTemplateGroups(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("attendanceTemplateGroups", List.of());
        int count = 0;
        for (var item : items) {
            int templateId = idMap.get("attendanceTemplate", intVal(item, "template_id"));
            int groupId = idMap.get("group", intVal(item, "group_id"));
            if (templateId > 0 && groupId > 0) {
                query(
                                "INSERT INTO attendance_template_group(template_id, group_id) VALUES(:tid, :gid) ON CONFLICT DO NOTHING;")
                        .single(call().bind("tid", templateId).bind("gid", groupId))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importAttendanceSessions(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("attendanceSessions", List.of());
        int count = 0;
        for (var item : items) {
            int oldId = intVal(item, "id");
            int templateId = idMap.get("attendanceTemplate", intVal(item, "template_id"));
            Integer eventId = item.get("event_id") != null ? idMap.get("event", intVal(item, "event_id")) : null;
            if (templateId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO attendance_session(template_id, start_time, end_time, created_at, event_id, title) VALUES(:tid, :start::timestamp, :end::timestamp, :created::timestamp, :eid, :title) RETURNING id;",
                        call().bind("tid", templateId)
                                .bind("start", str(item, "start_time", ""))
                                .bind("end", str(item, "end_time", ""))
                                .bind("created", str(item, "created_at", ""))
                                .bind("eid", eventId)
                                .bind("title", str(item, "title", null)));
                idMap.put("attendanceSession", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importAttendanceSessionFields(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("attendanceSessionFields", List.of());
        int count = 0;
        for (var item : items) {
            int sessionId = idMap.get("attendanceSession", intVal(item, "session_id"));
            int fieldId = idMap.get("attendanceTemplateField", intVal(item, "field_id"));
            if (sessionId > 0 && fieldId > 0) {
                query(
                                "INSERT INTO attendance_session_field(session_id, field_id, value) VALUES(:sid, :fid, :value::JSONB) ON CONFLICT DO NOTHING;")
                        .single(call().bind("sid", sessionId)
                                .bind("fid", fieldId)
                                .bind("value", str(item, "value", "{}")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importAttendanceEntries(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("attendanceEntries", List.of());
        int count = 0;
        for (var item : items) {
            int sessionId = idMap.get("attendanceSession", intVal(item, "session_id"));
            int memberId = idMap.get("member", intVal(item, "member_id"));
            if (sessionId > 0 && memberId > 0) {
                query(
                                "INSERT INTO attendance_entry(session_id, member_id, check_in, check_out, status, source) VALUES(:sid, :mid, :cin::TIMESTAMP, :cout::TIMESTAMP, :status, :source) ON CONFLICT DO NOTHING;")
                        .single(call().bind("sid", sessionId)
                                .bind("mid", memberId)
                                .bind("cin", str(item, "check_in", null))
                                .bind("cout", str(item, "check_out", null))
                                .bind("status", str(item, "status", "PRESENT"))
                                .bind("source", str(item, "source", "EXPECTED")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importAttendanceReportPresets(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("attendanceReportPresets", List.of());
        int count = 0;
        for (var item : items) {
            int oldId = intVal(item, "id");
            Integer groupId = item.get("group_id") != null ? idMap.get("group", intVal(item, "group_id")) : null;
            int newId = insertReturningId(
                    "INSERT INTO attendance_report_preset(station_id, name, role_name, group_id, period, rounding) VALUES(:sid, :name, :role_name, :group_id, :period, :rounding) RETURNING id;",
                    call().bind("sid", stationId)
                            .bind("name", str(item, "name", ""))
                            .bind("role_name", str(item, "role_name", null))
                            .bind("group_id", groupId)
                            .bind("period", str(item, "period", "month"))
                            .bind("rounding", str(item, "rounding", "exact")));
            idMap.put("attendanceReportPreset", oldId, newId);
            count++;
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
                    "INSERT INTO station_event(station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id, restriction_mode, public, registration_limit, cancelled, cancelled_at, cancel_reason, min_registrations, threshold_date) VALUES(:station_id, :name, :desc, :event_type, :dow, :start::timestamptz, :end::timestamptz, :tmpl, :reg, :deadline::timestamp, :confirm, :cat, :restriction_mode, :public, :reg_limit, :cancelled, :cancelled_at::timestamptz, :cancel_reason, :min_reg, :threshold_date::timestamptz) RETURNING id;",
                    call().bind("station_id", stationId)
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
                            .bind("cat", categoryId != null && categoryId > 0 ? categoryId : null)
                            .bind("restriction_mode", str(event, "restriction_mode", "AND"))
                            .bind("public", event.get("public") != null ? boolVal(event, "public") : null)
                            .bind(
                                    "reg_limit",
                                    event.get("registration_limit") != null
                                            ? intVal(event, "registration_limit")
                                            : null)
                            .bind("cancelled", boolVal(event, "cancelled"))
                            .bind("cancelled_at", str(event, "cancelled_at", null))
                            .bind("cancel_reason", str(event, "cancel_reason", null))
                            .bind(
                                    "min_reg",
                                    event.get("min_registrations") != null ? intVal(event, "min_registrations") : null)
                            .bind("threshold_date", str(event, "threshold_date", null)));
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
                    call().bind("station_id", stationId)
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
                        call().bind("inv", inventoryId)
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
            int oldId = intVal(item, "id");
            int inventoryId = idMap.get("inventory", intVal(item, "inventory_id"));
            Integer sizeId = item.get("size_id") != null ? idMap.get("inventorySize", intVal(item, "size_id")) : null;
            Integer assignedTo =
                    item.get("assigned_to") != null ? idMap.get("member", intVal(item, "assigned_to")) : null;
            if (inventoryId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO inventory_item(inventory_id, internal_id, name, size_id, metadata, assigned_to, lost_at, item_source) VALUES(:inv, :iid, :name, :sid, :meta::JSONB, :at, :lost_at::timestamp, :src) RETURNING id;",
                        call().bind("inv", inventoryId)
                                .bind("iid", str(item, "internal_id", null))
                                .bind("name", str(item, "name", ""))
                                .bind("sid", sizeId != null && sizeId > 0 ? sizeId : null)
                                .bind("meta", str(item, "metadata", "{}"))
                                .bind("at", assignedTo != null && assignedTo > 0 ? assignedTo : null)
                                .bind("lost_at", str(item, "lost_at", null))
                                .bind("src", str(item, "item_source", null)));
                idMap.put("inventoryItem", oldId, newId);
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
                    "INSERT INTO form(station_id, title, description, status, shuffle_questions, allow_edit, start_at, end_at, closed_at, created_by, created_at, updated_at, restriction_mode, forced) VALUES(:sid, :title, :desc, :status, :shuffle, :edit, :start_at::timestamp, :end_at::timestamp, :closed_at::timestamp, :by, :created_at::timestamp, :updated_at::timestamp, :restriction_mode, :forced) RETURNING id;",
                    call().bind("sid", stationId)
                            .bind("title", str(form, "title", ""))
                            .bind("desc", str(form, "description", ""))
                            .bind("status", str(form, "status", "DRAFT"))
                            .bind("shuffle", boolVal(form, "shuffle_questions"))
                            .bind("edit", boolVal(form, "allow_edit"))
                            .bind("start_at", str(form, "start_at", null))
                            .bind("end_at", str(form, "end_at", null))
                            .bind("closed_at", str(form, "closed_at", null))
                            .bind("by", createdBy)
                            .bind("created_at", str(form, "created_at", null))
                            .bind("updated_at", str(form, "updated_at", null))
                            .bind("restriction_mode", str(form, "restriction_mode", "AND"))
                            .bind("forced", boolVal(form, "forced")));
            idMap.put("form", oldId, newId);
        }
        return forms.size();
    }

    @SuppressWarnings("unchecked")
    private int importFormQuestions(Map<String, Object> data, IdRemapper idMap) {
        var questions = (List<Map<String, Object>>) data.getOrDefault("formQuestions", List.of());
        int count = 0;
        for (var q : questions) {
            int oldId = intVal(q, "id");
            int formId = idMap.get("form", intVal(q, "form_id"));
            if (formId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO form_question(form_id, position, question_type, title, description, required, shuffle, config) VALUES(:fid, :pos, :qt, :title, :desc, :req, :shuf, :cfg::JSONB) RETURNING id;",
                        call().bind("fid", formId)
                                .bind("pos", intVal(q, "position"))
                                .bind("qt", str(q, "question_type", "TEXT"))
                                .bind("title", str(q, "title", ""))
                                .bind("desc", str(q, "description", ""))
                                .bind("req", boolVal(q, "required"))
                                .bind("shuf", boolVal(q, "shuffle"))
                                .bind("cfg", str(q, "config", "{}")));
                idMap.put("formQuestion", oldId, newId);
                count++;
            }
        }
        return count;
    }

    // -- Member absences --

    @SuppressWarnings("unchecked")
    private int importMemberAbsences(Map<String, Object> data, IdRemapper idMap) {
        var absences = (List<Map<String, Object>>) data.getOrDefault("memberAbsences", List.of());
        int count = 0;
        for (var ab : absences) {
            int memberId = idMap.get("member", intVal(ab, "member_id"));
            Integer createdBy = ab.get("created_by") != null ? idMap.get("member", intVal(ab, "created_by")) : null;
            if (memberId > 0) {
                query(
                                "INSERT INTO member_absence(member_id, absent_from, absent_until, reason, created_at, created_by) VALUES(:member_id, :absent_from::DATE, :absent_until::DATE, :reason, :created_at::TIMESTAMPTZ, :created_by) ON CONFLICT DO NOTHING;")
                        .single(call().bind("member_id", memberId)
                                .bind("absent_from", str(ab, "absent_from", null))
                                .bind("absent_until", str(ab, "absent_until", null))
                                .bind("reason", str(ab, "reason", null))
                                .bind("created_at", str(ab, "created_at", null))
                                .bind("created_by", createdBy != null && createdBy > 0 ? createdBy : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- Event extras --

    @SuppressWarnings("unchecked")
    private int importEventRegistrations(Map<String, Object> data, IdRemapper idMap) {
        var registrations = (List<Map<String, Object>>) data.getOrDefault("eventRegistrations", List.of());
        int count = 0;
        for (var reg : registrations) {
            int eventId = idMap.get("event", intVal(reg, "event_id"));
            int memberId = idMap.get("member", intVal(reg, "member_id"));
            Integer createdBy = reg.get("created_by") != null ? idMap.get("member", intVal(reg, "created_by")) : null;
            if (eventId > 0 && memberId > 0) {
                query(
                                "INSERT INTO event_registration(event_id, member_id, event_date, status, created_by, created_at) VALUES(:event_id, :member_id, :event_date::DATE, :status, :created_by, :created_at::TIMESTAMPTZ) ON CONFLICT DO NOTHING;")
                        .single(call().bind("event_id", eventId)
                                .bind("member_id", memberId)
                                .bind("event_date", str(reg, "event_date", null))
                                .bind("status", str(reg, "status", "REGISTERED"))
                                .bind("created_by", createdBy != null && createdBy > 0 ? createdBy : null)
                                .bind("created_at", str(reg, "created_at", null)))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEventComments(Map<String, Object> data, IdRemapper idMap) {
        var comments = (List<Map<String, Object>>) data.getOrDefault("eventComments", List.of());
        for (var c : comments) {
            int oldId = intVal(c, "id");
            int eventId = idMap.get("event", intVal(c, "event_id"));
            Integer oldParentId = c.get("parent_id") != null ? intVal(c, "parent_id") : null;
            Integer parentId = oldParentId != null ? idMap.get("eventComment", oldParentId) : null;
            if (eventId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO event_comment(event_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at) VALUES(:event_id, :parent_id, :author_station_uid::uuid, :author_member_uid::uuid, :content, :deleted, :created_at::timestamptz, :updated_at::timestamptz) RETURNING id;",
                        call().bind("event_id", eventId)
                                .bind("parent_id", parentId != null && parentId > 0 ? parentId : null)
                                .bind("author_station_uid", str(c, "author_station_uid", null))
                                .bind("author_member_uid", str(c, "author_member_uid", null))
                                .bind("content", str(c, "content", ""))
                                .bind("deleted", boolVal(c, "deleted"))
                                .bind("created_at", str(c, "created_at", null))
                                .bind("updated_at", str(c, "updated_at", null)));
                idMap.put("eventComment", oldId, newId);
            }
        }
        return comments.size();
    }

    @SuppressWarnings("unchecked")
    private int importEventFields(Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("eventFields", List.of());
        int count = 0;
        for (var f : fields) {
            int eventId = idMap.get("event", intVal(f, "event_id"));
            if (eventId > 0) {
                int oldId = intVal(f, "id");
                Integer attendanceFieldId = f.get("attendance_field_id") != null
                        ? idMap.get("attendanceTemplateField", intVal(f, "attendance_field_id"))
                        : null;
                int newId = insertReturningId(
                        "INSERT INTO event_field(event_id, name, value, position, field_type, config, overview, attendance_field_id, public) VALUES(:event_id, :name, :value, :position, :field_type, :config::jsonb, :overview, :attendance_field_id, :public) RETURNING id;",
                        call().bind("event_id", eventId)
                                .bind("name", str(f, "name", ""))
                                .bind("value", str(f, "value", ""))
                                .bind("position", intVal(f, "position"))
                                .bind("field_type", str(f, "field_type", "string"))
                                .bind("config", str(f, "config", "{}"))
                                .bind("overview", boolVal(f, "overview"))
                                .bind(
                                        "attendance_field_id",
                                        attendanceFieldId != null && attendanceFieldId > 0 ? attendanceFieldId : null)
                                .bind("public", boolVal(f, "public")));
                idMap.put("eventField", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEventTemplates(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var templates = (List<Map<String, Object>>) data.getOrDefault("eventTemplates", List.of());
        for (var tmpl : templates) {
            int oldId = intVal(tmpl, "id");
            Integer categoryId =
                    tmpl.get("category_id") != null ? idMap.get("eventCategory", intVal(tmpl, "category_id")) : null;
            Integer attendanceTemplateId = tmpl.get("attendance_template_id") != null
                    ? idMap.get("attendanceTemplate", intVal(tmpl, "attendance_template_id"))
                    : null;
            int newId = insertReturningId(
                    "INSERT INTO event_template(station_id, name, title, description, category_id, event_type, requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit) VALUES(:station_id, :name, :title, :desc, :cat, :event_type, :reg, :deadline_offset::interval, :confirm, :restriction_mode, :att_tmpl, :reg_limit) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("name", str(tmpl, "name", ""))
                            .bind("title", str(tmpl, "title", null))
                            .bind("desc", str(tmpl, "description", null))
                            .bind("cat", categoryId != null && categoryId > 0 ? categoryId : null)
                            .bind("event_type", str(tmpl, "event_type", null))
                            .bind(
                                    "reg",
                                    tmpl.get("requires_registration") != null
                                            ? boolVal(tmpl, "requires_registration")
                                            : null)
                            .bind("deadline_offset", str(tmpl, "registration_deadline_offset", null))
                            .bind(
                                    "confirm",
                                    tmpl.get("requires_confirmation") != null
                                            ? boolVal(tmpl, "requires_confirmation")
                                            : null)
                            .bind("restriction_mode", str(tmpl, "restriction_mode", null))
                            .bind(
                                    "att_tmpl",
                                    attendanceTemplateId != null && attendanceTemplateId > 0
                                            ? attendanceTemplateId
                                            : null)
                            .bind(
                                    "reg_limit",
                                    tmpl.get("registration_limit") != null
                                            ? intVal(tmpl, "registration_limit")
                                            : null));
            idMap.put("eventTemplate", oldId, newId);
        }
        return templates.size();
    }

    @SuppressWarnings("unchecked")
    private int importEventBreaks(int stationId, Map<String, Object> data) {
        var breaks = (List<Map<String, Object>>) data.getOrDefault("eventBreaks", List.of());
        for (var br : breaks) {
            query(
                            "INSERT INTO station_event_break(station_id, name, start_date, end_date) VALUES(:station_id, :name, :start_date::DATE, :end_date::DATE);")
                    .single(call().bind("station_id", stationId)
                            .bind("name", str(br, "name", ""))
                            .bind("start_date", str(br, "start_date", null))
                            .bind("end_date", str(br, "end_date", null)))
                    .insert();
        }
        return breaks.size();
    }

    // -- News --

    @SuppressWarnings("unchecked")
    private int importNews(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var newsList = (List<Map<String, Object>>) data.getOrDefault("news", List.of());
        for (var n : newsList) {
            int oldId = intVal(n, "id");
            int newId = insertReturningId(
                    "INSERT INTO news(station_id, title, content_markdown, content_html, author_station_uid, author_member_uid, published_at, created_at, restriction_mode) VALUES(:station_id, :title, :content_markdown, :content_html, :author_station_uid::uuid, :author_member_uid::uuid, :published_at::timestamptz, :created_at::timestamptz, :restriction_mode) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("title", str(n, "title", ""))
                            .bind("content_markdown", str(n, "content_markdown", ""))
                            .bind("content_html", str(n, "content_html", ""))
                            .bind("author_station_uid", str(n, "author_station_uid", null))
                            .bind("author_member_uid", str(n, "author_member_uid", null))
                            .bind("published_at", str(n, "published_at", null))
                            .bind("created_at", str(n, "created_at", null))
                            .bind("restriction_mode", str(n, "restriction_mode", "AND")));
            idMap.put("news", oldId, newId);
        }
        return newsList.size();
    }

    @SuppressWarnings("unchecked")
    private int importNewsComments(Map<String, Object> data, IdRemapper idMap) {
        var comments = (List<Map<String, Object>>) data.getOrDefault("newsComments", List.of());
        for (var c : comments) {
            int oldId = intVal(c, "id");
            int newsId = idMap.get("news", intVal(c, "news_id"));
            Integer oldParentId = c.get("parent_id") != null ? intVal(c, "parent_id") : null;
            Integer parentId = oldParentId != null ? idMap.get("newsComment", oldParentId) : null;
            if (newsId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO news_comment(news_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at) VALUES(:news_id, :parent_id, :author_station_uid::uuid, :author_member_uid::uuid, :content, :deleted, :created_at::timestamptz) RETURNING id;",
                        call().bind("news_id", newsId)
                                .bind("parent_id", parentId != null && parentId > 0 ? parentId : null)
                                .bind("author_station_uid", str(c, "author_station_uid", null))
                                .bind("author_member_uid", str(c, "author_member_uid", null))
                                .bind("content", str(c, "content", ""))
                                .bind("deleted", boolVal(c, "deleted"))
                                .bind("created_at", str(c, "created_at", null)));
                idMap.put("newsComment", oldId, newId);
            }
        }
        return comments.size();
    }

    // -- Boards --

    @SuppressWarnings("unchecked")
    private int importBoards(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var boards = (List<Map<String, Object>>) data.getOrDefault("boards", List.of());
        for (var b : boards) {
            int oldId = intVal(b, "id");
            int newId = insertReturningId(
                    "INSERT INTO board(station_id, uid, name, description, short_key, hide_done_after_days, ticket_counter, created_at) VALUES(:station_id, :uid::uuid, :name, :description, :short_key, :hide_done_after_days, :ticket_counter, :created_at::timestamptz) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind(
                                    "uid",
                                    str(b, "uid", java.util.UUID.randomUUID().toString()))
                            .bind("name", str(b, "name", ""))
                            .bind("description", str(b, "description", ""))
                            .bind("short_key", str(b, "short_key", ""))
                            .bind("hide_done_after_days", intVal(b, "hide_done_after_days"))
                            .bind("ticket_counter", intVal(b, "ticket_counter"))
                            .bind("created_at", str(b, "created_at", null)));
            idMap.put("board", oldId, newId);
        }
        return boards.size();
    }

    @SuppressWarnings("unchecked")
    private int importBoardLanes(Map<String, Object> data, IdRemapper idMap) {
        var lanes = (List<Map<String, Object>>) data.getOrDefault("boardLanes", List.of());
        for (var lane : lanes) {
            int oldId = intVal(lane, "id");
            int boardId = idMap.get("board", intVal(lane, "board_id"));
            if (boardId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO board_lane(board_id, name, color, position) VALUES(:board_id, :name, :color, :position) RETURNING id;",
                        call().bind("board_id", boardId)
                                .bind("name", str(lane, "name", ""))
                                .bind("color", str(lane, "color", null))
                                .bind("position", intVal(lane, "position")));
                idMap.put("boardLane", oldId, newId);

                // Update backlog_lane_id reference on board if this lane was the backlog
                // We check the original export data for backlog_lane_id matching
            }
        }
        // After all lanes are imported, update backlog_lane_id on boards
        var boards = (List<Map<String, Object>>) data.getOrDefault("boards", List.of());
        if (boards == null) {
            boards = List.of();
        }
        for (var b : boards) {
            if (b.get("backlog_lane_id") != null) {
                int boardId = idMap.get("board", intVal(b, "id"));
                int backlogLaneId = idMap.get("boardLane", intVal(b, "backlog_lane_id"));
                if (boardId > 0 && backlogLaneId > 0) {
                    query("UPDATE board SET backlog_lane_id = :lane_id WHERE id = :board_id;")
                            .single(call().bind("lane_id", backlogLaneId).bind("board_id", boardId))
                            .update();
                }
            }
        }
        return lanes.size();
    }

    @SuppressWarnings("unchecked")
    private int importBoardFields(Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("boardFields", List.of());
        int count = 0;
        for (var f : fields) {
            int boardId = idMap.get("board", intVal(f, "board_id"));
            if (boardId > 0) {
                int oldId = intVal(f, "id");
                int newId = insertReturningId(
                        "INSERT INTO board_field(board_id, name, field_type, config, position) VALUES(:board_id, :name, :field_type, :config::jsonb, :position) RETURNING id;",
                        call().bind("board_id", boardId)
                                .bind("name", str(f, "name", ""))
                                .bind("field_type", str(f, "field_type", "text"))
                                .bind("config", str(f, "config", "{}"))
                                .bind("position", intVal(f, "position")));
                idMap.put("boardField", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importBoardLabels(Map<String, Object> data, IdRemapper idMap) {
        var labels = (List<Map<String, Object>>) data.getOrDefault("boardLabels", List.of());
        for (var l : labels) {
            int oldId = intVal(l, "id");
            int boardId = idMap.get("board", intVal(l, "board_id"));
            if (boardId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO board_label(board_id, name, color) VALUES(:board_id, :name, :color) RETURNING id;",
                        call().bind("board_id", boardId)
                                .bind("name", str(l, "name", ""))
                                .bind("color", str(l, "color", null)));
                idMap.put("boardLabel", oldId, newId);
            }
        }
        return labels.size();
    }

    @SuppressWarnings("unchecked")
    private int importBoardTickets(Map<String, Object> data, IdRemapper idMap) {
        var tickets = (List<Map<String, Object>>) data.getOrDefault("boardTickets", List.of());
        for (var t : tickets) {
            int oldId = intVal(t, "id");
            int boardId = idMap.get("board", intVal(t, "board_id"));
            int laneId = idMap.get("boardLane", intVal(t, "lane_id"));
            if (boardId > 0 && laneId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO board_ticket(board_id, lane_id, ticket_number, title, description, assignee_station_uid, assignee_member_uid, priority, due_date, position, creator_station_uid, creator_member_uid, created_at, updated_at, lane_entered_at) VALUES(:board_id, :lane_id, :ticket_number, :title, :description, :assignee_station_uid::uuid, :assignee_member_uid::uuid, :priority, :due_date::date, :position, :creator_station_uid::uuid, :creator_member_uid::uuid, :created_at::timestamptz, :updated_at::timestamptz, :lane_entered_at::timestamptz) RETURNING id;",
                        call().bind("board_id", boardId)
                                .bind("lane_id", laneId)
                                .bind("ticket_number", intVal(t, "ticket_number"))
                                .bind("title", str(t, "title", ""))
                                .bind("description", str(t, "description", null))
                                .bind("assignee_station_uid", str(t, "assignee_station_uid", null))
                                .bind("assignee_member_uid", str(t, "assignee_member_uid", null))
                                .bind("priority", str(t, "priority", null))
                                .bind("due_date", str(t, "due_date", null))
                                .bind("position", intVal(t, "position"))
                                .bind("creator_station_uid", str(t, "creator_station_uid", null))
                                .bind("creator_member_uid", str(t, "creator_member_uid", null))
                                .bind("created_at", str(t, "created_at", null))
                                .bind("updated_at", str(t, "updated_at", null))
                                .bind("lane_entered_at", str(t, "lane_entered_at", null)));
                idMap.put("boardTicket", oldId, newId);
            }
        }
        return tickets.size();
    }

    @SuppressWarnings("unchecked")
    private int importBoardTicketComments(Map<String, Object> data, IdRemapper idMap) {
        var comments = (List<Map<String, Object>>) data.getOrDefault("boardTicketComments", List.of());
        for (var c : comments) {
            int oldId = intVal(c, "id");
            int ticketId = idMap.get("boardTicket", intVal(c, "ticket_id"));
            Integer oldParentId = c.get("parent_id") != null ? intVal(c, "parent_id") : null;
            Integer parentId = oldParentId != null ? idMap.get("boardTicketComment", oldParentId) : null;
            if (ticketId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO board_ticket_comment(ticket_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at) VALUES(:ticket_id, :parent_id, :author_station_uid::uuid, :author_member_uid::uuid, :content, :deleted, :created_at::timestamptz, :updated_at::timestamptz) RETURNING id;",
                        call().bind("ticket_id", ticketId)
                                .bind("parent_id", parentId != null && parentId > 0 ? parentId : null)
                                .bind("author_station_uid", str(c, "author_station_uid", null))
                                .bind("author_member_uid", str(c, "author_member_uid", null))
                                .bind("content", str(c, "content", ""))
                                .bind("deleted", boolVal(c, "deleted"))
                                .bind("created_at", str(c, "created_at", null))
                                .bind("updated_at", str(c, "updated_at", null)));
                idMap.put("boardTicketComment", oldId, newId);
            }
        }
        return comments.size();
    }

    @SuppressWarnings("unchecked")
    private int importBoardTicketLabels(Map<String, Object> data, IdRemapper idMap) {
        var labels = (List<Map<String, Object>>) data.getOrDefault("boardTicketLabels", List.of());
        int count = 0;
        for (var l : labels) {
            int ticketId = idMap.get("boardTicket", intVal(l, "ticket_id"));
            int labelId = idMap.get("boardLabel", intVal(l, "label_id"));
            if (ticketId > 0 && labelId > 0) {
                query(
                                "INSERT INTO board_ticket_label(ticket_id, label_id) VALUES(:ticket_id, :label_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("ticket_id", ticketId).bind("label_id", labelId))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importBoardTicketChecklist(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("boardTicketChecklist", List.of());
        int count = 0;
        for (var item : items) {
            int ticketId = idMap.get("boardTicket", intVal(item, "ticket_id"));
            if (ticketId > 0) {
                query(
                                "INSERT INTO board_ticket_checklist_item(ticket_id, title, checked, position) VALUES(:ticket_id, :title, :checked, :position);")
                        .single(call().bind("ticket_id", ticketId)
                                .bind("title", str(item, "title", ""))
                                .bind("checked", boolVal(item, "checked"))
                                .bind("position", intVal(item, "position")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importBoardTicketLinks(Map<String, Object> data, IdRemapper idMap) {
        var links = (List<Map<String, Object>>) data.getOrDefault("boardTicketLinks", List.of());
        int count = 0;
        for (var l : links) {
            int ticketId = idMap.get("boardTicket", intVal(l, "ticket_id"));
            int linkedTicketId = idMap.get("boardTicket", intVal(l, "linked_ticket_id"));
            if (ticketId > 0 && linkedTicketId > 0) {
                query(
                                "INSERT INTO board_ticket_link(ticket_id, linked_ticket_id, link_type) VALUES(:ticket_id, :linked_ticket_id, :link_type) ON CONFLICT DO NOTHING;")
                        .single(call().bind("ticket_id", ticketId)
                                .bind("linked_ticket_id", linkedTicketId)
                                .bind("link_type", str(l, "link_type", "RELATED")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importBoardTicketWeblinks(Map<String, Object> data, IdRemapper idMap) {
        var weblinks = (List<Map<String, Object>>) data.getOrDefault("boardTicketWeblinks", List.of());
        int count = 0;
        for (var w : weblinks) {
            int ticketId = idMap.get("boardTicket", intVal(w, "ticket_id"));
            if (ticketId > 0) {
                query(
                                "INSERT INTO board_ticket_weblink(ticket_id, url, title, position) VALUES(:ticket_id, :url, :title, :position);")
                        .single(call().bind("ticket_id", ticketId)
                                .bind("url", str(w, "url", ""))
                                .bind("title", str(w, "title", ""))
                                .bind("position", intVal(w, "position")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importBoardViewAccess(Map<String, Object> data, IdRemapper idMap) {
        var access = (List<Map<String, Object>>) data.getOrDefault("boardViewAccess", List.of());
        int count = 0;
        for (var a : access) {
            int boardId = idMap.get("board", intVal(a, "board_id"));
            if (boardId > 0) {
                Integer groupId = a.get("group_id") != null ? idMap.get("group", intVal(a, "group_id")) : null;
                Integer tagId = a.get("tag_id") != null ? idMap.get("tag", intVal(a, "tag_id")) : null;
                query(
                                "INSERT INTO board_view_access(board_id, user_type, group_id, tag_id) VALUES(:board_id, :user_type, :group_id, :tag_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("board_id", boardId)
                                .bind("user_type", str(a, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("tag_id", tagId != null && tagId > 0 ? tagId : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importBoardEditAccess(Map<String, Object> data, IdRemapper idMap) {
        var access = (List<Map<String, Object>>) data.getOrDefault("boardEditAccess", List.of());
        int count = 0;
        for (var a : access) {
            int boardId = idMap.get("board", intVal(a, "board_id"));
            if (boardId > 0) {
                Integer groupId = a.get("group_id") != null ? idMap.get("group", intVal(a, "group_id")) : null;
                Integer tagId = a.get("tag_id") != null ? idMap.get("tag", intVal(a, "tag_id")) : null;
                query(
                                "INSERT INTO board_edit_access(board_id, user_type, group_id, tag_id) VALUES(:board_id, :user_type, :group_id, :tag_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("board_id", boardId)
                                .bind("user_type", str(a, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("tag_id", tagId != null && tagId > 0 ? tagId : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- Lost & Found --

    @SuppressWarnings("unchecked")
    private int importLostAndFound(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("lostAndFound", List.of());
        for (var item : items) {
            int oldId = intVal(item, "id");
            Integer claimedBy = item.get("claimed_by") != null ? idMap.get("member", intVal(item, "claimed_by")) : null;
            Integer createdBy = item.get("created_by") != null ? idMap.get("member", intVal(item, "created_by")) : null;
            int newId = insertReturningId(
                    "INSERT INTO lost_and_found_item(station_id, description, found_at, claimed_by, claimed_at, created_by, created_at) VALUES(:station_id, :description, :found_at::date, :claimed_by, :claimed_at::timestamptz, :created_by, :created_at::timestamptz) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("description", str(item, "description", ""))
                            .bind("found_at", str(item, "found_at", null))
                            .bind("claimed_by", claimedBy != null && claimedBy > 0 ? claimedBy : null)
                            .bind("claimed_at", str(item, "claimed_at", null))
                            .bind("created_by", createdBy != null && createdBy > 0 ? createdBy : null)
                            .bind("created_at", str(item, "created_at", null)));
            idMap.put("lostAndFound", oldId, newId);
        }
        return items.size();
    }

    // -- Waiting Lists --

    @SuppressWarnings("unchecked")
    private int importWaitingLists(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var lists = (List<Map<String, Object>>) data.getOrDefault("waitingLists", List.of());
        for (var wl : lists) {
            int oldId = intVal(wl, "id");
            Integer testingGroupId =
                    wl.get("testing_group_id") != null ? idMap.get("group", intVal(wl, "testing_group_id")) : null;
            Integer joinGroupId =
                    wl.get("join_group_id") != null ? idMap.get("group", intVal(wl, "join_group_id")) : null;
            int newId = insertReturningId(
                    "INSERT INTO waiting_list(station_id, name, description, scoring_formula, confirm_interval_days, visible_fields, testing_group_id, join_group_id, join_user_type, attendance_threshold, created_at) VALUES(:station_id, :name, :description, :scoring_formula, :confirm_interval_days, :visible_fields::jsonb, :testing_group_id, :join_group_id, :join_user_type, :attendance_threshold, :created_at::timestamp) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("name", str(wl, "name", ""))
                            .bind("description", str(wl, "description", ""))
                            .bind("scoring_formula", str(wl, "scoring_formula", null))
                            .bind("confirm_interval_days", intVal(wl, "confirm_interval_days"))
                            .bind("visible_fields", str(wl, "visible_fields", "[]"))
                            .bind(
                                    "testing_group_id",
                                    testingGroupId != null && testingGroupId > 0 ? testingGroupId : null)
                            .bind("join_group_id", joinGroupId != null && joinGroupId > 0 ? joinGroupId : null)
                            .bind("join_user_type", str(wl, "join_user_type", "MEMBER"))
                            .bind("attendance_threshold", intVal(wl, "attendance_threshold"))
                            .bind("created_at", str(wl, "created_at", null)));
            idMap.put("waitingList", oldId, newId);
        }
        return lists.size();
    }

    @SuppressWarnings("unchecked")
    private int importWaitingListFields(Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("waitingListFields", List.of());
        int count = 0;
        for (var f : fields) {
            int listId = idMap.get("waitingList", intVal(f, "list_id"));
            if (listId > 0) {
                int oldId = intVal(f, "id");
                int newId = insertReturningId(
                        "INSERT INTO waiting_list_field(list_id, name, field_type, config, required, position) VALUES(:list_id, :name, :field_type, :config::jsonb, :required, :position) RETURNING id;",
                        call().bind("list_id", listId)
                                .bind("name", str(f, "name", ""))
                                .bind("field_type", str(f, "field_type", "text"))
                                .bind("config", str(f, "config", "{}"))
                                .bind("required", boolVal(f, "required"))
                                .bind("position", intVal(f, "position")));
                idMap.put("waitingListField", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importWaitingListEntries(Map<String, Object> data, IdRemapper idMap) {
        var entries = (List<Map<String, Object>>) data.getOrDefault("waitingListEntries", List.of());
        int count = 0;
        for (var e : entries) {
            int listId = idMap.get("waitingList", intVal(e, "list_id"));
            if (listId > 0) {
                int oldId = intVal(e, "id");
                Integer memberId = e.get("member_id") != null ? idMap.get("member", intVal(e, "member_id")) : null;
                int newId = insertReturningId(
                        "INSERT INTO waiting_list_entry(list_id, firstname, lastname, parent_name, email, access_token, status, confirmed_at, reminder_sent_at, created_at, notes, member_id, invited_at, testing_at, joined_at, withdrawn_at, attendance_count) VALUES(:list_id, :firstname, :lastname, :parent_name, :email, :access_token, :status, :confirmed_at::timestamp, :reminder_sent_at::timestamp, :created_at::timestamp, :notes, :member_id, :invited_at::timestamp, :testing_at::timestamp, :joined_at::timestamp, :withdrawn_at::timestamp, :attendance_count) RETURNING id;",
                        call().bind("list_id", listId)
                                .bind("firstname", str(e, "firstname", ""))
                                .bind("lastname", str(e, "lastname", ""))
                                .bind("parent_name", str(e, "parent_name", ""))
                                .bind("email", str(e, "email", ""))
                                .bind(
                                        "access_token",
                                        str(
                                                e,
                                                "access_token",
                                                java.util.UUID.randomUUID().toString()))
                                .bind("status", str(e, "status", "WAITING"))
                                .bind("confirmed_at", str(e, "confirmed_at", null))
                                .bind("reminder_sent_at", str(e, "reminder_sent_at", null))
                                .bind("created_at", str(e, "created_at", null))
                                .bind("notes", str(e, "notes", ""))
                                .bind("member_id", memberId != null && memberId > 0 ? memberId : null)
                                .bind("invited_at", str(e, "invited_at", null))
                                .bind("testing_at", str(e, "testing_at", null))
                                .bind("joined_at", str(e, "joined_at", null))
                                .bind("withdrawn_at", str(e, "withdrawn_at", null))
                                .bind("attendance_count", intVal(e, "attendance_count")));
                idMap.put("waitingListEntry", oldId, newId);
                count++;
            }
        }
        return count;
    }

    // -- Entity Notes --

    @SuppressWarnings("unchecked")
    private int importEntityNotes(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var notes = (List<Map<String, Object>>) data.getOrDefault("entityNotes", List.of());
        for (var n : notes) {
            int oldId = intVal(n, "id");
            Integer updatedBy = n.get("updated_by") != null ? idMap.get("member", intVal(n, "updated_by")) : null;
            int newId = insertReturningId(
                    "INSERT INTO entity_note(entity_type, entity_id, station_id, content, updated_by, updated_at) VALUES(:entity_type, :entity_id, :station_id, :content, :updated_by, :updated_at::timestamptz) RETURNING id;",
                    call().bind("entity_type", str(n, "entity_type", ""))
                            .bind("entity_id", intVal(n, "entity_id"))
                            .bind("station_id", stationId)
                            .bind("content", str(n, "content", ""))
                            .bind("updated_by", updatedBy != null && updatedBy > 0 ? updatedBy : null)
                            .bind("updated_at", str(n, "updated_at", null)));
            idMap.put("entityNote", oldId, newId);
        }
        return notes.size();
    }

    @SuppressWarnings("unchecked")
    private int importEntityNoteVersions(Map<String, Object> data, IdRemapper idMap) {
        var versions = (List<Map<String, Object>>) data.getOrDefault("entityNoteVersions", List.of());
        int count = 0;
        for (var v : versions) {
            int noteId = idMap.get("entityNote", intVal(v, "note_id"));
            int authorId = idMap.get("member", intVal(v, "author_id"));
            if (noteId > 0 && authorId > 0) {
                query(
                                "INSERT INTO entity_note_version(note_id, diff_patch, author_id, created_at) VALUES(:note_id, :diff_patch, :author_id, :created_at::TIMESTAMPTZ);")
                        .single(call().bind("note_id", noteId)
                                .bind("diff_patch", str(v, "diff_patch", ""))
                                .bind("author_id", authorId)
                                .bind("created_at", str(v, "created_at", null)))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- Equipment --

    @SuppressWarnings("unchecked")
    private int importEquipmentExchangeRequests(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var requests = (List<Map<String, Object>>) data.getOrDefault("equipmentExchangeRequests", List.of());
        for (var r : requests) {
            int oldId = intVal(r, "id");
            int memberId = idMap.get("member", intVal(r, "member_id"));
            int inventoryId = idMap.get("inventory", intVal(r, "inventory_id"));
            Integer itemId = r.get("item_id") != null ? idMap.get("inventoryItem", intVal(r, "item_id")) : null;
            Integer oldSizeId =
                    r.get("old_size_id") != null ? idMap.get("inventorySize", intVal(r, "old_size_id")) : null;
            Integer newSizeId =
                    r.get("new_size_id") != null ? idMap.get("inventorySize", intVal(r, "new_size_id")) : null;
            Integer exchangedItemId = r.get("exchanged_item_id") != null
                    ? idMap.get("inventoryItem", intVal(r, "exchanged_item_id"))
                    : null;
            Integer createdBy = r.get("created_by") != null ? idMap.get("member", intVal(r, "created_by")) : null;
            if (memberId > 0 && inventoryId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO equipment_exchange_request(station_id, member_id, item_id, inventory_id, old_size_id, new_size_id, exchanged_item_id, status, reason, created_by, created_at, updated_at) VALUES(:station_id, :member_id, :item_id, :inventory_id, :old_size_id, :new_size_id, :exchanged_item_id, :status, :reason, :created_by, :created_at::timestamp, :updated_at::timestamp) RETURNING id;",
                        call().bind("station_id", stationId)
                                .bind("member_id", memberId)
                                .bind("item_id", itemId != null && itemId > 0 ? itemId : null)
                                .bind("inventory_id", inventoryId)
                                .bind("old_size_id", oldSizeId != null && oldSizeId > 0 ? oldSizeId : null)
                                .bind("new_size_id", newSizeId != null && newSizeId > 0 ? newSizeId : null)
                                .bind(
                                        "exchanged_item_id",
                                        exchangedItemId != null && exchangedItemId > 0 ? exchangedItemId : null)
                                .bind("status", str(r, "status", "ANNOUNCED"))
                                .bind("reason", str(r, "reason", ""))
                                .bind("created_by", createdBy != null && createdBy > 0 ? createdBy : null)
                                .bind("created_at", str(r, "created_at", null))
                                .bind("updated_at", str(r, "updated_at", null)));
                idMap.put("equipmentExchangeRequest", oldId, newId);
            }
        }
        return requests.size();
    }

    @SuppressWarnings("unchecked")
    private int importEquipmentExchangeLogs(Map<String, Object> data, IdRemapper idMap) {
        var logs = (List<Map<String, Object>>) data.getOrDefault("equipmentExchangeLogs", List.of());
        int count = 0;
        for (var l : logs) {
            int requestId = idMap.get("equipmentExchangeRequest", intVal(l, "request_id"));
            int changedBy = idMap.get("member", intVal(l, "changed_by"));
            if (requestId > 0 && changedBy > 0) {
                query(
                                "INSERT INTO equipment_exchange_log(request_id, old_status, new_status, changed_by, changed_at, note) VALUES(:request_id, :old_status, :new_status, :changed_by, :changed_at::TIMESTAMP, :note);")
                        .single(call().bind("request_id", requestId)
                                .bind("old_status", str(l, "old_status", ""))
                                .bind("new_status", str(l, "new_status", ""))
                                .bind("changed_by", changedBy)
                                .bind("changed_at", str(l, "changed_at", null))
                                .bind("note", str(l, "note", "")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEquipmentProcurements(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("equipmentProcurements", List.of());
        int count = 0;
        for (var p : items) {
            int inventoryId = idMap.get("inventory", intVal(p, "inventory_id"));
            int memberId = idMap.get("member", intVal(p, "member_id"));
            Integer sizeId = p.get("size_id") != null ? idMap.get("inventorySize", intVal(p, "size_id")) : null;
            if (inventoryId > 0 && memberId > 0) {
                query(
                                "INSERT INTO equipment_procurement(station_id, inventory_id, member_id, size_id, notes, requested_at, fulfilled_at) VALUES(:station_id, :inventory_id, :member_id, :size_id, :notes, :requested_at::TIMESTAMP, :fulfilled_at::TIMESTAMP);")
                        .single(call().bind("station_id", stationId)
                                .bind("inventory_id", inventoryId)
                                .bind("member_id", memberId)
                                .bind("size_id", sizeId != null && sizeId > 0 ? sizeId : null)
                                .bind("notes", str(p, "notes", ""))
                                .bind("requested_at", str(p, "requested_at", null))
                                .bind("fulfilled_at", str(p, "fulfilled_at", null)))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- Form responses & restrictions --

    @SuppressWarnings("unchecked")
    private int importFormResponses(Map<String, Object> data, IdRemapper idMap) {
        var responses = (List<Map<String, Object>>) data.getOrDefault("formResponses", List.of());
        for (var r : responses) {
            int oldId = intVal(r, "id");
            int formId = idMap.get("form", intVal(r, "form_id"));
            int memberId = idMap.get("member", intVal(r, "member_id"));
            int submittedBy = idMap.get("member", intVal(r, "submitted_by"));
            if (formId > 0 && memberId > 0 && submittedBy > 0) {
                int newId = insertReturningId(
                        "INSERT INTO form_response(form_id, member_id, submitted_by, submitted_at, updated_at) VALUES(:form_id, :member_id, :submitted_by, :submitted_at::timestamp, :updated_at::timestamp) RETURNING id;",
                        call().bind("form_id", formId)
                                .bind("member_id", memberId)
                                .bind("submitted_by", submittedBy)
                                .bind("submitted_at", str(r, "submitted_at", null))
                                .bind("updated_at", str(r, "updated_at", null)));
                idMap.put("formResponse", oldId, newId);
            }
        }
        return responses.size();
    }

    @SuppressWarnings("unchecked")
    private int importFormAnswers(Map<String, Object> data, IdRemapper idMap) {
        var answers = (List<Map<String, Object>>) data.getOrDefault("formAnswers", List.of());
        int count = 0;
        for (var a : answers) {
            int responseId = idMap.get("formResponse", intVal(a, "response_id"));
            int questionId = idMap.get("formQuestion", intVal(a, "question_id"));
            if (responseId > 0 && questionId > 0) {
                query(
                                "INSERT INTO form_answer(response_id, question_id, value) VALUES(:response_id, :question_id, :value::JSONB) ON CONFLICT DO NOTHING;")
                        .single(call().bind("response_id", responseId)
                                .bind("question_id", questionId)
                                .bind("value", str(a, "value", "{}")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importFormRestrictions(Map<String, Object> data, IdRemapper idMap) {
        var restrictions = (List<Map<String, Object>>) data.getOrDefault("formRestrictions", List.of());
        int count = 0;
        for (var r : restrictions) {
            int formId = idMap.get("form", intVal(r, "form_id"));
            if (formId > 0) {
                Integer groupId = r.get("group_id") != null ? idMap.get("group", intVal(r, "group_id")) : null;
                Integer tagId = r.get("tag_id") != null ? idMap.get("tag", intVal(r, "tag_id")) : null;
                Integer memberId = r.get("member_id") != null ? idMap.get("member", intVal(r, "member_id")) : null;
                query(
                                "INSERT INTO form_restriction(form_id, user_type, group_id, tag_id, member_id) VALUES(:form_id, :user_type, :group_id, :tag_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("form_id", formId)
                                .bind("user_type", str(r, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("tag_id", tagId != null && tagId > 0 ? tagId : null)
                                .bind("member_id", memberId != null && memberId > 0 ? memberId : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- Event restrictions & layouts --

    @SuppressWarnings("unchecked")
    private int importEventRestrictions(Map<String, Object> data, IdRemapper idMap) {
        var restrictions = (List<Map<String, Object>>) data.getOrDefault("eventRestrictions", List.of());
        int count = 0;
        for (var r : restrictions) {
            int eventId = idMap.get("event", intVal(r, "event_id"));
            if (eventId > 0) {
                Integer groupId = r.get("group_id") != null ? idMap.get("group", intVal(r, "group_id")) : null;
                Integer tagId = r.get("tag_id") != null ? idMap.get("tag", intVal(r, "tag_id")) : null;
                Integer memberId = r.get("member_id") != null ? idMap.get("member", intVal(r, "member_id")) : null;
                query(
                                "INSERT INTO event_restriction(event_id, user_type, group_id, tag_id, member_id) VALUES(:event_id, :user_type, :group_id, :tag_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("event_id", eventId)
                                .bind("user_type", str(r, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("tag_id", tagId != null && tagId > 0 ? tagId : null)
                                .bind("member_id", memberId != null && memberId > 0 ? memberId : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEventLayouts(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var layouts = (List<Map<String, Object>>) data.getOrDefault("eventLayouts", List.of());
        for (var l : layouts) {
            int oldId = intVal(l, "id");
            int newId = insertReturningId(
                    "INSERT INTO event_layout(station_id, name) VALUES(:station_id, :name) RETURNING id;",
                    call().bind("station_id", stationId).bind("name", str(l, "name", "")));
            idMap.put("eventLayout", oldId, newId);
        }
        return layouts.size();
    }

    @SuppressWarnings("unchecked")
    private int importEventLayoutFields(Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("eventLayoutFields", List.of());
        int count = 0;
        for (var f : fields) {
            int layoutId = idMap.get("eventLayout", intVal(f, "layout_id"));
            Integer attendanceFieldId = f.get("attendance_field_id") != null
                    ? idMap.get("attendanceTemplateField", intVal(f, "attendance_field_id"))
                    : null;
            if (layoutId > 0) {
                int oldId = intVal(f, "id");
                int newId = insertReturningId(
                        "INSERT INTO event_layout_field(layout_id, name, field_type, config, position, overview, attendance_field_id) VALUES(:layout_id, :name, :field_type, :config::jsonb, :position, :overview, :attendance_field_id) RETURNING id;",
                        call().bind("layout_id", layoutId)
                                .bind("name", str(f, "name", ""))
                                .bind("field_type", str(f, "field_type", "string"))
                                .bind("config", str(f, "config", "{}"))
                                .bind("position", intVal(f, "position"))
                                .bind("overview", boolVal(f, "overview"))
                                .bind(
                                        "attendance_field_id",
                                        attendanceFieldId != null && attendanceFieldId > 0 ? attendanceFieldId : null));
                idMap.put("eventLayoutField", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEventTemplateFields(Map<String, Object> data, IdRemapper idMap) {
        var fields = (List<Map<String, Object>>) data.getOrDefault("eventTemplateFields", List.of());
        int count = 0;
        for (var f : fields) {
            int templateId = idMap.get("eventTemplate", intVal(f, "template_id"));
            Integer attendanceFieldId = f.get("attendance_field_id") != null
                    ? idMap.get("attendanceTemplateField", intVal(f, "attendance_field_id"))
                    : null;
            if (templateId > 0) {
                int oldId = intVal(f, "id");
                int newId = insertReturningId(
                        "INSERT INTO event_template_field(template_id, name, field_type, config, position, overview, public, attendance_field_id) VALUES(:template_id, :name, :field_type, :config::jsonb, :position, :overview, :public, :attendance_field_id) RETURNING id;",
                        call().bind("template_id", templateId)
                                .bind("name", str(f, "name", ""))
                                .bind("field_type", str(f, "field_type", "string"))
                                .bind("config", str(f, "config", "{}"))
                                .bind("position", intVal(f, "position"))
                                .bind("overview", boolVal(f, "overview"))
                                .bind("public", boolVal(f, "public"))
                                .bind(
                                        "attendance_field_id",
                                        attendanceFieldId != null && attendanceFieldId > 0 ? attendanceFieldId : null));
                idMap.put("eventTemplateField", oldId, newId);
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importEventTemplateRestrictions(Map<String, Object> data, IdRemapper idMap) {
        var restrictions = (List<Map<String, Object>>) data.getOrDefault("eventTemplateRestrictions", List.of());
        int count = 0;
        for (var r : restrictions) {
            int templateId = idMap.get("eventTemplate", intVal(r, "template_id"));
            if (templateId > 0) {
                query(
                                "INSERT INTO event_template_restriction(template_id, user_type) VALUES(:template_id, :user_type) ON CONFLICT DO NOTHING;")
                        .single(call().bind("template_id", templateId).bind("user_type", str(r, "user_type", "")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- KB tags, restrictions & comments --

    @SuppressWarnings("unchecked")
    private int importKbTags(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var tags = (List<Map<String, Object>>) data.getOrDefault("kbTags", List.of());
        for (var t : tags) {
            int oldId = intVal(t, "id");
            int newId = insertReturningId(
                    "INSERT INTO kb_tag(station_id, name) VALUES(:station_id, :name) RETURNING id;",
                    call().bind("station_id", stationId).bind("name", str(t, "name", "")));
            idMap.put("kbTag", oldId, newId);
        }
        return tags.size();
    }

    @SuppressWarnings("unchecked")
    private int importKbFileTags(Map<String, Object> data, IdRemapper idMap) {
        var fileTags = (List<Map<String, Object>>) data.getOrDefault("kbFileTags", List.of());
        int count = 0;
        for (var ft : fileTags) {
            int fileId = idMap.get("kbFile", intVal(ft, "file_id"));
            int tagId = idMap.get("kbTag", intVal(ft, "tag_id"));
            if (fileId > 0 && tagId > 0) {
                query("INSERT INTO kb_file_tag(file_id, tag_id) VALUES(:file_id, :tag_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("file_id", fileId).bind("tag_id", tagId))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importKbFolderTags(Map<String, Object> data, IdRemapper idMap) {
        var folderTags = (List<Map<String, Object>>) data.getOrDefault("kbFolderTags", List.of());
        int count = 0;
        for (var ft : folderTags) {
            int folderId = idMap.get("kbFolder", intVal(ft, "folder_id"));
            int tagId = idMap.get("kbTag", intVal(ft, "tag_id"));
            if (folderId > 0 && tagId > 0) {
                query(
                                "INSERT INTO kb_folder_tag(folder_id, tag_id) VALUES(:folder_id, :tag_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("folder_id", folderId).bind("tag_id", tagId))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importKbAccessRestrictions(Map<String, Object> data, IdRemapper idMap) {
        var restrictions = (List<Map<String, Object>>) data.getOrDefault("kbAccessRestrictions", List.of());
        int count = 0;
        for (var r : restrictions) {
            Integer folderId = r.get("folder_id") != null ? idMap.get("kbFolder", intVal(r, "folder_id")) : null;
            Integer fileId = r.get("file_id") != null ? idMap.get("kbFile", intVal(r, "file_id")) : null;
            Integer groupId = r.get("group_id") != null ? idMap.get("group", intVal(r, "group_id")) : null;
            Integer tagId = r.get("tag_id") != null ? idMap.get("tag", intVal(r, "tag_id")) : null;
            Integer memberId = r.get("member_id") != null ? idMap.get("member", intVal(r, "member_id")) : null;
            if ((folderId != null && folderId > 0) || (fileId != null && fileId > 0)) {
                query(
                                "INSERT INTO kb_access_restriction(folder_id, file_id, user_type, group_id, tag_id, member_id) VALUES(:folder_id, :file_id, :user_type, :group_id, :tag_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("folder_id", folderId != null && folderId > 0 ? folderId : null)
                                .bind("file_id", fileId != null && fileId > 0 ? fileId : null)
                                .bind("user_type", str(r, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("tag_id", tagId != null && tagId > 0 ? tagId : null)
                                .bind("member_id", memberId != null && memberId > 0 ? memberId : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importKbComments(Map<String, Object> data, IdRemapper idMap) {
        var comments = (List<Map<String, Object>>) data.getOrDefault("kbComments", List.of());
        for (var c : comments) {
            int oldId = intVal(c, "id");
            int fileId = idMap.get("kbFile", intVal(c, "file_id"));
            Integer oldParentId = c.get("parent_id") != null ? intVal(c, "parent_id") : null;
            Integer parentId = oldParentId != null ? idMap.get("kbComment", oldParentId) : null;
            if (fileId > 0) {
                int newId = insertReturningId(
                        "INSERT INTO kb_comment(file_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at) VALUES(:file_id, :parent_id, :author_station_uid::uuid, :author_member_uid::uuid, :content, :deleted, :created_at::timestamptz, :updated_at::timestamptz) RETURNING id;",
                        call().bind("file_id", fileId)
                                .bind("parent_id", parentId != null && parentId > 0 ? parentId : null)
                                .bind("author_station_uid", str(c, "author_station_uid", null))
                                .bind("author_member_uid", str(c, "author_member_uid", null))
                                .bind("content", str(c, "content", ""))
                                .bind("deleted", boolVal(c, "deleted"))
                                .bind("created_at", str(c, "created_at", null))
                                .bind("updated_at", str(c, "updated_at", null)));
                idMap.put("kbComment", oldId, newId);
            }
        }
        return comments.size();
    }

    // -- Inventory extras --

    @SuppressWarnings("unchecked")
    private int importInventoryChecks(int stationId, Map<String, Object> data, IdRemapper idMap) {
        var checks = (List<Map<String, Object>>) data.getOrDefault("inventoryChecks", List.of());
        for (var c : checks) {
            int oldId = intVal(c, "id");
            int memberId = idMap.get("member", intVal(c, "member_id"));
            int checkedBy = idMap.get("member", intVal(c, "checked_by"));
            if (memberId > 0 && checkedBy > 0) {
                int newId = insertReturningId(
                        "INSERT INTO inventory_check(station_id, member_id, checked_by, checked_at) VALUES(:station_id, :member_id, :checked_by, :checked_at::timestamptz) RETURNING id;",
                        call().bind("station_id", stationId)
                                .bind("member_id", memberId)
                                .bind("checked_by", checkedBy)
                                .bind("checked_at", str(c, "checked_at", null)));
                idMap.put("inventoryCheck", oldId, newId);
            }
        }
        return checks.size();
    }

    @SuppressWarnings("unchecked")
    private int importInventoryCheckItems(Map<String, Object> data, IdRemapper idMap) {
        var items = (List<Map<String, Object>>) data.getOrDefault("inventoryCheckItems", List.of());
        int count = 0;
        for (var i : items) {
            int checkId = idMap.get("inventoryCheck", intVal(i, "check_id"));
            Integer itemId = i.get("item_id") != null ? idMap.get("inventoryItem", intVal(i, "item_id")) : null;
            Integer inventoryId =
                    i.get("inventory_id") != null ? idMap.get("inventory", intVal(i, "inventory_id")) : null;
            if (checkId > 0) {
                query(
                                "INSERT INTO inventory_check_item(check_id, item_id, inventory_id, result, note) VALUES(:check_id, :item_id, :inventory_id, :result, :note) ON CONFLICT DO NOTHING;")
                        .single(call().bind("check_id", checkId)
                                .bind("item_id", itemId != null && itemId > 0 ? itemId : null)
                                .bind("inventory_id", inventoryId != null && inventoryId > 0 ? inventoryId : null)
                                .bind("result", str(i, "result", ""))
                                .bind("note", str(i, "note", "")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importInventoryItemHistory(Map<String, Object> data, IdRemapper idMap) {
        var history = (List<Map<String, Object>>) data.getOrDefault("inventoryItemHistory", List.of());
        int count = 0;
        for (var h : history) {
            int itemId = idMap.get("inventoryItem", intVal(h, "item_id"));
            Integer memberId = h.get("member_id") != null ? idMap.get("member", intVal(h, "member_id")) : null;
            if (itemId > 0) {
                query(
                                "INSERT INTO inventory_item_history(item_id, member_id, member_name, given_out, returned) VALUES(:item_id, :member_id, :member_name, :given_out::TIMESTAMP, :returned::TIMESTAMP);")
                        .single(call().bind("item_id", itemId)
                                .bind("member_id", memberId != null && memberId > 0 ? memberId : null)
                                .bind("member_name", str(h, "member_name", ""))
                                .bind("given_out", str(h, "given_out", null))
                                .bind("returned", str(h, "returned", null)))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importInventoryRequirements(Map<String, Object> data, IdRemapper idMap) {
        var requirements = (List<Map<String, Object>>) data.getOrDefault("inventoryRequirements", List.of());
        int count = 0;
        for (var r : requirements) {
            int inventoryId = idMap.get("inventory", intVal(r, "inventory_id"));
            Integer groupId = r.get("group_id") != null ? idMap.get("group", intVal(r, "group_id")) : null;
            if (inventoryId > 0) {
                query(
                                "INSERT INTO inventory_requirement(inventory_id, user_type, group_id, quantity, position) VALUES(:inventory_id, :user_type, :group_id, :quantity, :position) ON CONFLICT DO NOTHING;")
                        .single(call().bind("inventory_id", inventoryId)
                                .bind("user_type", str(r, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("quantity", intVal(r, "quantity"))
                                .bind("position", intVal(r, "position")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- News restrictions --

    @SuppressWarnings("unchecked")
    private int importNewsRestrictions(Map<String, Object> data, IdRemapper idMap) {
        var restrictions = (List<Map<String, Object>>) data.getOrDefault("newsRestrictions", List.of());
        int count = 0;
        for (var r : restrictions) {
            int newsId = idMap.get("news", intVal(r, "news_id"));
            if (newsId > 0) {
                Integer groupId = r.get("group_id") != null ? idMap.get("group", intVal(r, "group_id")) : null;
                Integer tagId = r.get("tag_id") != null ? idMap.get("tag", intVal(r, "tag_id")) : null;
                Integer memberId = r.get("member_id") != null ? idMap.get("member", intVal(r, "member_id")) : null;
                query(
                                "INSERT INTO news_restriction(news_id, user_type, group_id, tag_id, member_id) VALUES(:news_id, :user_type, :group_id, :tag_id, :member_id) ON CONFLICT DO NOTHING;")
                        .single(call().bind("news_id", newsId)
                                .bind("user_type", str(r, "user_type", null))
                                .bind("group_id", groupId != null && groupId > 0 ? groupId : null)
                                .bind("tag_id", tagId != null && tagId > 0 ? tagId : null)
                                .bind("member_id", memberId != null && memberId > 0 ? memberId : null))
                        .insert();
                count++;
            }
        }
        return count;
    }

    // -- User settings --

    @SuppressWarnings("unchecked")
    private int importUserSettings(Map<String, Object> data, IdRemapper idMap) {
        var settings = (List<Map<String, Object>>) data.getOrDefault("userSettings", List.of());
        int count = 0;
        for (var s : settings) {
            int memberId = idMap.get("member", intVal(s, "member_id"));
            if (memberId > 0) {
                query(
                                "INSERT INTO user_settings(member_id, email_enabled, theme, dark_mode, feel) VALUES(:member_id, :email_enabled, :theme, :dark_mode, :feel) ON CONFLICT DO NOTHING;")
                        .single(call().bind("member_id", memberId)
                                .bind("email_enabled", boolVal(s, "email_enabled"))
                                .bind("theme", str(s, "theme", "ember"))
                                .bind("dark_mode", str(s, "dark_mode", "system"))
                                .bind("feel", str(s, "feel", "ROUNDED")))
                        .insert();
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importUserNotificationSettings(Map<String, Object> data, IdRemapper idMap) {
        var settings = (List<Map<String, Object>>) data.getOrDefault("userNotificationSettings", List.of());
        int count = 0;
        for (var s : settings) {
            int memberId = idMap.get("member", intVal(s, "member_id"));
            if (memberId > 0) {
                query(
                                "INSERT INTO user_notification_settings(member_id, notification_type, app_enabled, email_enabled, feed_enabled) VALUES(:member_id, :notification_type, :app_enabled, :email_enabled, :feed_enabled) ON CONFLICT DO NOTHING;")
                        .single(call().bind("member_id", memberId)
                                .bind("notification_type", str(s, "notification_type", ""))
                                .bind("app_enabled", boolVal(s, "app_enabled"))
                                .bind("email_enabled", boolVal(s, "email_enabled"))
                                .bind("feed_enabled", boolVal(s, "feed_enabled")))
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
                    "INSERT INTO kb_folder(station_id, parent_id, name, description, position, restriction_mode) VALUES(:station_id, :parent_id, :name, :description, :position, :restriction_mode) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("parent_id", parentId)
                            .bind("name", str(folder, "name", ""))
                            .bind("description", str(folder, "description", ""))
                            .bind("position", intVal(folder, "position"))
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
                    "INSERT INTO kb_file(station_id, folder_id, name, description, file_type, position, restriction_mode) VALUES(:station_id, :folder_id, :name, :description, :file_type, :position, :restriction_mode) RETURNING id;",
                    call().bind("station_id", stationId)
                            .bind("folder_id", folderId)
                            .bind("name", str(file, "name", ""))
                            .bind("description", str(file, "description", ""))
                            .bind("file_type", str(file, "file_type", "MARKDOWN"))
                            .bind("position", intVal(file, "position"))
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
            query(
                            "INSERT INTO kb_file_content(file_id, text_content) VALUES(:file_id, :text_content) ON CONFLICT(file_id) DO UPDATE SET text_content = :text_content;")
                    .single(call().bind("file_id", newFileId).bind("text_content", text))
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
            query(
                            "INSERT INTO kb_file_version(file_id, patch, is_full, version, created_at) VALUES(:file_id, :patch, :is_full, :version, now());")
                    .single(call().bind("file_id", newFileId)
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
