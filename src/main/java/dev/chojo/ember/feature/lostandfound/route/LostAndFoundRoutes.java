/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Singleton
public class LostAndFoundRoutes implements Routes {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final LostAndFoundService lostAndFoundService;
    private final StationMemberService memberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public LostAndFoundRoutes(
            LostAndFoundService lostAndFoundService,
            StationMemberService memberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.lostAndFoundService = lostAndFoundService;
        this.memberService = memberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/lost-and-found", this::list, Roles.LOGIN);
        routes.post(prefix + "/lost-and-found", this::create, Roles.LOST_AND_FOUND_MANAGEMENT);
        routes.get(prefix + "/lost-and-found/{id}", this::getById, Roles.LOGIN);
        routes.get(prefix + "/lost-and-found/{id}/image", this::getImage, Roles.LOGIN);
        routes.post(prefix + "/lost-and-found/{id}/image", this::uploadImage, Roles.LOST_AND_FOUND_MANAGEMENT);
        routes.post(prefix + "/lost-and-found/{id}/claim", this::claim, Roles.LOGIN);
        routes.post(prefix + "/lost-and-found/{id}/provided", this::provided, Roles.LOST_AND_FOUND_MANAGEMENT);
        routes.delete(prefix + "/lost-and-found/{id}", this::delete, Roles.LOST_AND_FOUND_MANAGEMENT);
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
        boolean isManager = session.roles().contains(Roles.LOST_AND_FOUND_MANAGEMENT);
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
        LocalDate foundAt = request.foundAt() != null ? LocalDate.parse(request.foundAt()) : LocalDate.now();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var item = lostAndFoundService.findById(id).orElseThrow(() -> new NotFoundResponse("Item not found"));
        ctx.json(toResponse(item));
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/image",
            methods = HttpMethod.GET,
            summary = "Get the image of a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getImage(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var image = lostAndFoundService.findImage(id);
        if (image.isEmpty()) {
            throw new NotFoundResponse("No image");
        }
        ctx.contentType(image.get().contentType());
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.result(image.get().data());
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/image",
            methods = HttpMethod.POST,
            summary = "Upload an image for a lost and found item",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void uploadImage(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = ctx.uploadedFile("image");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try {
            byte[] data = file.content().readAllBytes();
            lostAndFoundService.uploadImage(id, data, file.contentType());
            ctx.json(new MessageResponse("Image uploaded"));
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read file");
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(ClaimRequest.class);

        int claimMemberId;
        if (request.memberId() != null) {
            var managed = memberService.findManaged(session.member().id());
            boolean isManaging = managed.stream().anyMatch(m -> m.id() == request.memberId());
            if (!isManaging && request.memberId() != session.member().id()) {
                throw new BadRequestResponse("Not authorized to claim for this member");
            }
            claimMemberId = request.memberId();
        } else {
            claimMemberId = session.member().id();
        }

        String claimerName = resolveMemberName(claimMemberId);
        if (!lostAndFoundService.claim(id, claimMemberId, session.stationId(), claimerName)) {
            throw new BadRequestResponse("Item already claimed or not found");
        }
        ctx.json(new MessageResponse("Item claimed"));
    }

    @OpenApi(
            path = "/api/v1/lost-and-found/{id}/provided",
            methods = HttpMethod.POST,
            summary = "Mark a claimed item as provided (handed back) and delete it",
            tags = {"Lost and Found"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void provided(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var item = lostAndFoundService.findById(id).orElseThrow(() -> new NotFoundResponse("Item not found"));
        if (item.claimedBy() == null) {
            throw new BadRequestResponse("Item has not been claimed yet");
        }
        lostAndFoundService.delete(id);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        lostAndFoundService.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
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
        return new LostAndFoundItemResponse(
                item.id(),
                item.stationId(),
                item.description(),
                item.foundAt() != null ? item.foundAt().toString() : null,
                item.hasImage(),
                item.claimedBy(),
                claimedByName,
                item.claimedAt(),
                item.createdBy(),
                item.createdAt());
    }

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

    public record CreateItemRequest(String description, String foundAt) {}

    public record ClaimRequest(Integer memberId) {}
}
