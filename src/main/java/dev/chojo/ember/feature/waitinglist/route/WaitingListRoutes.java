/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Singleton
public class WaitingListRoutes implements Routes {
    private final WaitingListService service;

    @Inject
    public WaitingListRoutes(WaitingListService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Public endpoints
        routes.get(prefix + "/public/waiting-list/invite/{code}", this::getInviteInfo);
        routes.post(prefix + "/public/waiting-list/register", this::registerViaInvite);
        routes.get(prefix + "/public/waiting-list/entry/{token}", this::getEntryByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/remove", this::removeByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/confirm", this::confirmInterest);

        // Management endpoints
        routes.get(prefix + "/waiting-lists", this::listAll, Roles.WAITLIST_MANAGEMENT);
        routes.post(prefix + "/waiting-lists", this::create, Roles.WAITLIST_MANAGEMENT);
        routes.get(prefix + "/waiting-lists/{id}", this::getById, Roles.WAITLIST_MANAGEMENT);
        routes.put(prefix + "/waiting-lists/{id}", this::update, Roles.WAITLIST_MANAGEMENT);
        routes.delete(prefix + "/waiting-lists/{id}", this::deleteList, Roles.WAITLIST_MANAGEMENT);
        routes.put(prefix + "/waiting-lists/{id}/visible-fields", this::updateVisibleFields, Roles.WAITLIST_MANAGEMENT);

        // Fields
        routes.get(prefix + "/waiting-lists/{id}/fields", this::listFields, Roles.WAITLIST_MANAGEMENT);
        routes.post(prefix + "/waiting-lists/{id}/fields", this::createField, Roles.WAITLIST_MANAGEMENT);
        routes.put(prefix + "/waiting-lists/{id}/fields/{fieldId}", this::updateField, Roles.WAITLIST_MANAGEMENT);
        routes.delete(prefix + "/waiting-lists/{id}/fields/{fieldId}", this::deleteField, Roles.WAITLIST_MANAGEMENT);

        // Invites
        routes.get(prefix + "/waiting-lists/{id}/invites", this::listInvites, Roles.WAITLIST_MANAGEMENT);
        routes.post(prefix + "/waiting-lists/{id}/invites", this::createInvite, Roles.WAITLIST_MANAGEMENT);
        routes.delete(prefix + "/waiting-lists/{id}/invites/{inviteId}", this::deleteInvite, Roles.WAITLIST_MANAGEMENT);

        // Entries
        routes.get(prefix + "/waiting-lists/{id}/entries", this::listEntries, Roles.WAITLIST_MANAGEMENT);
        routes.post(prefix + "/waiting-lists/{id}/entries", this::createEntry, Roles.WAITLIST_MANAGEMENT);
        routes.put(prefix + "/waiting-lists/{id}/entries/{entryId}", this::updateEntry, Roles.WAITLIST_MANAGEMENT);
        routes.delete(prefix + "/waiting-lists/{id}/entries/{entryId}", this::deleteEntry, Roles.WAITLIST_MANAGEMENT);
        routes.put(
                prefix + "/waiting-lists/{id}/entries/{entryId}/created-at",
                this::updateCreatedAt,
                Roles.WAITLIST_MANAGEMENT);

        // State transitions
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/invite", this::inviteEntry, Roles.WAITLIST_MANAGEMENT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/testing",
                this::moveToTesting,
                Roles.WAITLIST_MANAGEMENT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/join", this::moveToJoined, Roles.WAITLIST_MANAGEMENT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/withdraw",
                this::withdrawEntry,
                Roles.WAITLIST_MANAGEMENT);
    }

    // --- Public ---

    @OpenApi(
            path = "/api/v1/public/waiting-list/invite/{code}",
            methods = HttpMethod.GET,
            summary = "Get invite info and fields for registration",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "code", type = String.class, required = true))
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
        if (request.inviteCode() == null || request.firstname() == null || request.email() == null) {
            throw new BadRequestResponse("inviteCode, firstname and email are required");
        }
        try {
            var entry = service.registerViaInvite(
                    request.inviteCode(),
                    request.firstname(),
                    request.lastname() != null ? request.lastname() : "",
                    request.parentName() != null ? request.parentName() : "",
                    request.email(),
                    request.values() != null ? request.values() : Map.of(),
                    request.notes());
            ctx.status(HttpStatus.CREATED).json(new PublicEntryResponse(entry.accessToken()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (IllegalStateException e) {
            throw new ForbiddenResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}",
            methods = HttpMethod.GET,
            summary = "View waiting list entry by access token",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true))
    private void getEntryByToken(Context ctx) {
        String token = ctx.pathParam("token");
        var entry = service.findEntryByToken(token).orElseThrow(NotFoundResponse::new);
        var values = service.findEntryValues(entry.id());
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
                values));
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}/remove",
            methods = HttpMethod.POST,
            summary = "Remove self from waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true))
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
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true))
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
                request.joinRoleId(),
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
        var request = ctx.bodyAsClass(ListRequest.class);
        var fieldNames = service.findFieldsByList(id).stream()
                .map(WaitingListField::name)
                .toList();
        validateFormula(request.scoringFormula(), fieldNames);
        var list = service.update(
                        id,
                        request.name(),
                        request.description() != null ? request.description() : "",
                        request.scoringFormula(),
                        request.confirmIntervalDays() != null ? request.confirmIntervalDays() : 180,
                        request.testingGroupId(),
                        request.joinGroupId(),
                        request.joinRoleId(),
                        request.attendanceThreshold() != null ? request.attendanceThreshold() : 5)
                .orElseThrow(NotFoundResponse::new);
        ctx.json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true))
    private void deleteList(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void updateVisibleFields(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        var request = ctx.bodyAsClass(FieldRequest.class);
        var field = service.createField(
                listId,
                request.name(),
                request.fieldType(),
                request.config() != null ? request.config() : "{}",
                request.position(),
                request.required());
        ctx.status(HttpStatus.CREATED).json(field);
    }

    private void updateField(Context ctx) {
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var request = ctx.bodyAsClass(FieldRequest.class);
        var field = service.updateField(
                        fieldId,
                        request.name(),
                        request.fieldType(),
                        request.config() != null ? request.config() : "{}",
                        request.position(),
                        request.required())
                .orElseThrow(NotFoundResponse::new);
        ctx.json(field);
    }

    private void deleteField(Context ctx) {
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
        var result = entries.stream()
                .map(entry -> {
                    var values = service.findEntryValues(entry.id());
                    double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
                    return new EntryWithScore(entry, values, score);
                })
                .toList();
        ctx.json(result);
    }

    private void createEntry(Context ctx) {
        int listId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(EntryRequest.class);
        var entry = service.createEntry(
                listId,
                request.firstname(),
                request.lastname() != null ? request.lastname() : "",
                request.parentName() != null ? request.parentName() : "",
                request.email(),
                request.values() != null ? request.values() : Map.of(),
                request.notes());
        ctx.status(HttpStatus.CREATED).json(entry);
    }

    private void updateEntry(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        var request = ctx.bodyAsClass(EntryRequest.class);
        service.updateEntry(
                entryId,
                request.firstname(),
                request.lastname() != null ? request.lastname() : "",
                request.parentName(),
                request.email(),
                request.notes(),
                request.values());
        var updated = service.findEntryById(entryId).orElseThrow(NotFoundResponse::new);
        ctx.json(updated);
    }

    private void updateCreatedAt(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        var request = ctx.bodyAsClass(CreatedAtRequest.class);
        service.updateCreatedAt(entryId, request.createdAt());
        var updated = service.findEntryById(entryId).orElseThrow(NotFoundResponse::new);
        ctx.json(updated);
    }

    private void deleteEntry(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        service.deleteEntry(entryId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // --- State transitions ---

    private void inviteEntry(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.inviteEntry(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void moveToTesting(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.moveToTesting(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void moveToJoined(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.moveToJoined(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void withdrawEntry(Context ctx) {
        int entryId = ctx.pathParamAsClass("entryId", Integer.class).get();
        try {
            var entry = service.withdrawEntry(entryId);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            throw new NotFoundResponse(e.getMessage());
        } catch (IllegalStateException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void validateFormula(String formula, List<String> fieldNames) {
        if (formula == null || formula.isBlank()) return;
        try {
            ScoreEvaluator.validate(formula, fieldNames);
        } catch (IllegalArgumentException e) {
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
            List<WaitingListEntryValue> values) {}

    public record ListRequest(
            String name,
            String description,
            String scoringFormula,
            Integer confirmIntervalDays,
            Integer testingGroupId,
            Integer joinGroupId,
            Integer joinRoleId,
            Integer attendanceThreshold) {}

    @OpenApiName("WaitingListListWithCount")
    public record ListWithCount(WaitingList list, int entryCount) {}

    public record FieldRequest(String name, String fieldType, String config, int position, boolean required) {}

    public record VisibleFieldsRequest(List<Integer> fieldIds) {}

    public record InviteRequest(Integer maxUses, String expiresAt) {}

    public record EntryRequest(
            String firstname,
            String lastname,
            String parentName,
            String email,
            Map<Integer, String> values,
            String notes) {}

    public record CreatedAtRequest(Instant createdAt) {}

    public record InviteInfoResponse(String listName, String listDescription, List<WaitingListField> fields) {}

    @OpenApiName("WaitingListEntryWithScore")
    public record EntryWithScore(WaitingListEntry entry, List<WaitingListEntryValue> values, double score) {}
}
