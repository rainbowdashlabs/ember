/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.AccountSignature;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.SignatureImages;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * The caller's own signature picture and their consent to letters being signed with it, in the account
 * settings. Only the account's owner reads or changes either; there is no way here to see another
 * person's picture.
 */
@Singleton
public class SignatureImageRoutes implements Routes {
    private static final String PNG = "image/png";

    private final SignatureImageService images;

    @Inject
    public SignatureImageRoutes(SignatureImageService images) {
        this.images = images;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session/signature", this::settings, StationPermission.LOGIN);
        routes.get(prefix + "/session/signature/image", this::image, StationPermission.LOGIN);
        routes.put(prefix + "/session/signature/image", this::save, StationPermission.LOGIN);
        routes.delete(prefix + "/session/signature/image", this::delete, StationPermission.LOGIN);
        routes.put(prefix + "/session/signature/consent", this::consent, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/session/signature",
            methods = HttpMethod.GET,
            summary = "The caller's own signature picture and consent to automatic signing",
            tags = {"Signing"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignatureSettingsResponse.class)))
    private void settings(Context ctx) {
        ctx.json(SignatureSettingsResponse.of(
                images.settings(UserSession.from(ctx).accountId())));
    }

    @OpenApi(
            path = "/api/v1/session/signature/image",
            methods = HttpMethod.GET,
            summary = "The caller's own signature picture",
            tags = {"Signing"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = PNG)),
                @OpenApiResponse(status = "204")
            })
    private void image(Context ctx) {
        images.image(UserSession.from(ctx).accountId())
                .ifPresentOrElse(
                        png -> {
                            ctx.res().setCharacterEncoding(null);
                            ctx.contentType(PNG);
                            ctx.header("Cache-Control", "private, no-store");
                            ctx.result(png);
                        },
                        () -> ctx.status(HttpStatus.NO_CONTENT));
    }

    @OpenApi(
            path = "/api/v1/session/signature/image",
            methods = HttpMethod.PUT,
            summary = "Save a drawn, typed or uploaded signature picture as the caller's own",
            tags = {"Signing"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(type = "multipart/form-data")),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignatureSettingsResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "413", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "415", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void save(Context ctx) {
        int accountId = UserSession.from(ctx).accountId();
        var source = sourceOf(ctx.formParam("source"));
        var file = ctx.uploadedFile("image");
        if (file == null) throw DocumentRefusal.SIGNATURE_IMAGE_MISSING.raise();
        ctx.json(SignatureSettingsResponse.of(images.save(accountId, bytesOf(file), source)));
    }

    @OpenApi(
            path = "/api/v1/session/signature/image",
            methods = HttpMethod.DELETE,
            summary = "Delete the caller's own signature picture",
            tags = {"Signing"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignatureSettingsResponse.class)))
    private void delete(Context ctx) {
        ctx.json(
                SignatureSettingsResponse.of(images.delete(UserSession.from(ctx).accountId())));
    }

    @OpenApi(
            path = "/api/v1/session/signature/consent",
            methods = HttpMethod.PUT,
            summary = "Agree to, or take back, letters being signed with the caller's picture automatically",
            tags = {"Signing"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SignatureConsentRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignatureSettingsResponse.class)))
    private void consent(Context ctx) {
        var body = ctx.bodyAsClass(SignatureConsentRequest.class);
        int accountId = UserSession.from(ctx).accountId();
        ctx.json(SignatureSettingsResponse.of(images.consent(accountId, body.consented())));
    }

    private static SignatureImageSource sourceOf(@Nullable String source) {
        if (source == null) throw DocumentRefusal.SIGNATURE_IMAGE_SOURCE_UNKNOWN.raise();
        try {
            return SignatureImageSource.valueOf(source);
        } catch (IllegalArgumentException e) {
            throw DocumentRefusal.SIGNATURE_IMAGE_SOURCE_UNKNOWN.raise();
        }
    }

    /** The upload's bytes, read no further than one byte past the largest picture taken. */
    private static byte[] bytesOf(UploadedFile file) {
        if (file.size() > SignatureImages.MAX_BYTES) throw DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE.raise();
        try (InputStream content = file.content()) {
            byte[] data = content.readNBytes(SignatureImages.MAX_BYTES + 1);
            if (data.length > SignatureImages.MAX_BYTES) throw DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE.raise();
            return data;
        } catch (IOException e) {
            throw new UncheckedIOException("The signature picture could not be read", e);
        }
    }

    /**
     * Whether letters the caller issues are signed with their picture from now on.
     *
     * @param consented true to agree, false to take it back
     */
    public record SignatureConsentRequest(boolean consented) {}

    /**
     * What the caller keeps about their own signature.
     *
     * @param hasImage            whether a signature picture is saved
     * @param imageSource         how it was made, or null while none is saved
     * @param imageSavedAt        when it was saved, or null while none is saved
     * @param autoSignConsentedAt when the caller agreed to letters they issue being signed with it, or null
     *                            while they have not
     */
    public record SignatureSettingsResponse(
            boolean hasImage,
            @Nullable SignatureImageSource imageSource,
            @Nullable Instant imageSavedAt,
            @Nullable Instant autoSignConsentedAt) {
        static SignatureSettingsResponse of(AccountSignature signature) {
            return new SignatureSettingsResponse(
                    signature.hasImage(),
                    signature.imageSource(),
                    signature.imageSavedAt(),
                    signature.autoSignConsentedAt());
        }
    }
}
