/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListRateLimiter;
import dev.chojo.ember.feature.waitinglist.service.ScoreEvaluator;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.util.ClientIp;
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
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

@Singleton
public class WaitingListRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(WaitingListRoutes.class);

    private final WaitingListService service;
    private final StationRepository stationRepository;
    private final ConsentService consentService;
    private final PublicWaitingListRateLimiter rateLimiter;
    private final Network network;

    @Inject
    public WaitingListRoutes(
            WaitingListService service,
            StationRepository stationRepository,
            ConsentService consentService,
            PublicWaitingListRateLimiter rateLimiter,
            Network network) {
        this.service = service;
        this.stationRepository = stationRepository;
        this.consentService = consentService;
        this.rateLimiter = rateLimiter;
        this.network = network;
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

    private static List<GuardianInput> resolveGuardians(
            List<GuardianRequest> guardians, String parentName, String email) {
        if (guardians != null && !guardians.isEmpty()) {
            return guardians.stream()
                    .map(g -> new GuardianInput(
                            g.firstname() != null ? g.firstname() : "",
                            g.lastname() != null ? g.lastname() : "",
                            g.email() != null ? g.email() : "",
                            g.phone() != null ? g.phone() : ""))
                    .toList();
        }
        if ((parentName != null && !parentName.isBlank()) || (email != null && !email.isBlank())) {
            return List.of(new GuardianInput(parentName != null ? parentName : "", "", email != null ? email : "", ""));
        }
        return List.of();
    }

    // --- Public ---

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Public endpoints
        routes.get(prefix + "/public/waiting-list/invite/{code}", this::getInviteInfo);
        routes.post(prefix + "/public/waiting-list/register", this::registerViaInvite);
        routes.get(prefix + "/public/waiting-list/entry/{token}", this::getEntryByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/remove", this::removeByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/confirm", this::confirmInterest);

        // Public waitlist registration
        routes.get(prefix + "/public/station/{stationUid}/waitlists", this::listPublicWaitlists);
        routes.get(prefix + "/public/station/{stationUid}/waitlists/{wid}/form", this::getPublicForm);
        routes.post(prefix + "/public/station/{stationUid}/waitlists/{wid}/register", this::submitPublicRegistration);
        routes.get(prefix + "/public/waitlist/verify/{token}", this::verifyPublicEmail);

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

        // Approve/reject pending entries
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/approve",
                this::approveEntry,
                StationPermission.WAITLIST_EDIT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/reject",
                this::rejectEntry,
                StationPermission.WAITLIST_EDIT);
    }

    private void verifyListOwnership(Context ctx, int listId) {
        requireOwnedOrNotFound(ctx, listId, service::findById, WaitingList::stationId);
    }

    /**
     * Asserts the given field belongs to the given list, so a field id from another list cannot
     * be edited or deleted by pairing it with an owned list id.
     */
    private void verifyFieldInList(int listId, int fieldId) {
        if (service.findFieldsByList(listId).stream().noneMatch(f -> f.id() == fieldId)) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Asserts the given invite belongs to the given list.
     */
    private void verifyInviteInList(int listId, int inviteId) {
        if (service.findInvitesByList(listId).stream().noneMatch(i -> i.id() == inviteId)) {
            throw new NotFoundResponse();
        }
    }

    /**
     * Asserts the given entry belongs to the given list.
     */
    private void verifyEntryInList(int listId, int entryId) {
        var entry = service.findEntryById(entryId).orElseThrow(NotFoundResponse::new);
        if (entry.listId() != listId) {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/invite/{code}",
            methods = HttpMethod.GET,
            summary = "Get invite info and fields for registration",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "code", required = true))
    @StationFree("an invite code names the list it belongs to; whoever holds it is meant to see that form")
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
        var retryAfter = rateLimiter.tryAcquire(ClientIp.resolve(ctx, network).getHostAddress(), request.inviteCode());
        if (retryAfter.isPresent()) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter.get()))
                    .json(new ErrorResponseWrapper("Rate limit exceeded"));
            return;
        }
        var consent = consentService.requireAcceptance(
                ctx, request.consentVersion(), request.privacyVersion(), request.tosVersion());
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        try {
            var entry = service.registerViaInvite(
                    request.inviteCode(),
                    request.firstname(),
                    request.lastname() != null ? request.lastname() : "",
                    guardians,
                    request.values() != null ? request.values() : Map.of(),
                    request.notes(),
                    consent);
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
    @StationFree("the entry token is what a family holds instead of a login, and it names one entry")
    private void getEntryByToken(Context ctx) {
        String token = ctx.pathParam("token");
        var entry = service.findEntryByToken(token).orElseThrow(NotFoundResponse::new);
        var values = service.findEntryValues(entry.id());
        var guardians = service.findGuardiansByEntry(entry.id());
        var list = service.findById(entry.listId()).orElseThrow(NotFoundResponse::new);
        var fields = service.findFieldsByList(entry.listId());
        int position = service.findWaitingPositionByScore(entry);
        ctx.json(new PublicStatusResponse(
                entry.firstname(),
                entry.lastname(),
                entry.parentName(),
                entry.email(),
                entry.status(),
                entry.confirmedAt().toString(),
                entry.createdAt().toString(),
                list.confirmIntervalDays(),
                position,
                list.name(),
                fields,
                values,
                guardians));
    }

    // --- Management ---

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}/remove",
            methods = HttpMethod.POST,
            summary = "Remove self from waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true))
    @StationFree("the same token, used to withdraw the entry it names")
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
    @StationFree("the same token, used to confirm the entry it names is still wanted")
    private void confirmInterest(Context ctx) {
        String token = ctx.pathParam("token");
        service.confirmInterest(token);
        ctx.status(HttpStatus.NO_CONTENT);
    }

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
                request.attendanceThreshold() != null ? request.attendanceThreshold() : 5,
                request.isPublic() != null && request.isPublic(),
                request.minAgeRegister(),
                request.minAgeJoin());
        ctx.status(HttpStatus.CREATED).json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.GET,
            summary = "Get waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true))
    private void getById(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyListOwnership(ctx, id);
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
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, service::findById, WaitingList::stationId);
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
                        request.attendanceThreshold() != null ? request.attendanceThreshold() : 5,
                        request.isPublic() != null && request.isPublic(),
                        request.minAgeRegister(),
                        request.minAgeJoin())
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
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, service::findById, WaitingList::stationId);
        service.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // --- Fields ---

    private void updateVisibleFields(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyListOwnership(ctx, id);
        var request = ctx.bodyAsClass(VisibleFieldsRequest.class);
        var list = service.updateVisibleFields(id, toJson(request.fieldIds())).orElseThrow(NotFoundResponse::new);
        ctx.json(list);
    }

    private void listFields(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        ctx.json(service.findFieldsByList(listId));
    }

    private void createField(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        var request = ctx.bodyAsClass(FieldRequest.class);
        var field = service.createField(
                listId,
                request.name(),
                request.fieldType(),
                request.config() != null ? request.config() : WaitingListFieldConfig.EMPTY,
                request.position(),
                request.required(),
                request.isPublic() == null || request.isPublic());
        ctx.status(HttpStatus.CREATED).json(field);
    }

    private void updateField(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int fieldId = pathInt(ctx, "fieldId");
        verifyFieldInList(listId, fieldId);
        var request = ctx.bodyAsClass(FieldRequest.class);
        var field = service.updateField(
                        fieldId,
                        request.name(),
                        request.fieldType(),
                        request.config() != null ? request.config() : WaitingListFieldConfig.EMPTY,
                        request.position(),
                        request.required(),
                        request.isPublic() == null || request.isPublic())
                .orElseThrow(NotFoundResponse::new);
        ctx.json(field);
    }

    // --- Invites ---

    private void deleteField(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int fieldId = pathInt(ctx, "fieldId");
        verifyFieldInList(listId, fieldId);
        service.deleteField(fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listInvites(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        ctx.json(service.findInvitesByList(listId));
    }

    private void createInvite(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
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

    // --- Entries ---

    private void deleteInvite(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int inviteId = pathInt(ctx, "inviteId");
        verifyInviteInList(listId, inviteId);
        service.deleteInvite(inviteId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listEntries(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        var entries = service.findEntriesByList(listId);
        var list = service.findById(listId).orElseThrow(NotFoundResponse::new);
        var fields = service.findFieldsByList(listId);
        var allGuardians = service.findGuardiansByList(listId);
        var guardianMap = new HashMap<Integer, List<WaitingListEntryGuardian>>();
        for (var g : allGuardians) {
            guardianMap.computeIfAbsent(g.entryId(), _ -> new ArrayList<>()).add(g);
        }
        var result = entries.stream()
                .map(entry -> {
                    var values = service.findEntryValues(entry.id());
                    double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
                    var entryGuardians = guardianMap.getOrDefault(entry.id(), List.of());
                    var age = service.ageOf(listId, values);
                    return new EntryWithScore(
                            entry, values, score, entryGuardians, age.orElse(null), service.belowJoinAge(list, age));
                })
                .toList();
        ctx.json(result);
    }

    private void createEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
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
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
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
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        var request = ctx.bodyAsClass(CreatedAtRequest.class);
        service.updateCreatedAt(entryId, request.createdAt());
        var updated = service.findEntryById(entryId).orElseThrow(NotFoundResponse::new);
        ctx.json(updated);
    }

    // --- State transitions ---

    private void deleteEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        service.deleteEntry(entryId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void inviteEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
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
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
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
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
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
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
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

    // --- Records ---

    private void validateFormula(String formula, List<String> fieldNames) {
        if (formula == null || formula.isBlank()) return;
        try {
            ScoreEvaluator.validate(formula, fieldNames);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scoring formula: {}", formula, e);
            throw new BadRequestResponse("Invalid formula: " + e.getMessage());
        }
    }

    private int resolveStation(Context ctx) {
        String stationUid = ctx.pathParam("stationUid");
        var station = stationRepository
                .findBySlug(stationUid)
                .or(() -> {
                    try {
                        return stationRepository.findByUid(UUID.fromString(stationUid));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(NotFoundResponse::new);
        if (!station.publicWaitlistEnabled()) throw new NotFoundResponse();
        return station.id();
    }

    private void listPublicWaitlists(Context ctx) {
        int stationId = resolveStation(ctx);
        var lists = service.findPublicByStation(stationId);
        ctx.json(lists.stream()
                .map(l -> new PublicWaitlistSummary(l.id(), l.name(), l.description()))
                .toList());
    }

    private void getPublicForm(Context ctx) {
        int stationId = resolveStation(ctx);
        int wid = pathInt(ctx, "wid");
        var list = service.findById(wid).orElseThrow(NotFoundResponse::new);
        if (list.stationId() != stationId || !list.isPublic()) throw new NotFoundResponse();
        var fields = service.findPublicFieldsByList(wid);
        ctx.json(new PublicFormResponse(list.name(), list.description(), fields));
    }

    private void submitPublicRegistration(Context ctx) {
        int stationId = resolveStation(ctx);
        int wid = pathInt(ctx, "wid");
        var list = service.findById(wid).orElseThrow(NotFoundResponse::new);
        if (list.stationId() != stationId || !list.isPublic()) throw new NotFoundResponse();
        var request = ctx.bodyAsClass(PublicRegistrationRequest.class);
        if (request.firstname() == null || request.firstname().isBlank()) {
            throw new BadRequestResponse("firstname is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestResponse("email is required");
        }
        var retryAfter = rateLimiter.tryAcquire(ClientIp.resolve(ctx, network).getHostAddress(), "list:" + wid);
        if (retryAfter.isPresent()) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter.get()))
                    .json(new ErrorResponseWrapper("Rate limit exceeded"));
            return;
        }
        service.requireOldEnoughToRegister(list, request.values() != null ? request.values() : Map.of());
        var consent = consentService.requireAcceptance(
                ctx, request.consentVersion(), request.privacyVersion(), request.tosVersion());
        var guardianInputs = request.guardians() != null
                ? request.guardians().stream()
                        .map(g -> new GuardianInput(
                                g.firstname() != null ? g.firstname() : "",
                                g.lastname() != null ? g.lastname() : "",
                                g.email() != null ? g.email() : "",
                                g.phone() != null ? g.phone() : ""))
                        .toList()
                : List.<GuardianInput>of();
        service.submitPublicRegistration(
                wid,
                request.firstname(),
                request.lastname() != null ? request.lastname() : "",
                request.email(),
                guardianInputs,
                request.values() != null ? request.values() : Map.of(),
                request.notes(),
                consent);
        ctx.status(HttpStatus.ACCEPTED).json(new StatusResponse("verification_email_sent"));
    }

    @StationFree("the verification token is mailed to the address it confirms and names one registration")
    private void verifyPublicEmail(Context ctx) {
        String token = ctx.pathParam("token");
        boolean success = service.verifyPublicRegistration(token);
        if (!success) {
            throw new BadRequestResponse("Invalid or expired verification token");
        }
        ctx.json(new StatusResponse("verified"));
    }

    private void approveEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        var entry = service.approvePendingEntry(entryId);
        ctx.json(entry);
    }

    private void rejectEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        service.rejectPendingEntry(entryId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApiName("WaitingListRegisterRequest")
    public record RegisterRequest(
            String inviteCode,
            String firstname,
            String lastname,
            String parentName,
            String email,
            List<GuardianRequest> guardians,
            Map<Integer, JsonNode> values,
            String notes,
            String consentVersion,
            String privacyVersion,
            String tosVersion) {}

    public record PublicEntryResponse(String accessToken) {}

    @OpenApiName("WaitingListPublicStatusResponse")
    public record PublicStatusResponse(
            String firstname,
            String lastname,
            String parentName,
            String email,
            WaitingListEntryStatus status,
            String confirmedAt,
            String createdAt,
            int confirmIntervalDays,
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
            Integer attendanceThreshold,
            Boolean isPublic,
            Integer minAgeRegister,
            Integer minAgeJoin) {}

    @OpenApiName("WaitingListListWithCount")
    public record ListWithCount(WaitingList list, int entryCount) {}

    /**
     * @param config the field's settings as an object, the same shape the field is read back in.
     *               It used to be JSON text on the way in and an object on the way out, and the
     *               two halves of that never agreed.
     */
    public record FieldRequest(
            String name,
            WaitingListFieldType fieldType,
            WaitingListFieldConfig config,
            int position,
            boolean required,
            Boolean isPublic) {}

    // --- Public waitlist routes ---

    public record VisibleFieldsRequest(List<Integer> fieldIds) {}

    public record InviteRequest(Integer maxUses, String expiresAt) {}

    public record EntryRequest(
            String firstname,
            String lastname,
            String parentName,
            String email,
            List<GuardianRequest> guardians,
            Map<Integer, JsonNode> values,
            String notes) {}

    public record CreatedAtRequest(Instant createdAt) {}

    public record InviteInfoResponse(String listName, String listDescription, List<WaitingListField> fields) {}

    /**
     * @param age          how old they are today, from the birth date field; null when the list has
     *                     none or the entry left it unanswered
     * @param belowJoinAge whether they are waiting for their age rather than for their turn
     */
    @OpenApiName("WaitingListEntryWithScore")
    public record EntryWithScore(
            WaitingListEntry entry,
            List<WaitingListEntryValue> values,
            double score,
            List<WaitingListEntryGuardian> guardians,
            Integer age,
            boolean belowJoinAge) {}

    public record GuardianRequest(String firstname, String lastname, String email, String phone) {}

    private record StatusResponse(String status) {}

    public record PublicWaitlistSummary(int id, String name, String description) {}

    public record PublicFormResponse(String listName, String listDescription, List<WaitingListField> fields) {}

    public record PublicRegistrationRequest(
            String firstname,
            String lastname,
            String email,
            List<GuardianRequest> guardians,
            Map<Integer, JsonNode> values,
            String notes,
            String consentVersion,
            String privacyVersion,
            String tosVersion) {}
}
