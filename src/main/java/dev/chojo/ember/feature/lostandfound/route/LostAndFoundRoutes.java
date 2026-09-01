/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * HTTP routes for managing lost and found items. Provides endpoints for listing, creating,
 * claiming, uploading images, marking as provided, and deleting items. Regular users see
 * only unclaimed items and their own claims; managers see all items.
 */
@Singleton
public class LostAndFoundRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(LostAndFoundRoutes.class);
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final LostAndFoundService lostAndFoundService;
    private final StationMemberService memberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final LostAndFoundImageService imageService;
    private final Api apiConfig;

    @Inject
    public LostAndFoundRoutes(
            LostAndFoundService lostAndFoundService,
            StationMemberService memberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            LostAndFoundImageService imageService,
            Api apiConfig) {
        this.lostAndFoundService = lostAndFoundService;
        this.memberService = memberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.imageService = imageService;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/lost-and-found", this::list, StationPermission.LOGIN);
        routes.post(prefix + "/lost-and-found", this::create, StationPermission.LOST_AND_FOUND_CREATE);
        routes.get(prefix + "/lost-and-found/{id}", this::getById, StationPermission.LOGIN);
        routes.get(prefix + "/lost-and-found/{id}/image", this::getImage, StationPermission.LOGIN);
        routes.post(prefix + "/lost-and-found/{id}/image", this::uploadImage, StationPermission.LOST_AND_FOUND_CREATE);
        routes.post(prefix + "/lost-and-found/{id}/claim", this::claim, StationPermission.LOGIN);
        routes.post(prefix + "/lost-and-found/{id}/release", this::release, StationPermission.LOGIN);
        routes.post(prefix + "/lost-and-found/{id}/provided", this::provided, StationPermission.LOST_AND_FOUND_MANAGE);
        routes.delete(prefix + "/lost-and-found/{id}", this::delete, StationPermission.LOST_AND_FOUND_MANAGE);
    }

    @OpenApi(
            path = "/api/v1/lost-and-found",
            methods = HttpMethod.GET,
            summary = "List lost and found items",
            description = "Returns unclaimed items for regular users. For managers, also includes claimed items.",
            tags = {"Lost and Found"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = LostAndFoundItemResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        boolean isManager = session.hasPermission(StationPermission.LOST_AND_FOUND_MANAGE);
        var items = isManager
                ? lostAndFoundService.findByStation(session.stationId())
                : lostAndFoundService.findUnclaimedOrClaimedBy(
                        session.stationId(), session.member().id());
        ctx.json(items.stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/lost-and-found",
            methods = HttpMethod.POST,
            summary = "Create a lost and found item",
            tags = {"Lost and Found"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateItemRequest.class)),
            responses =
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = LostAndFoundItemResponse.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateItemRequest.class);
        LocalDate foundAt = parseFoundAt(request.foundAt());
        var item = lostAndFoundService.create(
                session.stationId(),
                request.description(),
                foundAt,
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(toResponse(item));
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}",
            methods = HttpMethod.GET,
            summary = "Get a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = LostAndFoundItemResponse.class)))
    private void getById(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(toResponse(requireOwnedItem(ctx, id)));
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/image",
            methods = HttpMethod.GET,
            summary = "Get the image of a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        imageService
                .read(session.stationId(), id, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=3600");
                            ctx.result(img.data());
                        },
                        () -> {
                            throw new NotFoundResponse("No image");
                        });
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/image",
            methods = HttpMethod.POST,
            summary = "Upload an image for a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void uploadImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedItem(ctx, id);
        var file = ctx.uploadedFile("image");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            imageService.store(session.stationId(), id, data, file.contentType(), apiConfig.maxImageSizeBytes());
            ctx.json(new MessageResponse("Image uploaded"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing lost-and-found image for item {}", id, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process lost-and-found image for item {}", id, e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/claim",
            methods = HttpMethod.POST,
            summary = "Claim a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClaimRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void claim(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedItem(ctx, id);
        var request = ctx.bodyAsClass(ClaimRequest.class);

        int claimMemberId = request.memberId() != null
                ? request.memberId()
                : session.member().id();
        if (!maySpeakFor(session, claimMemberId)) {
            throw new BadRequestResponse("Not authorized to claim for this member");
        }

        String claimerName = resolveMemberName(claimMemberId);
        if (!lostAndFoundService.claim(id, claimMemberId, session.stationId(), claimerName)) {
            throw new BadRequestResponse("Item already claimed or not found");
        }
        ctx.json(new MessageResponse("Item claimed"));
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/release",
            methods = HttpMethod.POST,
            summary = "Take a claim back off a lost and found item",
            description = "Open to whoever the item is claimed for, to whoever claimed it for somebody in "
                    + "their care, and to the members who look after the lost and found.",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void release(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var item = requireOwnedItem(ctx, id);
        if (item.claimedBy() == null) {
            throw new BadRequestResponse("Item has not been claimed yet");
        }
        boolean isManager = session.hasPermission(StationPermission.LOST_AND_FOUND_MANAGE);
        if (!isManager && !maySpeakFor(session, item.claimedBy())) {
            throw new BadRequestResponse("Not authorized to release this claim");
        }
        if (!lostAndFoundService.release(id)) {
            throw new BadRequestResponse("Item has not been claimed yet");
        }
        ctx.json(new MessageResponse("Claim released"));
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/provided",
            methods = HttpMethod.POST,
            summary = "Mark a claimed item as provided (handed back) and delete it",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void provided(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var item = requireOwnedItem(ctx, id);
        if (item.claimedBy() == null) {
            throw new BadRequestResponse("Item has not been claimed yet");
        }
        lostAndFoundService.delete(session.stationId(), id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedItem(ctx, id);
        lostAndFoundService.delete(session.stationId(), id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Loads the item named in the path, in the caller's station. Answers 404 for an item of another
     * station, so an item id cannot be walked to find out what other stations have lost.
     */
    private LostAndFoundItem requireOwnedItem(Context ctx, int id) {
        return requireOwnedOrNotFound(ctx, id, lostAndFoundService::findById, LostAndFoundItem::stationId);
    }

    /**
     * Whether the caller may act as the given member: themselves, or somebody in their care.
     */
    private boolean maySpeakFor(UserSession session, int memberId) {
        if (memberId == session.member().id()) {
            return true;
        }
        return memberService.findManaged(session.member().id()).stream().anyMatch(m -> m.id() == memberId);
    }

    /**
     * Reads the found date the reporter gave, defaulting to today. A date nobody could parse is the
     * caller's mistake and is answered as one, rather than as a failure of the server.
     */
    private static LocalDate parseFoundAt(String foundAt) {
        if (foundAt == null || foundAt.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(foundAt);
        } catch (DateTimeParseException e) {
            throw new BadRequestResponse("Invalid found date");
        }
    }

    private String resolveMemberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(m -> {
                    if (m.accountId() != null) {
                        return accountRepository
                                .findById(m.accountId())
                                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                                .orElse(m.displayName());
                    }
                    return m.displayName();
                })
                .orElse("?");
    }

    private LostAndFoundItemResponse toResponse(LostAndFoundItem item) {
        String claimedByName = item.claimedBy() != null ? resolveMemberName(item.claimedBy()) : null;
        boolean hasImage = imageService.exists(item.stationId(), item.id());
        return new LostAndFoundItemResponse(
                item.id(),
                item.stationId(),
                item.description(),
                item.foundAt() != null ? item.foundAt().toString() : null,
                hasImage,
                item.claimedBy(),
                claimedByName,
                item.claimedAt(),
                item.createdBy(),
                item.createdAt());
    }

    /**
     * API response representing a lost and found item with resolved claimer name.
     *
     * @param id            the item ID
     * @param stationId     the station ID
     * @param description   the item description
     * @param foundAt       the date the item was found (ISO format)
     * @param hasImage      whether an image is attached
     * @param claimedBy     the claiming member ID (null if unclaimed)
     * @param claimedByName the display name of the claimer (null if unclaimed)
     * @param claimedAt     the claim timestamp (null if unclaimed)
     * @param createdBy     the reporting member ID
     * @param createdAt     the creation timestamp
     */
    public record LostAndFoundItemResponse(
            int id,
            int stationId,
            String description,
            String foundAt,
            boolean hasImage,
            Integer claimedBy,
            String claimedByName,
            Instant claimedAt,
            int createdBy,
            Instant createdAt) {}

    /**
     * Request body for creating a new lost and found item.
     *
     * @param description a description of the found item
     * @param foundAt     the date the item was found (ISO format, defaults to today if null)
     */
    public record CreateItemRequest(String description, String foundAt) {}

    /**
     * Request body for claiming a lost and found item.
     *
     * @param memberId the member ID to claim on behalf of (null to claim for the current user)
     */
    public record ClaimRequest(Integer memberId) {}
}
