/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.SealVerification;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityInfo;
import dev.chojo.ember.feature.signing.entity.StationCertificateInfo;
import dev.chojo.ember.feature.signing.service.PublishedCertificates;
import dev.chojo.ember.feature.signing.service.RevocationListAddress;
import dev.chojo.ember.feature.signing.service.SealVerifier;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.http.util.MultipartUtil;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.MultipartConfigElement;
import org.jspecify.annotations.Nullable;

/**
 * The public addresses a reader of a sealed document checks its seal against, without signing in: the
 * installation's signing authorities with their certificates and revocation lists, and each station's
 * seal certificates.
 *
 * <p>The revocation list answers at {@link RevocationListAddress#ROUTE}, the address every station
 * certificate names as its distribution point, so the two can never drift apart. Certificates and
 * lists are DER encoded with their registered media types, the form readers and validators fetch.
 * Unknown serial numbers and stations that never sealed anything are refused as not found.
 *
 * <p>Anybody holding a PDF can also have its seals checked here ({@link SealVerifier}): the file is
 * checked in memory and never stored, and a request larger than a check takes is refused by its announced
 * length before its body is read, or while it is read where it announces none. Besides the global rate
 * limit, the check counts towards the tighter limit of the expensive routes.
 */
@Singleton
public class PublicSigningRoutes implements Routes {
    /** Where an authority's certificate is published, beside its revocation list. */
    static final String AUTHORITY_CERTIFICATE_ROUTE = "/public/signing/ca/{serial}.crt";

    static final String CERTIFICATE_TYPE = "application/pkix-cert";
    static final String REVOCATION_LIST_TYPE = "application/pkix-crl";

    /**
     * How much larger than the file itself a check request may be: the multipart envelope around it. A
     * request announcing more is refused before any of its body is read.
     */
    static final int FORM_ALLOWANCE_BYTES = 64 * 1024;

    /** How much of a form is kept in memory before the server writes it to a temporary file. */
    private static final int IN_MEMORY_BYTES = 1024 * 1024;

    private static final MultipartConfigElement CHECK_FORM = new MultipartConfigElement(
            System.getProperty("java.io.tmpdir"),
            SealVerifier.MAX_BYTES,
            SealVerifier.MAX_BYTES + FORM_ALLOWANCE_BYTES,
            IN_MEMORY_BYTES);

    private final PublishedCertificates certificates;
    private final SealVerifier verifier;

    /**
     * @param certificates what the installation publishes for its seals
     * @param verifier     checks the seals of an uploaded document
     */
    @Inject
    public PublicSigningRoutes(PublishedCertificates certificates, SealVerifier verifier) {
        this.certificates = certificates;
        this.verifier = verifier;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/signing/ca", this::listAuthorities);
        routes.get(prefix + AUTHORITY_CERTIFICATE_ROUTE, this::authorityCertificate);
        routes.get(prefix + RevocationListAddress.ROUTE, this::revocationList);
        routes.get(prefix + "/public/station/{stationUid}/signing/certificates", this::listStationCertificates);
        routes.get(prefix + "/public/station/{stationUid}/signing/certificates/{serial}.crt", this::stationCertificate);
        routes.post(prefix + "/public/signing/verify", this::verify);
    }

    @OpenApi(
            path = "/api/v1/public/signing/verify",
            methods = HttpMethod.POST,
            summary = "Check the seals and timestamps of a PDF",
            tags = {"Public Signing"},
            requestBody =
                    @OpenApiRequestBody(
                            content = @OpenApiContent(type = "multipart/form-data"),
                            description = "Multipart form with 'file', the PDF to check, at most 25 MB"),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SealVerification.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "413", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "415", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void verify(Context ctx) {
        if (ctx.contentLength() > SealVerifier.MAX_BYTES + FORM_ALLOWANCE_BYTES) {
            throw DocumentRefusal.SEAL_CHECK_TOO_LARGE.raise();
        }
        ctx.json(verifier.verify(upload(ctx)));
    }

    /**
     * The file sent for a check, read under the check's own limits rather than the server's: the file at
     * most {@link SealVerifier#MAX_BYTES} and the whole form at most {@link #FORM_ALLOWANCE_BYTES} more. The
     * limits hold while the body is read, so a body sent in chunks without announcing its length is cut off
     * as soon as it grows past them. A body cut off that way is refused as too large, any other one that
     * cannot be read as a form as not received.
     */
    private static @Nullable UploadedFile upload(Context ctx) {
        ctx.req().setAttribute(MultipartUtil.MULTIPART_CONFIG_ATTRIBUTE, CHECK_FORM);
        try {
            return ctx.uploadedFile("file");
        } catch (Exception e) {
            if (overLimit(e)) throw DocumentRefusal.SEAL_CHECK_TOO_LARGE.raise();
            throw DocumentRefusal.SEAL_CHECK_NOT_RECEIVED.raise();
        }
    }

    /** Whether reading a form broke off because it grew past a limit, which the server says in words only. */
    private static boolean overLimit(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (cause instanceof IllegalStateException && message != null && message.contains(" exceeded")) {
                return true;
            }
        }
        return false;
    }

    @OpenApi(
            path = "/api/v1/public/signing/ca",
            methods = HttpMethod.GET,
            summary = "List the installation's signing authorities",
            tags = {"Public Signing"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SigningAuthorityInfo[].class)))
    private void listAuthorities(Context ctx) {
        ctx.json(certificates.authorities());
    }

    @StationFree("a signing authority belongs to the installation, not to a station")
    @OpenApi(
            path = "/api/v1" + AUTHORITY_CERTIFICATE_ROUTE,
            methods = HttpMethod.GET,
            summary = "Download a signing authority's certificate",
            tags = {"Public Signing"},
            pathParams = @OpenApiParam(name = "serial", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = CERTIFICATE_TYPE)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void authorityCertificate(Context ctx) {
        var serial = ctx.pathParam("serial");
        var der = certificates
                .authorityCertificate(serial)
                .orElseThrow(DocumentRefusal.SIGNING_AUTHORITY_NOT_HERE::raise);
        send(ctx, der, CERTIFICATE_TYPE, serial + ".crt");
    }

    @StationFree("a signing authority's revocation list belongs to the installation, not to a station")
    @OpenApi(
            path = "/api/v1" + RevocationListAddress.ROUTE,
            methods = HttpMethod.GET,
            summary = "Download a signing authority's current revocation list",
            tags = {"Public Signing"},
            pathParams =
                    @OpenApiParam(name = RevocationListAddress.SERIAL_PARAMETER, type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = REVOCATION_LIST_TYPE)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void revocationList(Context ctx) {
        var serial = ctx.pathParam(RevocationListAddress.SERIAL_PARAMETER);
        var der = certificates.revocationList(serial).orElseThrow(DocumentRefusal.SIGNING_AUTHORITY_NOT_HERE::raise);
        send(ctx, der, REVOCATION_LIST_TYPE, serial + ".crl");
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/signing/certificates",
            methods = HttpMethod.GET,
            summary = "List a station's seal certificates",
            tags = {"Public Signing"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationCertificateInfo[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listStationCertificates(Context ctx) {
        int stationId = certificates.resolveSealingStation(ctx.pathParam("stationUid"));
        ctx.json(certificates.stationCertificates(stationId));
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/signing/certificates/{serial}.crt",
            methods = HttpMethod.GET,
            summary = "Download one of a station's seal certificates",
            tags = {"Public Signing"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "serial", type = String.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = CERTIFICATE_TYPE)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void stationCertificate(Context ctx) {
        int stationId = certificates.resolveSealingStation(ctx.pathParam("stationUid"));
        var serial = ctx.pathParam("serial");
        var der = certificates
                .stationCertificate(stationId, serial)
                .orElseThrow(DocumentRefusal.SEAL_CERTIFICATE_NOT_HERE::raise);
        send(ctx, der, CERTIFICATE_TYPE, serial + ".crt");
    }

    /**
     * Sends DER bytes under their media type alone. The server's default text encoding is cleared
     * first, since a charset parameter on a binary type is wrong and a strict validator may refuse it.
     */
    private static void send(Context ctx, byte[] der, String contentType, String fileName) {
        ctx.res().setCharacterEncoding(null);
        ctx.contentType(contentType);
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, fileName));
        ctx.result(der);
    }
}
