/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.service.ScoreEvaluator;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Singleton
public class WaitingListRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(WaitingListRoutes.class);

    private final WaitingListService service;

    @Inject
    public WaitingListRoutes(WaitingListService service) {
        this.service = service;
    }

    private void verifyListOwnership(int listId, UserSession session) {
        var list = service.findById(listId).orElseThrow(NotFoundResponse::new);
        if (list.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Public endpoints
        routes.get(prefix + "/public/waiting-list/invite/{code}", this::getInviteInfo);
        routes.post(prefix + "/public/waiting-list/register", this::registerViaInvite);
        routes.get(prefix + "/public/waiting-list/entry/{token}", this::getEntryByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/remove", this::removeByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/confirm", this::confirmInterest);

        // Read endpoints
        routes.get(prefix + "/waiting-lists", this::listAll, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}", this::getById, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}/fields", this::listFields, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}/invites", this::listInvites, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}/entries", this::listEntries, StationPermission.WAITLIST_READ);

        // Add endpoints
        routes.post(prefix + "/waiting-lists/{id}/entries", this::createEntry, StationPermission.WAITLIST_ADD);

        // Management endpoints
        routes.post(prefix + "/waiting-lists", this::create, StationPermission.WAITLIST_EDIT);
        routes.put(prefix + "/waiting-lists/{id}", this::update, StationPermission.WAITLIST_EDIT);
        routes.delete(prefix + "/waiting-lists/{id}", this::deleteList, StationPermission.WAITLIST_EDIT);
        routes.put(
                prefix + "/waiting-lists/{id}/visible-fields",
                this::updateVisibleFields,
                StationPermission.WAITLIST_MANAGER);
        routes.post(prefix + "/waiting-lists/{id}/fields", this::createField, StationPermission.WAITLIST_EDIT);
        routes.put(prefix + "/waiting-lists/{id}/fields/{fieldId}", this::updateField, StationPermission.WAITLIST_EDIT);
        routes.delete(
                prefix + "/waiting-lists/{id}/fields/{fieldId}", this::deleteField, StationPermission.WAITLIST_EDIT);
        routes.post(prefix + "/waiting-lists/{id}/invites", this::createInvite, StationPermission.WAITLIST_EDIT);
        routes.delete(
                prefix + "/waiting-lists/{id}/invites/{inviteId}", this::deleteInvite, StationPermission.WAITLIST_EDIT);
        routes.put(
                prefix + "/waiting-lists/{id}/entries/{entryId}", this::updateEntry, StationPermission.WAITLIST_EDIT);
        routes.delete(
                prefix + "/waiting-lists/{id}/entries/{entryId}", this::deleteEntry, StationPermission.WAITLIST_EDIT);
        routes.put(
                prefix + "/waiting-lists/{id}/entries/{entryId}/created-at",
                this::updateCreatedAt,
                StationPermission.WAITLIST_EDIT);

        // State transitions
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/invite",
                this::inviteEntry,
                StationPermission.WAITLIST_EDIT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/testing",
                this::moveToTesting,
                StationPermission.WAITLIST_EDIT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/join",
                this::moveToJoined,
                StationPermission.WAITLIST_EDIT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/withdraw",
                this::withdrawEntry,
                StationPermission.WAITLIST_EDIT);
    }

    // --- Public ---

    @OpenApi(
            path = "/api/v1/public/waiting-list/invite/{code}",
            methods = HttpMethod.GET,
            summary = "Get invite info and fields for registration",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "code", required = true))
    private void getInviteInfo(Context ctx) {
        String code = ctx.pathParam("code");
        var invite = service.findInviteByCode(code).orElseThrow(NotFoundResponse::new);
        if (!invite.hasUsesLeft() || invite.isExpired()) {
            throw new ForbiddenResponse("Invite is no longer valid");
        }
        var list = service.findById(invite.listId()).orElseThrow(NotFoundResponse::new);
        var fields = service.findFieldsByList(invite.listId());
        ctx.json(new InviteInfoResponse(list.name(), list.description(), fields));
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/register",
            methods = HttpMethod.POST,
            summary = "Register on waiting list via invite code",
            tags = {"Waiting List"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RegisterRequest.class)),
            responses = {@OpenApiResponse(status = "201"), @OpenApiResponse(status = "400")})
    private void registerViaInvite(Context ctx) {
        var request = ctx.bodyAsClass(RegisterRequest.class);
        if (request.inviteCode() == null || request.firstname() == null) {
            throw new BadRequestResponse("inviteCode and firstname are required");
        }
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        try {
            var entry = service.registerViaInvite(
                    request.inviteCode(),
                    request.firstname(),
                    request.lastname() != null ? request.lastname() : "",
                    guardians,
                    request.values() != null ? request.values() : Map.of(),
                    request.notes());
            ctx.status(HttpStatus.CREATED).json(new PublicEntryResponse(entry.accessToken()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument registering via waiting list invite", e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state registering via waiting list invite", e);
            throw new ForbiddenResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}",
            methods = HttpMethod.GET,
            summary = "View waiting list entry by access token",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true))
    private void getEntryByToken(Context ctx) {
        String token = ctx.pathParam("token");
        var entry = service.findEntryByToken(token).orElseThrow(NotFoundResponse::new);
        var values = service.findEntryValues(entry.id());
        var guardians = service.findGuardiansByEntry(entry.id());
        var list = service.findById(entry.listId()).orElseThrow(NotFoundResponse::new);
        var fields = service.findFieldsByList(entry.listId());
        int position = service.findEntriesByList(entry.listId()).stream()
                        .filter(e -> e.status() == WaitingListEntryStatus.WAITING)
                        .toList()
                        .indexOf(entry)
                + 1;
        ctx.json(new PublicStatusResponse(
                entry.firstname(),
                entry.lastname(),
                entry.parentName(),
                entry.status(),
                entry.confirmedAt().toString(),
                position,
                list.name(),
                fields,
                values,
                guardians));
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}/remove",
            methods = HttpMethod.POST,
            summary = "Remove self from waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true))
    private void removeByToken(Context ctx) {
        String token = ctx.pathParam("token");
        service.removeByToken(token);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}/confirm",
            methods = HttpMethod.POST,
            summary = "Re-confirm interest on waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true))
    private void confirmInterest(Context ctx) {
        String token = ctx.pathParam("token");
        service.confirmInterest(token);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // --- Management ---

    @OpenApi(
            path = "/api/v1/waiting-lists",
            methods = HttpMethod.GET,
            summary = "List waiting lists",
            tags = {"Waiting List"})
    private void listAll(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var lists = service.findByStation(session.member().stationId());
        ctx.json(lists.stream()
                .map(l -> new ListWithCount(l, service.countEntries(l.id())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/waiting-lists",
            methods = HttpMethod.POST,
            summary = "Create waiting list",
            tags = {"Waiting List"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ListRequest.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ListRequest.class);
        validateFormula(request.scoringFormula(), List.of());
        var list = service.create(
                session.member().stationId(),
                request.name(),
                request.description() != null ? request.description() : "",
                request.scoringFormula(),
                request.confirmIntervalDays() != null ? request.confirmIntervalDays() : 180,
                request.testingGroupId(),
                request.joinGroupId(),
                request.attendanceThreshold() != null ? request.attendanceThreshold() : 5);
        ctx.status(HttpStatus.CREATED).json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.GET,
            summary = "Get waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true))
    private void getById(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var list = service.findById(id).orElseThrow(NotFoundResponse::new);
        ctx.json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.PUT,
            summary = "Update waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ListRequest.class)))
    private void update(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var list = service.findById(id).orElseThrow(NotFoundResponse::new);
        if (list.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var request = ctx.bodyAsClass(ListRequest.class);
        var fieldNames = service.findFieldsByList(id).stream()
                .map(WaitingListField::name)
                .toList();
        validateFormula(request.scoringFormula(), fieldNames);
        var updated = service.update(
                        id,
                        request.name(),
                        request.description() != null ? request.description() : "",
                        request.scoringFormula(),
                        request.confirmIntervalDays() != null ? request.confirmIntervalDays() : 180,
                        request.testingGroupId(),
                        request.joinGroupId(),
                        request.attendanceThreshold() != null ? request.attendanceThreshold() : 5)
                .orElseThrow(NotFoundResponse::new);
        ctx.json(updated);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true))
    private void deleteList(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var list = service.findById(id).orElseThrow(NotFoundResponse::new);
        if (list.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        service.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void updateVisibleFields(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(id, UserSession.from(ctx));
        var request = ctx.bodyAsClass(VisibleFieldsRequest.class);
        var list = service.updateVisibleFields(id, toJson(request.fieldIds())).orElseThrow(NotFoundResponse::new);
        ctx.json(list);
    }

    private static String toJson(List<Integer> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < fieldIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(fieldIds.get(i));
        }
        return sb.append("]").toString();
    }

    // --- Fields ---

    private void listFields(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.findFieldsByList(listId));
    }

    private void createField(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        var request = ctx.bodyAsClass(FieldRequest.class);
        var field = service.createField(
                listId,
                request.name(),
                request.fieldType(),
                request.config() != null ? request.config() : WaitingListFieldConfig.parse("{}"),
                request.position(),
                request.required());
        ctx.status(HttpStatus.CREATED).json(field);
    }

    private void updateField(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var request = ctx.bodyAsClass(FieldRequest.class);
        var field = service.updateField(
                        fieldId,
                        request.name(),
                        request.fieldType(),
                        request.config() != null ? request.config() : WaitingListFieldConfig.parse("{}"),
                        request.position(),
                        request.required())
                .orElseThrow(NotFoundResponse::new);
        ctx.json(field);
    }

    private void deleteField(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        service.deleteField(fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // --- Invites ---

    private void listInvites(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.findInvitesByList(listId));
    }

    private void createInvite(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        var request = ctx.bodyAsClass(InviteRequest.class);
        Instant expiresAt = null;
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            try {
                expiresAt = Instant.parse(request.expiresAt());
            } catch (Exception e) {
                // Try parsing as date only (e.g., "2026-05-30") and convert to end of day UTC
                expiresAt = LocalDate.parse(request.expiresAt())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .plusSeconds(86399);
            }
        }
        var invite = service.createInvite(listId, request.maxUses() != null ? request.maxUses() : 1, expiresAt);
        ctx.status(HttpStatus.CREATED).json(invite);
    }

    private void deleteInvite(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int inviteId = ctx.pathParamAsClass("inviteId", Integer.class).get();
        service.deleteInvite(inviteId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // --- Entries ---

    private void listEntries(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        var entries = service.findEntriesByList(listId);
        var list = service.findById(listId).orElseThrow(NotFoundResponse::new);
        var fields = service.findFieldsByList(listId);
        var allGuardians = service.findGuardiansByList(listId);
        var guardianMap = new java.util.HashMap<Integer, List<WaitingListEntryGuardian>>();
        for (var g : allGuardians) {
            guardianMap
                    .computeIfAbsent(g.entryId(), k -> new java.util.ArrayList<>())
                    .add(g);
        }
        var result = entries.stream()
                .map(entry -> {
                    var values = service.findEntryValues(entry.id());
                    double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
                    var entryGuardians = guardianMap.getOrDefault(entry.id(), List.of());
                    return new EntryWithScore(entry, values, score, entryGuardians);
                })
                .toList();
        ctx.json(result);
    }

    private void createEntry(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        var request = ctx.bodyAsClass(EntryRequest.class);
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        var entry = service.createEntry(
                listId,
                request.firstname(),
                request.lastname() != null ? request.lastname() : "",
                guardians,
                request.values() != null ? request.values() : Map.of(),
                request.notes());
        ctx.status(HttpStatus.CREATED).json(entry);
    }

    private void updateEntry(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        var request = ctx.bodyAsClass(EntryRequest.class);
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        service.updateEntry(
                entryId,
                request.firstname(),
                request.lastname() != null ? request.lastname() : "",
                guardians,
                request.notes(),
                request.values());
        var updated = service.findEntryById(entryId).orElseThrow(NotFoundResponse::new);
        ctx.json(updated);
    }

    private void updateCreatedAt(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        var request = ctx.bodyAsClass(CreatedAtRequest.class);
        service.updateCreatedAt(entryId, request.createdAt());
        var updated = service.findEntryById(entryId).orElseThrow(NotFoundResponse::new);
        ctx.json(updated);
    }

    private void deleteEntry(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        service.deleteEntry(entryId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // --- State transitions ---

    private void inviteEntry(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.inviteEntry(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            log.warn("Waiting list entry not found for invite, entryId={}", entryId, e);
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state when inviting waiting list entry, entryId={}", entryId, e);
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void moveToTesting(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.moveToTesting(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            log.warn("Waiting list entry not found for moveToTesting, entryId={}", entryId, e);
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state when moving waiting list entry to testing, entryId={}", entryId, e);
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void moveToJoined(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.moveToJoined(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            log.warn("Waiting list entry not found for moveToJoined, entryId={}", entryId, e);
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state when moving waiting list entry to joined, entryId={}", entryId, e);
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void withdrawEntry(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        verifyListOwnership(listId, UserSession.from(ctx));
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            service.withdrawEntry(entryId);
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            log.warn("Waiting list entry not found for withdraw, entryId={}", entryId, e);
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state when withdrawing waiting list entry, entryId={}", entryId, e);
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void validateFormula(String formula, List<String> fieldNames) {
        if (formula == null || formula.isBlank()) return;
        try {
            ScoreEvaluator.validate(formula, fieldNames);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scoring formula: {}", formula, e);
            throw new BadRequestResponse("Invalid formula: " + e.getMessage());
        }
    }

    // --- Records ---

    @OpenApiName("WaitingListRegisterRequest")
    public record RegisterRequest(
            String inviteCode,
            String firstname,
            String lastname,
            String parentName,
            String email,
            List<GuardianRequest> guardians,
            Map<Integer, String> values,
            String notes) {}

    public record PublicEntryResponse(String accessToken) {}

    @OpenApiName("WaitingListPublicStatusResponse")
    public record PublicStatusResponse(
            String firstname,
            String lastname,
            String parentName,
            WaitingListEntryStatus status,
            String confirmedAt,
            int position,
            String listName,
            List<WaitingListField> fields,
            List<WaitingListEntryValue> values,
            List<WaitingListEntryGuardian> guardians) {}

    public record ListRequest(
            String name,
            String description,
            String scoringFormula,
            Integer confirmIntervalDays,
            Integer testingGroupId,
            Integer joinGroupId,
            Integer attendanceThreshold) {}

    @OpenApiName("WaitingListListWithCount")
    public record ListWithCount(WaitingList list, int entryCount) {}

    public record FieldRequest(
            String name, String fieldType, WaitingListFieldConfig config, int position, boolean required) {}

    public record VisibleFieldsRequest(List<Integer> fieldIds) {}

    public record InviteRequest(Integer maxUses, String expiresAt) {}

    public record EntryRequest(
            String firstname,
            String lastname,
            String parentName,
            String email,
            List<GuardianRequest> guardians,
            Map<Integer, String> values,
            String notes) {}

    public record CreatedAtRequest(Instant createdAt) {}

    public record InviteInfoResponse(String listName, String listDescription, List<WaitingListField> fields) {}

    @OpenApiName("WaitingListEntryWithScore")
    public record EntryWithScore(
            WaitingListEntry entry,
            List<WaitingListEntryValue> values,
            double score,
            List<WaitingListEntryGuardian> guardians) {}

    public record GuardianRequest(String name, String email, String phone) {}

    private static List<WaitingListService.GuardianInput> resolveGuardians(
            List<GuardianRequest> guardians, String parentName, String email) {
        if (guardians != null && !guardians.isEmpty()) {
            return guardians.stream()
                    .map(g -> new WaitingListService.GuardianInput(
                            g.name() != null ? g.name() : "",
                            g.email() != null ? g.email() : "",
                            g.phone() != null ? g.phone() : ""))
                    .toList();
        }
        if ((parentName != null && !parentName.isBlank()) || (email != null && !email.isBlank())) {
            return List.of(new WaitingListService.GuardianInput(
                    parentName != null ? parentName : "", email != null ? email : "", ""));
        }
        return List.of();
    }
}
