/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListAnswer;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryGuardian;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListField;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvite;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListRateLimiter;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListService;
import dev.chojo.ember.feature.waitinglist.service.ScoreEvaluator;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

@Singleton
public class WaitingListRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(WaitingListRoutes.class);

    private final WaitingListService service;
    private final PublicWaitingListService publicLists;
    private final ConsentService consentService;
    private final PublicWaitingListRateLimiter rateLimiter;
    private final EventCrudService eventCrudService;
    private final EventRestrictionService eventRestrictionService;

    @Inject
    public WaitingListRoutes(
            WaitingListService service,
            PublicWaitingListService publicLists,
            ConsentService consentService,
            PublicWaitingListRateLimiter rateLimiter,
            EventCrudService eventCrudService,
            EventRestrictionService eventRestrictionService) {
        this.service = service;
        this.publicLists = publicLists;
        this.consentService = consentService;
        this.rateLimiter = rateLimiter;
        this.eventCrudService = eventCrudService;
        this.eventRestrictionService = eventRestrictionService;
    }

    private static String toJson(@Nullable List<Integer> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < fieldIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(fieldIds.get(i));
        }
        return sb.append("]").toString();
    }

    private static List<GuardianInput> resolveGuardians(
            @Nullable List<WaitingListGuardianRequest> guardians, @Nullable String parentName, @Nullable String email) {
        if (guardians != null && !guardians.isEmpty()) {
            return guardians.stream().map(WaitingListRoutes::guardianInput).toList();
        }
        if ((parentName != null && !parentName.isBlank()) || (email != null && !email.isBlank())) {
            return List.of(new GuardianInput(parentName != null ? parentName : "", "", email != null ? email : "", ""));
        }
        return List.of();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/waiting-list/invite/{code}", this::getInviteInfo);
        routes.post(prefix + "/public/waiting-list/register", this::registerViaInvite);
        routes.get(prefix + "/public/waiting-list/entry/{token}", this::getEntryByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/remove", this::removeByToken);
        routes.post(prefix + "/public/waiting-list/entry/{token}/confirm", this::confirmInterest);
        routes.post(prefix + "/public/waiting-list/entry/{token}/answer", this::answerInvitation);

        routes.get(prefix + "/public/station/{stationUid}/waitlists", this::listPublicWaitlists);
        routes.get(prefix + "/public/station/{stationUid}/waitlists/{wid}/form", this::getPublicForm);
        routes.post(prefix + "/public/station/{stationUid}/waitlists/{wid}/register", this::submitPublicRegistration);
        routes.get(prefix + "/public/waitlist/verify/{token}", this::verifyPublicEmail);

        routes.get(prefix + "/waiting-lists", this::listAll, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}", this::getById, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}/fields", this::listFields, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}/invites", this::listInvites, StationPermission.WAITLIST_READ);
        routes.get(prefix + "/waiting-lists/{id}/entries", this::listEntries, StationPermission.WAITLIST_READ);

        routes.post(prefix + "/waiting-lists/{id}/entries", this::createEntry, StationPermission.WAITLIST_ADD);

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

        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/invite",
                this::inviteEntry,
                StationPermission.WAITLIST_EDIT);
        routes.post(
                prefix + "/waiting-lists/{id}/entries/{entryId}/back-to-waiting",
                this::returnToWaiting,
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
            throw Refusal.WAITING_LIST_FIELD_NOT_IN_LIST.raise();
        }
    }

    /**
     * Asserts the given invite belongs to the given list.
     */
    private void verifyInviteInList(int listId, int inviteId) {
        if (service.findInvitesByList(listId).stream().noneMatch(i -> i.id() == inviteId)) {
            throw Refusal.WAITING_LIST_INVITE_NOT_IN_LIST.raise();
        }
    }

    /**
     * Asserts the given entry belongs to the given list.
     */
    private void verifyEntryInList(int listId, int entryId) {
        var entry = service.findEntryById(entryId).orElseThrow(Refusal.WAITING_LIST_ENTRY_NOT_IN_LIST::raise);
        if (entry.listId() != listId) {
            throw Refusal.WAITING_LIST_ENTRY_NOT_IN_LIST.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/invite/{code}",
            methods = HttpMethod.GET,
            summary = "Get invite info and fields for registration",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "code", required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListInviteInfo.class)))
    @StationFree("an invite code names the list it belongs to; whoever holds it is meant to see that form")
    private void getInviteInfo(Context ctx) {
        String code = ctx.pathParam("code");
        var invite = service.findInviteByCode(code).orElseThrow(Refusal.WAITING_LIST_INVITE_UNKNOWN::raise);
        if (!invite.hasUsesLeft() || invite.isExpired()) {
            throw Refusal.WAITING_LIST_INVITE_NO_LONGER_VALID.raise();
        }
        var list = service.findById(invite.listId()).orElseThrow(Refusal.WAITING_LIST_NOT_HERE_BEHIND_INVITE::raise);
        var fields = service.findFieldsByList(invite.listId());
        ctx.json(new WaitingListInviteInfo(list.name(), list.description(), fields));
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/register",
            methods = HttpMethod.POST,
            summary = "Register on waiting list via invite code",
            tags = {"Waiting List"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListRegisterRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = WaitingListAccessResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void registerViaInvite(Context ctx) {
        var request = ctx.bodyAsClass(WaitingListRegisterRequest.class);
        String inviteCode = request.inviteCode();
        String firstname = request.firstname();
        if (inviteCode == null || firstname == null) {
            throw Refusal.WAITING_LIST_REGISTRATION_INCOMPLETE.raise();
        }
        if (answerWhenLimited(ctx, rateLimiter.tryAcquire(ctx.ip(), inviteCode))) {
            return;
        }
        var consent = consentService.requireAcceptance(
                ctx, request.consentVersion(), request.privacyVersion(), request.tosVersion());
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        try {
            var entry = service.registerViaInvite(
                    inviteCode,
                    firstname,
                    Objects.requireNonNullElse(request.lastname(), ""),
                    guardians,
                    Objects.requireNonNullElse(request.values(), Map.of()),
                    request.notes(),
                    consent);
            ctx.status(HttpStatus.CREATED).json(new WaitingListAccessResponse(entry.accessToken()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument registering via waiting list invite", e);
            throw Refusal.WAITING_LIST_REGISTRATION_REFUSED.raise();
        } catch (IllegalStateException e) {
            log.warn("Invalid state registering via waiting list invite", e);
            throw Refusal.WAITING_LIST_CLOSED_TO_THIS_REGISTRATION.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}",
            methods = HttpMethod.GET,
            summary = "View waiting list entry by access token",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListPublicStatus.class)))
    @StationFree("the entry token is what a family holds instead of a login, and it names one entry")
    private void getEntryByToken(Context ctx) {
        String token = ctx.pathParam("token");
        if (readRateLimited(ctx)) return;
        var entry = service.findEntryByToken(token).orElseThrow(Refusal.WAITING_LIST_ENTRY_TOKEN_UNKNOWN::raise);
        var values = service.findEntryValues(entry.id());
        var guardians = service.findGuardiansByEntry(entry.id());
        var list = service.findById(entry.listId()).orElseThrow(Refusal.WAITING_LIST_NOT_HERE_BEHIND_ENTRY::raise);
        var fields = service.findFieldsByList(entry.listId());
        int position = service.findWaitingPositionByScore(entry);
        ctx.json(new WaitingListPublicStatus(
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
                guardians,
                describeInvitation(list.stationId(), entry),
                describeAnswer(entry)));
    }

    /**
     * The appointment the page is about, written exactly as the mail wrote it.
     *
     * <p>The page is the invitation rather than a window into the station: somebody holding the link
     * was deliberately invited, and an answer given without knowing the occasion is not an answer
     * worth collecting.
     */
    private @Nullable WaitingListPublicInvitation describeInvitation(int stationId, WaitingListEntry entry) {
        var invitation = entry.invitation();
        if (invitation == null) return null;
        var details = publicLists.invitationDetails(stationId, invitation);
        return new WaitingListPublicInvitation(
                invitation.eventId(),
                invitation.date().toString(),
                details.appointmentName(),
                details.date(),
                details.time(),
                details.arrivalTime(),
                details.location());
    }

    private static @Nullable WaitingListPublicAnswer describeAnswer(WaitingListEntry entry) {
        var answer = entry.answer();
        if (answer == null) return null;
        return new WaitingListPublicAnswer(answer.answer(), answer.answeredAt().toString(), answer.note());
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}/answer",
            methods = HttpMethod.POST,
            summary = "Answer the invitation the entry currently holds",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true),
            requestBody =
                    @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListInvitationAnswerRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    @StationFree("the same token, used to answer the invitation the entry it names is holding")
    private void answerInvitation(Context ctx) {
        String token = ctx.pathParam("token");
        if (rateLimited(ctx, token)) return;
        var request = ctx.bodyAsClass(WaitingListInvitationAnswerRequest.class);
        String answerName = request.answer();
        if (answerName == null) {
            throw Refusal.WAITING_LIST_ANSWER_MISSING.raise();
        }
        WaitingListAnswer answer;
        try {
            answer = WaitingListAnswer.valueOf(answerName);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown waiting-list invitation answer {}", answerName, e);
            throw Refusal.WAITING_LIST_ANSWER_UNKNOWN.raise(answerName);
        }
        try {
            service.answerInvitation(
                    token, request.eventId(), parseOptionalDate(request.date()), answer, request.note());
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            log.warn("No waiting-list entry for the token answering an invitation", e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_HERE_ON_ANSWER.raise();
        }
    }

    /**
     * Answers the request itself when the caller has asked too often, and says so.
     *
     * <p>The entry endpoints are keyed by the token rather than by the list, because the token is
     * what a guess would have to hit and it now returns something worth guessing at.
     *
     * @return whether the request has been answered and the handler should stop
     */
    private boolean rateLimited(Context ctx, String token) {
        return answerWhenLimited(ctx, rateLimiter.tryAcquire(ctx.ip(), "entry:" + token));
    }

    /**
     * Answers the request itself when the caller has read entry pages too often, and says so.
     *
     * <p>Reading is held apart from registering. The status page is a link a family opens again
     * whenever they wonder where they stand, and behind a shared connection one address speaks for
     * many families; spending the registration allowance on those visits took from a family the
     * ability to sign up at all.
     *
     * @return whether the request has been answered and the handler should stop
     */
    private boolean readRateLimited(Context ctx) {
        return answerWhenLimited(ctx, rateLimiter.tryAcquireRead(ctx.ip()));
    }

    /**
     * Answers a caller who is asking faster than the list allows, saying so and when to come back.
     *
     * @return whether the request was answered here and the handler should go no further
     */
    private boolean answerWhenLimited(Context ctx, Optional<Long> retryAfter) {
        if (retryAfter.isEmpty()) return false;
        ctx.status(Refusal.WAITING_LIST_TOO_OFTEN.status())
                .header("Retry-After", String.valueOf(retryAfter.get()))
                .json(ErrorResponseWrapper.of(
                        Refusal.WAITING_LIST_TOO_OFTEN, Refusal.WAITING_LIST_TOO_OFTEN.message(), retryAfter.get()));
        return true;
    }

    @OpenApi(
            path = "/api/v1/public/waiting-list/entry/{token}/remove",
            methods = HttpMethod.POST,
            summary = "Remove self from waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = @OpenApiResponse(status = "204"))
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
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = @OpenApiResponse(status = "204"))
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
            tags = {"Waiting List"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListWithCount[].class)))
    private void listAll(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var lists = service.findByStation(session.member().stationId());
        ctx.json(lists.stream()
                .map(l -> new WaitingListWithCount(l, service.countEntries(l.id())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/waiting-lists",
            methods = HttpMethod.POST,
            summary = "Create waiting list",
            tags = {"Waiting List"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = WaitingList.class)))
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(WaitingListRequest.class);
        validateFormula(request.scoringFormula(), List.of());
        var list = service.create(
                session.member().stationId(),
                request.name(),
                Objects.requireNonNullElse(request.description(), ""),
                request.scoringFormula(),
                Objects.requireNonNullElse(request.confirmIntervalDays(), 180),
                request.testingGroupId(),
                request.joinGroupId(),
                Objects.requireNonNullElse(request.attendanceThreshold(), 5),
                Boolean.TRUE.equals(request.isPublic()),
                !Boolean.FALSE.equals(request.sendsMail()),
                request.minAgeRegister(),
                request.minAgeJoin());
        ctx.status(HttpStatus.CREATED).json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.GET,
            summary = "Get waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingList.class)))
    private void getById(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyListOwnership(ctx, id);
        var list = service.findById(id).orElseThrow(Refusal.WAITING_LIST_NOT_HERE::raise);
        ctx.json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.PUT,
            summary = "Update waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingList.class)))
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, service::findById, WaitingList::stationId);
        var request = ctx.bodyAsClass(WaitingListRequest.class);
        var fieldNames = service.findFieldsByList(id).stream()
                .map(WaitingListField::name)
                .toList();
        validateFormula(request.scoringFormula(), fieldNames);
        var updated = service.update(
                        id,
                        request.name(),
                        Objects.requireNonNullElse(request.description(), ""),
                        request.scoringFormula(),
                        Objects.requireNonNullElse(request.confirmIntervalDays(), 180),
                        request.testingGroupId(),
                        request.joinGroupId(),
                        Objects.requireNonNullElse(request.attendanceThreshold(), 5),
                        Boolean.TRUE.equals(request.isPublic()),
                        !Boolean.FALSE.equals(request.sendsMail()),
                        request.minAgeRegister(),
                        request.minAgeJoin())
                .orElseThrow(Refusal.WAITING_LIST_NOT_CHANGED::raise);
        ctx.json(updated);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete waiting list",
            tags = {"Waiting List"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteList(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, service::findById, WaitingList::stationId);
        service.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/visible-fields",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListVisibleFieldsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingList.class)))
    private void updateVisibleFields(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyListOwnership(ctx, id);
        var request = ctx.bodyAsClass(WaitingListVisibleFieldsRequest.class);
        var list = service.updateVisibleFields(id, toJson(request.fieldIds()))
                .orElseThrow(Refusal.WAITING_LIST_NOT_HERE_ON_VISIBLE_QUESTIONS::raise);
        ctx.json(list);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/fields",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListField[].class)))
    private void listFields(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        ctx.json(service.findFieldsByList(listId));
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/fields",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListFieldRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = WaitingListField.class)))
    private void createField(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        var request = ctx.bodyAsClass(WaitingListFieldRequest.class);
        var field = service.createField(
                listId,
                request.name(),
                request.fieldType(),
                Objects.requireNonNullElse(request.config(), WaitingListFieldConfig.EMPTY),
                request.position(),
                request.required(),
                !Boolean.FALSE.equals(request.isPublic()));
        ctx.status(HttpStatus.CREATED).json(field);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/fields/{fieldId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListFieldRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListField.class)))
    private void updateField(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int fieldId = pathInt(ctx, "fieldId");
        verifyFieldInList(listId, fieldId);
        var request = ctx.bodyAsClass(WaitingListFieldRequest.class);
        var field = service.updateField(
                        fieldId,
                        request.name(),
                        request.fieldType(),
                        Objects.requireNonNullElse(request.config(), WaitingListFieldConfig.EMPTY),
                        request.position(),
                        request.required(),
                        !Boolean.FALSE.equals(request.isPublic()))
                .orElseThrow(Refusal.WAITING_LIST_FIELD_NOT_CHANGED::raise);
        ctx.json(field);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteField(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int fieldId = pathInt(ctx, "fieldId");
        verifyFieldInList(listId, fieldId);
        service.deleteField(fieldId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/invites",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListInvite[].class)))
    private void listInvites(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        ctx.json(service.findInvitesByList(listId));
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/invites",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListInviteRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = WaitingListInvite.class)))
    private void createInvite(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        var request = ctx.bodyAsClass(WaitingListInviteRequest.class);
        Instant expiresAt = null;
        String requestedExpiry = request.expiresAt();
        if (requestedExpiry != null && !requestedExpiry.isBlank()) {
            try {
                expiresAt = Instant.parse(requestedExpiry);
            } catch (Exception e) {
                expiresAt = lastSecondOfDateUtc(requestedExpiry);
            }
        }
        var invite = service.createInvite(listId, Objects.requireNonNullElse(request.maxUses(), 1), expiresAt);
        ctx.status(HttpStatus.CREATED).json(invite);
    }

    /** The last second, in UTC, of a plain date such as {@code 2026-05-30}. */
    private static Instant lastSecondOfDateUtc(String isoDate) {
        return LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(86399);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/invites/{inviteId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteInvite(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int inviteId = pathInt(ctx, "inviteId");
        verifyInviteInList(listId, inviteId);
        service.deleteInvite(inviteId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = WaitingListEntryWithScore[].class)))
    private void listEntries(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        var entries = service.findEntriesByList(listId);
        var list = service.findById(listId).orElseThrow(Refusal.WAITING_LIST_NOT_HERE_ON_ENTRIES::raise);
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
                    return new WaitingListEntryWithScore(
                            entry, values, score, entryGuardians, age.orElse(null), service.belowJoinAge(list, age));
                })
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListEntryRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = WaitingListEntry.class)))
    private void createEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        var request = ctx.bodyAsClass(WaitingListEntryRequest.class);
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        var entry = service.createEntry(
                listId,
                request.firstname(),
                Objects.requireNonNullElse(request.lastname(), ""),
                guardians,
                Objects.requireNonNullElse(request.values(), Map.of()),
                request.notes());
        ctx.status(HttpStatus.CREATED).json(entry);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListEntryRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
    private void updateEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        var request = ctx.bodyAsClass(WaitingListEntryRequest.class);
        var guardians = resolveGuardians(request.guardians(), request.parentName(), request.email());
        service.updateEntry(
                entryId,
                request.firstname(),
                Objects.requireNonNullElse(request.lastname(), ""),
                guardians,
                request.notes(),
                request.values());
        var updated =
                service.findEntryById(entryId).orElseThrow(Refusal.WAITING_LIST_ENTRY_NOT_HERE_AFTER_CHANGE::raise);
        ctx.json(updated);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/created-at",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListCreatedAtRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
    private void updateCreatedAt(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        var request = ctx.bodyAsClass(WaitingListCreatedAtRequest.class);
        service.updateCreatedAt(entryId, request.createdAt());
        var updated = service.findEntryById(entryId)
                .orElseThrow(Refusal.WAITING_LIST_ENTRY_NOT_HERE_AFTER_DATE_CHANGE::raise);
        ctx.json(updated);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        service.deleteEntry(entryId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/invite",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WaitingListInvitationRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
    private void inviteEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        var invitation = ctx.body().isBlank()
                ? null
                : resolveInvitation(ctx, ctx.bodyAsClass(WaitingListInvitationRequest.class));
        try {
            var entry = service.inviteEntry(entryId, invitation);
            ctx.json(entry);
        } catch (IllegalArgumentException e) {
            log.warn("Waiting list entry not found for invite, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_HERE_ON_INVITE.raise();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when inviting waiting list entry, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_INVITED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/back-to-waiting",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
    private void returnToWaiting(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        try {
            ctx.json(service.returnToWaiting(entryId));
        } catch (IllegalArgumentException e) {
            log.warn("Waiting list entry not found for return to waiting, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_HERE_ON_RETURN.raise();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when returning waiting list entry to waiting, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_RETURNED.raise();
        }
    }

    /**
     * The appointment an invitation names, checked against the station that is inviting.
     *
     * <p>An appointment repeats, so the date travels with it and an invitation naming an appointment
     * without one is refused rather than silently meaning every occurrence there has ever been.
     */
    private @Nullable WaitingListInvitation resolveInvitation(
            Context ctx, @Nullable WaitingListInvitationRequest request) {
        if (request == null) return null;
        Integer eventId = request.eventId();
        if (eventId == null) return null;
        var session = StationSession.from(ctx);
        var event = eventCrudService
                .findById(eventId)
                .filter(candidate -> candidate.stationId() == session.stationId())
                .orElseThrow(Refusal.APPOINTMENT_NOT_HERE_FOR_INVITATION::raise);
        if (!eventRestrictionService.canView(
                event.id(), session.member().id(), session.user().permissions())) {
            throw Refusal.APPOINTMENT_NOT_YOURS_TO_INVITE_TO.raise();
        }
        return new WaitingListInvitation(event.id(), parseDate(request.date()), parseTime(request.arrivalTime()));
    }

    private static @Nullable LocalDate parseOptionalDate(@Nullable String raw) {
        return raw == null || raw.isBlank() ? null : parseDate(raw);
    }

    private static LocalDate parseDate(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw Refusal.INVITATION_NEEDS_A_DATE.raise();
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException _) {
            throw Refusal.INVITATION_DATE_NOT_A_DATE.raise(raw);
        }
    }

    private static @Nullable LocalTime parseTime(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException _) {
            throw Refusal.INVITATION_TIME_NOT_A_TIME.raise(raw);
        }
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/testing",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
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
            throw Refusal.WAITING_LIST_ENTRY_NOT_HERE_ON_TESTING.raise();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when moving waiting list entry to testing, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_MOVED_TO_TESTING.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/join",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
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
            throw Refusal.WAITING_LIST_ENTRY_NOT_HERE_ON_JOIN.raise();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when moving waiting list entry to joined, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_JOINED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/withdraw",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "204"))
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
            throw Refusal.WAITING_LIST_ENTRY_NOT_HERE_ON_WITHDRAWAL.raise();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when withdrawing waiting list entry, entryId={}", entryId, e);
            throw Refusal.WAITING_LIST_ENTRY_NOT_WITHDRAWN.raise();
        }
    }

    private void validateFormula(@Nullable String formula, List<String> fieldNames) {
        if (formula == null || formula.isBlank()) return;
        try {
            ScoreEvaluator.validate(formula, fieldNames);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scoring formula: {}", formula, e);
            throw Refusal.SCORING_FORMULA_NOT_READ.raise();
        }
    }

    private int resolveStation(Context ctx) {
        return publicLists.stationIdFor(ctx.pathParam("stationUid"));
    }

    private static List<GuardianInput> guardianInputs(PublicWaitlistRegistrationRequest request) {
        List<WaitingListGuardianRequest> guardians = request.guardians();
        if (guardians == null) return List.of();
        return guardians.stream().map(WaitingListRoutes::guardianInput).toList();
    }

    private static GuardianInput guardianInput(WaitingListGuardianRequest guardian) {
        return new GuardianInput(
                Objects.requireNonNullElse(guardian.firstname(), ""),
                Objects.requireNonNullElse(guardian.lastname(), ""),
                Objects.requireNonNullElse(guardian.email(), ""),
                Objects.requireNonNullElse(guardian.phone(), ""));
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/waitlists",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicWaitlistSummary[].class)))
    private void listPublicWaitlists(Context ctx) {
        int stationId = resolveStation(ctx);
        var lists = service.findPublicByStation(stationId);
        ctx.json(lists.stream()
                .map(l -> new PublicWaitlistSummary(l.id(), l.name(), l.description()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/waitlists/{wid}/form",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = PublicWaitlistFormResponse.class)))
    private void getPublicForm(Context ctx) {
        int stationId = resolveStation(ctx);
        int wid = pathInt(ctx, "wid");
        var list = publicLists.publicList(stationId, wid, Refusal.PUBLIC_WAITING_LIST_NOT_HERE);
        var fields = service.findPublicFieldsByList(wid);
        ctx.json(new PublicWaitlistFormResponse(list.name(), list.description(), list.sendsMail(), fields));
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/waitlists/{wid}/register",
            methods = HttpMethod.POST,
            requestBody =
                    @OpenApiRequestBody(content = @OpenApiContent(from = PublicWaitlistRegistrationRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "202",
                            content = @OpenApiContent(from = WaitingListRegistrationStatus.class)))
    private void submitPublicRegistration(Context ctx) {
        int stationId = resolveStation(ctx);
        int wid = pathInt(ctx, "wid");
        var list = publicLists.publicList(stationId, wid, Refusal.PUBLIC_WAITING_LIST_NOT_HERE_ON_REGISTRATION);
        var request = ctx.bodyAsClass(PublicWaitlistRegistrationRequest.class);
        String firstname = request.firstname();
        if (firstname == null || firstname.isBlank()) {
            throw Refusal.PUBLIC_REGISTRATION_NEEDS_A_FIRST_NAME.raise();
        }
        String email = Objects.requireNonNullElse(request.email(), "");
        if (list.sendsMail() && email.isBlank()) {
            throw Refusal.PUBLIC_REGISTRATION_NEEDS_AN_ADDRESS.raise();
        }
        if (answerWhenLimited(ctx, rateLimiter.tryAcquire(ctx.ip(), "list:" + wid))) {
            return;
        }
        service.requireOldEnoughToRegister(list, Objects.requireNonNullElse(request.values(), Map.of()));
        var consent = consentService.requireAcceptance(
                ctx, request.consentVersion(), request.privacyVersion(), request.tosVersion());
        service.submitPublicRegistration(
                wid,
                firstname,
                Objects.requireNonNullElse(request.lastname(), ""),
                email,
                guardianInputs(request),
                Objects.requireNonNullElse(request.values(), Map.of()),
                request.notes(),
                consent);
        ctx.status(HttpStatus.ACCEPTED)
                .json(new WaitingListRegistrationStatus(list.sendsMail() ? "verification_email_sent" : "registered"));
    }

    @OpenApi(
            path = "/api/v1/public/waitlist/verify/{token}",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = WaitingListRegistrationStatus.class)))
    @StationFree("the verification token is mailed to the address it confirms and names one registration")
    private void verifyPublicEmail(Context ctx) {
        String token = ctx.pathParam("token");
        boolean success = service.verifyPublicRegistration(token);
        if (!success) {
            throw Refusal.WAITING_LIST_CONFIRMATION_LINK_UNKNOWN.raise();
        }
        ctx.json(new WaitingListRegistrationStatus("verified"));
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/approve",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WaitingListEntry.class)))
    private void approveEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        var entry = service.approvePendingEntry(entryId);
        ctx.json(entry);
    }

    @OpenApi(
            path = "/api/v1/waiting-lists/{id}/entries/{entryId}/reject",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "204"))
    private void rejectEntry(Context ctx) {
        int listId = pathInt(ctx, "id");
        verifyListOwnership(ctx, listId);
        int entryId = pathInt(ctx, "entryId");
        verifyEntryInList(listId, entryId);
        service.rejectPendingEntry(entryId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record WaitingListRegisterRequest(
            @Nullable String inviteCode,
            @Nullable String firstname,
            @Nullable String lastname,
            @Nullable String parentName,
            @Nullable String email,
            @Nullable List<WaitingListGuardianRequest> guardians,
            @Nullable Map<Integer, JsonNode> values,
            @Nullable String notes,
            @Nullable String consentVersion,
            @Nullable String privacyVersion,
            @Nullable String tosVersion) {}

    /**
     * What registering through an invite hands back: the token the family keeps instead of a login.
     *
     * @param accessToken opens the entry's own page
     */
    public record WaitingListAccessResponse(String accessToken) {}

    public record WaitingListPublicStatus(
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
            List<WaitingListEntryGuardian> guardians,
            @Nullable WaitingListPublicInvitation invitation,
            @Nullable WaitingListPublicAnswer answer) {}

    /**
     * The appointment the entry is invited to, as the page has to show it.
     *
     * @param eventId         the appointment, sent back with the answer so it names what it answers
     * @param date            the one date of it, in {@code YYYY-MM-DD}, sent back for the same reason
     * @param appointmentName what it is called
     * @param appointmentDate the date written out for a reader
     * @param appointmentTime when the appointment itself runs
     * @param arrivalTime     when they were asked to be there, empty when the invitation named no time
     * @param location        where it is, empty when neither the appointment nor the station says
     */
    public record WaitingListPublicInvitation(
            int eventId,
            String date,
            String appointmentName,
            String appointmentDate,
            String appointmentTime,
            String arrivalTime,
            String location) {}

    public record WaitingListPublicAnswer(WaitingListAnswer answer, String answeredAt, String note) {}

    /**
     * An answer to the invitation an entry currently holds.
     *
     * @param eventId the appointment it answers, null when the invitation named none
     * @param date    the one date of it, null for the same
     * @param answer  one of COMING, NOT_INTERESTED or DATE_DOES_NOT_SUIT
     * @param note    anything they wrote alongside it
     */
    public record WaitingListInvitationAnswerRequest(
            @Nullable Integer eventId,
            @Nullable String date,
            @Nullable String answer,
            @Nullable String note) {}

    /**
     * @param sendsMail whether the list writes to the people on it, sending nothing at all where it
     *                  is false. Unanswered reads as true, which is what every list did before the
     *                  setting existed.
     */
    public record WaitingListRequest(
            String name,
            @Nullable String description,
            @Nullable String scoringFormula,
            @Nullable Integer confirmIntervalDays,
            @Nullable Integer testingGroupId,
            @Nullable Integer joinGroupId,
            @Nullable Integer attendanceThreshold,
            @Nullable Boolean isPublic,
            @Nullable Boolean sendsMail,
            @Nullable Integer minAgeRegister,
            @Nullable Integer minAgeJoin) {}

    public record WaitingListWithCount(WaitingList list, int entryCount) {}

    /**
     * @param config the field's settings as an object, the same shape the field is read back in.
     *               It used to be JSON text on the way in and an object on the way out, and the
     *               two halves of that never agreed.
     */
    public record WaitingListFieldRequest(
            String name,
            FieldType fieldType,
            @Nullable WaitingListFieldConfig config,
            int position,
            boolean required,
            @Nullable Boolean isPublic) {}

    public record WaitingListVisibleFieldsRequest(@Nullable List<Integer> fieldIds) {}

    public record WaitingListInviteRequest(
            @Nullable Integer maxUses, @Nullable String expiresAt) {}

    public record WaitingListEntryRequest(
            String firstname,
            @Nullable String lastname,
            @Nullable String parentName,
            @Nullable String email,
            @Nullable List<WaitingListGuardianRequest> guardians,
            @Nullable Map<Integer, JsonNode> values,
            @Nullable String notes) {}

    public record WaitingListCreatedAtRequest(Instant createdAt) {}

    public record WaitingListInviteInfo(String listName, String listDescription, List<WaitingListField> fields) {}

    /**
     * @param age          how old they are today, from the birth date field; null when the list has
     *                     none or the entry left it unanswered
     * @param belowJoinAge whether they are waiting for their age rather than for their turn
     */
    public record WaitingListEntryWithScore(
            WaitingListEntry entry,
            List<WaitingListEntryValue> values,
            double score,
            List<WaitingListEntryGuardian> guardians,
            @Nullable Integer age,
            boolean belowJoinAge) {}

    public record WaitingListGuardianRequest(
            @Nullable String firstname,
            @Nullable String lastname,
            @Nullable String email,
            @Nullable String phone) {}

    /**
     * The appointment an invitation is about.
     *
     * @param eventId     the appointment, or null to invite without naming one
     * @param date        the one date of it, as {@code YYYY-MM-DD}, required whenever an appointment is named
     * @param arrivalTime when they should be there, as {@code HH:MM}, or null to say nothing about it
     */
    public record WaitingListInvitationRequest(
            @Nullable Integer eventId,
            @Nullable String date,
            @Nullable String arrivalTime) {}

    /**
     * Where a public registration or its confirmation stands.
     *
     * @param status {@code verification_email_sent}, {@code registered} or {@code verified}
     */
    public record WaitingListRegistrationStatus(String status) {}

    public record PublicWaitlistSummary(int id, String name, String description) {}

    /**
     * @param sendsMail whether a confirmation link follows the registration, which is what decides
     *                  whether an address is asked for at all
     */
    public record PublicWaitlistFormResponse(
            String listName, String listDescription, boolean sendsMail, List<WaitingListField> fields) {}

    public record PublicWaitlistRegistrationRequest(
            @Nullable String firstname,
            @Nullable String lastname,
            @Nullable String email,
            @Nullable List<WaitingListGuardianRequest> guardians,
            @Nullable Map<Integer, JsonNode> values,
            @Nullable String notes,
            @Nullable String consentVersion,
            @Nullable String privacyVersion,
            @Nullable String tosVersion) {}
}
