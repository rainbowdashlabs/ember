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
import dev.chojo.ember.feature.signing.entity.SigningAuthorityInfo;
import dev.chojo.ember.feature.signing.entity.StationCertificateInfo;
import dev.chojo.ember.feature.signing.service.PublishedCertificates;
import dev.chojo.ember.feature.signing.service.RevocationListAddress;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The public addresses a reader of a sealed document checks its seal against, without signing in: the
 * installation's signing authorities with their certificates and revocation lists, and each station's
 * seal certificates.
 *
 * <p>The revocation list answers at {@link RevocationListAddress#ROUTE}, the address every station
 * certificate names as its distribution point, so the two can never drift apart. Certificates and
 * lists are DER encoded with their registered media types, the form readers and validators fetch.
 * Unknown serial numbers and stations that never sealed anything are refused as not found.
 */
@Singleton
public class PublicSigningRoutes implements Routes {
    /** Where an authority's certificate is published, beside its revocation list. */
    static final String AUTHORITY_CERTIFICATE_ROUTE = "/public/signing/ca/{serial}.crt";

    static final String CERTIFICATE_TYPE = "application/pkix-cert";
    static final String REVOCATION_LIST_TYPE = "application/pkix-crl";

    private final PublishedCertificates certificates;

    /** @param certificates what the installation publishes for its seals */
    @Inject
    public PublicSigningRoutes(PublishedCertificates certificates) {
        this.certificates = certificates;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/signing/ca", this::listAuthorities);
        routes.get(prefix + AUTHORITY_CERTIFICATE_ROUTE, this::authorityCertificate);
        routes.get(prefix + RevocationListAddress.ROUTE, this::revocationList);
        routes.get(prefix + "/public/station/{stationUid}/signing/certificates", this::listStationCertificates);
        routes.get(prefix + "/public/station/{stationUid}/signing/certificates/{serial}.crt", this::stationCertificate);
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
