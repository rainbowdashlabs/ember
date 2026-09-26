/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldAssignment;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for profile field definition management including CRUD operations,
 * field ordering, member value retrieval, and profile completeness checks.
 */
@Singleton
public class ProfileFieldRoutes implements Routes {

    private final ProfileFieldService profileFieldService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ProfileFieldRoutes(
            ProfileFieldService profileFieldService, StationMemberRepository stationMemberRepository) {
        this.profileFieldService = profileFieldService;
        this.stationMemberRepository = stationMemberRepository;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Returns the caller's station id, rejecting sessions without a resolved station.
     */
    private static int requireStation(UserSession session) {
        Integer stationId = session.stationId();
        if (stationId == null) {
            throw Refusal.NO_STATION_CHOSEN_FOR_PROFILE_QUESTIONS.raise();
        }
        return stationId;
    }

    /**
     * Loads a profile field definition and asserts it belongs to the caller's station.
     * Answers with 404 (rather than 403) when the field is absent or owned by another
     * station, so foreign field ids cannot be probed for existence.
     */
    private ProfileField requireOwnedField(Context ctx, int fieldId) {
        requireStation(UserSession.from(ctx));
        return requireOwnedOrNotFound(ctx, fieldId, profileFieldService::findById, ProfileField::stationId);
    }

    /**
     * Loads a station member and asserts they belong to the caller's station.
     * Answers with 404 when the member is absent or owned by another station.
     */
    private StationMember requireOwnedMember(Context ctx, int memberId) {
        requireStation(UserSession.from(ctx));
        return requireOwnedOrNotFound(ctx, memberId, stationMemberRepository::findById, StationMember::stationId);
    }

    // -- Field Definitions --

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Field definitions (station config)
        routes.get(prefix + "/profile-fields", this::list, StationPermission.USER);
        routes.post(prefix + "/profile-fields", this::create, StationPermission.MEMBER_FIELDS);
        routes.put(prefix + "/profile-fields/order", this::reorder, StationPermission.MEMBER_FIELDS);
        routes.get(prefix + "/profile-fields/assignments", this::listAssignments, StationPermission.USER);
        routes.get(prefix + "/profile-fields/{id}", this::get, StationPermission.USER);
        routes.put(prefix + "/profile-fields/{id}", this::update, StationPermission.MEMBER_FIELDS);
        routes.delete(prefix + "/profile-fields/{id}", this::delete, StationPermission.MEMBER_FIELDS);

        // Who a field is asked of, which is what makes one definition serve several audiences.
        routes.put(prefix + "/profile-fields/{id}/assignments", this::assign, StationPermission.MEMBER_FIELDS);
        routes.delete(prefix + "/profile-fields/{id}/assignments", this::unassign, StationPermission.MEMBER_FIELDS);

        // Field values per member - MEMBER or TEAM can read/write own, MEMBER_MANAGER for any
        routes.get(prefix + "/station-members/{memberId}/fields", this::getApplicableFields, StationPermission.USER);
        routes.get(prefix + "/station-members/{memberId}/profile", this::getValues, StationPermission.USER);
        routes.put(prefix + "/station-members/{memberId}/profile", this::setValues, StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/profile-fields",
            methods = HttpMethod.GET,
            summary = "List profile field definitions for the current station",
            tags = {"Profile Fields"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(profileFieldService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/profile-fields",
            methods = HttpMethod.POST,
            summary = "Create a profile field definition",
            tags = {"Profile Fields"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProfileFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ProfileField.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ProfileFieldRequest.class);
        // A spacer may arrive without a name: it is a gap, and the service numbers it instead.
        if (request.fieldType() == null || isBlank(request.name()) && request.fieldType() != ProfileFieldType.SPACER) {
            throw Refusal.PROFILE_FIELD_DETAILS_MISSING_ON_CREATE.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(profileFieldService.create(
                        session.stationId(),
                        request.name(),
                        request.fieldType(),
                        configOf(request),
                        request.required() != null && request.required(),
                        request.readonly() != null && request.readonly(),
                        request.width()));
    }

    @OpenApi(
            path = "/api/v1/profile-fields/assignments",
            methods = HttpMethod.GET,
            summary = "Who each of this station's fields is asked of",
            tags = {"Profile Fields"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldAssignment[].class)))
    private void listAssignments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(profileFieldService.findAssignmentsByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/profile-fields/{id}/assignments",
            methods = HttpMethod.PUT,
            summary = "Ask a kind of member, or a group, this question",
            description = "Adds the assignment or updates how the question is put to that audience.",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignmentRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assign(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedField(ctx, id);
        var request = ctx.bodyAsClass(AssignmentRequest.class);
        if ((request.role() == null) == (request.groupId() == null)) {
            throw Refusal.PROFILE_FIELD_AUDIENCE_AMBIGUOUS_ON_ASSIGN.raise();
        }
        if (request.role() != null) {
            profileFieldService.assignToRole(
                    id,
                    request.role(),
                    request.position(),
                    request.widthOverride(),
                    request.readonlyOverride(),
                    request.requiredOverride());
        } else {
            profileFieldService.assignToGroup(
                    id,
                    request.groupId(),
                    request.position(),
                    request.widthOverride(),
                    request.readonlyOverride(),
                    request.requiredOverride());
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/profile-fields/{id}/assignments",
            methods = HttpMethod.DELETE,
            summary = "Stop asking a kind of member, or a group, this question",
            description = "The definition stays; only this audience stops being asked.",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignmentRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void unassign(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedField(ctx, id);
        var request = ctx.bodyAsClass(AssignmentRequest.class);
        if ((request.role() == null) == (request.groupId() == null)) {
            throw Refusal.PROFILE_FIELD_AUDIENCE_AMBIGUOUS_ON_UNASSIGN.raise();
        }
        if (request.role() != null) {
            profileFieldService.unassignRole(id, request.role());
        } else {
            profileFieldService.unassignGroup(id, request.groupId());
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/profile-fields/{id}",
            methods = HttpMethod.GET,
            summary = "Get a profile field definition",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(requireOwnedField(ctx, id));
    }

    @OpenApi(
            path = "/api/v1/profile-fields/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a profile field definition",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProfileFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        var request = ctx.bodyAsClass(ProfileFieldRequest.class);
        if (isBlank(request.name()) || request.fieldType() == null) {
            throw Refusal.PROFILE_FIELD_DETAILS_MISSING_ON_CHANGE.raise();
        }
        requireOwnedField(ctx, id);
        profileFieldService
                .update(
                        id,
                        request.name(),
                        request.fieldType(),
                        configOf(request),
                        request.required() != null && request.required(),
                        request.readonly() != null && request.readonly(),
                        request.width(),
                        request.keepOnArchive() != null && request.keepOnArchive())
                .ifPresentOrElse(ctx::json, () -> {
                    throw Refusal.PROFILE_FIELD_NOT_HERE_ON_CHANGE.raise();
                });
    }

    // -- Field Values --

    @OpenApi(
            path = "/api/v1/profile-fields/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a profile field definition",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    /**
     * Puts the fields in the given order, in one request rather than one per field.
     *
     * <p>Registered before the path that takes a field id, or "order" is read as one.
     */
    private void reorder(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(FieldOrderRequest.class);
        if (req.role() == null) throw Refusal.PROFILE_FIELD_ORDER_AUDIENCE_MISSING.raise();
        profileFieldService.reorder(
                session.stationId(), req.role(), req.fieldIds() != null ? req.fieldIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedField(ctx, id);
        if (profileFieldService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw Refusal.PROFILE_FIELD_NOT_HERE_ON_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/fields",
            methods = HttpMethod.GET,
            summary = "Get applicable profile field definitions for a member based on their user type",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileField[].class)))
    private void getApplicableFields(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        requireOwnedMember(ctx, memberId);
        ctx.json(profileFieldService.findApplicableFields(memberId));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile",
            methods = HttpMethod.GET,
            summary = "Get profile field values for a member",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldValue[].class)))
    private void getValues(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        requireOwnedMember(ctx, memberId);
        ctx.json(profileFieldService.findValues(memberId));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/profile",
            methods = HttpMethod.PUT,
            summary = "Set profile field values for a member (batch upsert)",
            description = "Provide all field values to set. Each entry is upserted.",
            tags = {"Profile Fields"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetValuesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProfileFieldValue[].class)))
    private void setValues(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        requireOwnedMember(ctx, memberId);
        var request = ctx.bodyAsClass(SetValuesRequest.class);
        boolean canEditReadonly = session.hasPermission(StationPermission.MEMBER_EDIT);

        // Whether a field may be written is the assignment's to say and differs by audience, so it is
        // read for the member being written rather than from the question itself. A field this member
        // is never asked is not writable on them at all.
        Map<Integer, Boolean> readonlyForMember = profileFieldService.findApplicableFields(memberId).stream()
                .filter(field -> field.origin() == FieldOrigin.STATION)
                .collect(Collectors.toMap(
                        ProfileFieldService.MergedField::id,
                        ProfileFieldService.MergedField::readonly,
                        (first, ignored) -> first));

        // A cluster's question is not one of this station's, so it cannot be checked against the station's
        // own list: whether the station may answer it is the cluster's to say, and the service asks that.
        List<FieldValueEntry> entries = request.values() != null
                ? request.values().stream()
                        .filter(v -> {
                            if (v.origin() == FieldOrigin.CLUSTER) return true;
                            requireOwnedField(ctx, v.fieldId());
                            var readonly = readonlyForMember.get(v.fieldId());
                            if (readonly == null) return false;
                            return canEditReadonly || !readonly;
                        })
                        .map(v -> new FieldValueEntry(v.fieldId(), v.value(), originOf(v)))
                        .toList()
                : List.of();
        ctx.json(profileFieldService.setValues(
                memberId, entries, session.member().id()));
    }

    /** An entry that names no origin is the station's own, which is what every older caller sends. */
    private static FieldOrigin originOf(FieldValueEntry entry) {
        return entry.origin() != null ? entry.origin() : FieldOrigin.STATION;
    }

    /** A request naming no settings gets the empty ones rather than none at all. */
    private static ProfileFieldConfig configOf(ProfileFieldRequest request) {
        return request.config() != null ? request.config() : ProfileFieldConfig.empty();
    }

    // -- Request records --

    /**
     * @param config the field's settings as an object, the same shape the field is read back in.
     *               It used to be JSON text on the way in and an object on the way out, and the two
     *               halves of that never agreed: a setting the record did not name was dropped
     *               without a word, which is how a field of group scope lost its group.
     */
    /** @param width how much of a row the question takes by default, which an assignment may override */
    public record ProfileFieldRequest(
            String name,
            ProfileFieldType fieldType,
            ProfileFieldConfig config,
            Boolean required,
            Boolean readonly,
            String width,
            Boolean keepOnArchive) {}

    /**
     * Who a field is asked of, and how it is put to them.
     *
     * <p>Exactly one of {@code role} and {@code groupId} is given. Sending both, or neither, is the
     * caller failing to say who is asked.
     */
    public record AssignmentRequest(
            ProfileFieldScope role,
            Integer groupId,
            int position,
            String widthOverride,
            Boolean readonlyOverride,
            Boolean requiredOverride) {}

    /** The fields of one audience in the order they should stand. */
    public record FieldOrderRequest(ProfileFieldScope role, List<Integer> fieldIds) {}

    public record SetValuesRequest(List<FieldValueEntry> values) {}
}
