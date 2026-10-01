/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.entity.InviteCodeResponse;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationEnrollmentService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
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

import java.util.List;

@Singleton
public class FederationRoutes implements Routes {

    private final FederationService service;
    private final FederationEnrollmentService enrollmentService;
    private final KnowledgeBaseFederationService kbFederationService;

    @Inject
    public FederationRoutes(
            FederationService service,
            FederationEnrollmentService enrollmentService,
            KnowledgeBaseFederationService kbFederationService) {
        this.service = service;
        this.enrollmentService = enrollmentService;
        this.kbFederationService = kbFederationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/federation/partners",
                this::listPartners,
                StationPermission.STATION_FEDERATION,
                StationPermission.BOARD_FEDERATE,
                StationPermission.EVENTS_FEDERATE,
                StationPermission.NEWS_FEDERATE,
                StationPermission.KNOWLEDGE_FEDERATE);
        routes.post(
                prefix + "/federation/invite",
                this::createInvite,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/federation/accept",
                this::acceptInvite,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.get(prefix + "/federation/partners/{id}", this::getPartner, StationPermission.STATION_FEDERATION);
        routes.get(prefix + "/federation/requests", this::listPendingRequests, StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/requests/{id}/accept",
                this::acceptPairRequest,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/federation/requests/{id}/decline",
                this::declinePairRequest,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/federation/partners/{id}/suspend",
                this::suspendPartner,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/federation/partners/{id}/resume",
                this::resumePartner,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/federation/partners/{id}",
                this::endFederation,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);

        routes.get(
                prefix + "/federation/partners/{id}/capabilities",
                this::getCapabilities,
                StationPermission.STATION_FEDERATION);
        routes.put(
                prefix + "/federation/partners/{id}/capabilities",
                this::setCapabilities,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);

        routes.get(prefix + "/federation/shares/kb", this::listKbShares, StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/shares/kb",
                this::createKbShare,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/federation/shares/kb/{id}",
                this::deleteKbShare,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.get(prefix + "/federation/shares/quiz", this::listQuizShares, StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/shares/quiz",
                this::createQuizShare,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/federation/shares/quiz/{id}",
                this::deleteQuizShare,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.get(
                prefix + "/federation/shares/protocol", this::listProtocolShares, StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/shares/protocol",
                this::createProtocolShare,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/federation/shares/protocol/{id}",
                this::deleteProtocolShare,
                StationPermission.STATION_FEDERATION,
                StepUpCategory.FEDERATION);

        routes.get(prefix + "/federation/info", this::getInfo, StationPermission.STATION_FEDERATION);
    }

    @OpenApi(
            path = "/api/v1/federation/partners",
            methods = HttpMethod.GET,
            summary = "List the station's federation partners",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerResponse[].class)))
    private void listPartners(Context ctx) {
        var session = UserSession.from(ctx);
        var partners = service.findPartners(session.stationId());
        ctx.json(partners.stream()
                .map(p -> new PartnerResponse(p, service.partnerName(p)))
                .toList());
    }

    /**
     * A station invite, which carries a token proving consent, so the partnership is active as soon
     * as the other station accepts it.
     */
    @OpenApi(
            path = "/api/v1/federation/invite",
            methods = HttpMethod.POST,
            summary = "Create a pairing code for this station",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InviteCodeResponse.class)))
    private void createInvite(Context ctx) {
        var session = UserSession.from(ctx);
        var code = service.generateStationInvite(session.stationId())
                .orElseThrow(Refusal.FEDERATION_STATION_NOT_HERE::raise);
        ctx.json(new InviteCodeResponse(code));
    }

    /**
     * A refusal is reported with the reason as its own field, so the page can say what actually
     * stands in the way instead of guessing at an expiry that no pairing code has.
     */
    @OpenApi(
            path = "/api/v1/federation/accept",
            methods = HttpMethod.POST,
            summary = "Enter a pairing code from another station",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AcceptRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FederationPartner.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void acceptInvite(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(AcceptRequest.class);
        if (req.inviteCode() == null || req.inviteCode().isBlank()) {
            throw Refusal.INVITE_CODE_MISSING.raise();
        }

        switch (enrollmentService.enterCode(
                session.stationId(), req.inviteCode().trim())) {
            case FederationService.CodeOutcome.Partnered partnered ->
                ctx.status(HttpStatus.CREATED).json(partnered.partner());
            case FederationService.CodeOutcome.Requested requested ->
                ctx.status(HttpStatus.CREATED).json(requested.partner());
            case FederationService.CodeOutcome.Refused refused ->
                ctx.status(HttpStatus.BAD_REQUEST)
                        .json(new ErrorResponseWrapper(refused.reason().name(), refusalMessage(refused)));
        }
    }

    private String refusalMessage(FederationService.CodeOutcome.Refused refused) {
        return switch (refused.reason()) {
            case MALFORMED -> "Not a pairing code";
            case OTHER_INSTANCE -> "A code from " + refused.detail() + " must carry an invite token";
            case HOST_REFUSED -> refused.detail() + " is not an address this instance will call";
            case REMOTE_UNREACHABLE -> refused.detail() + " did not answer";
            case REMOTE_TIMEOUT -> refused.detail() + " took too long to answer";
            case REMOTE_REFUSED -> refused.detail() + " would not accept this station";
            case REMOTE_STATION_GONE -> "The station this code was made for no longer exists on " + refused.detail();
            case CONTRACT_MISMATCH ->
                "This instance and " + refused.detail() + " run federation versions that "
                        + "cannot talk to each other";
            case UNKNOWN_STATION -> "No station on this instance answers to that code";
            case OWN_STATION -> "A station cannot federate with itself";
            case ALREADY_PARTNERED -> "These stations are already connected";
            case REQUEST_PENDING -> "A request to this station is already waiting";
            case SPENT_TOKEN -> "This code has already been used";
        };
    }

    @OpenApi(
            path = "/api/v1/federation/requests",
            methods = HttpMethod.GET,
            summary = "List the pair requests waiting for this station",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PairRequestResponse[].class)))
    private void listPendingRequests(Context ctx) {
        var session = UserSession.from(ctx);
        var requests = service.findPendingRequests(session.stationId());
        ctx.json(requests.stream()
                .map(p -> new PairRequestResponse(
                        p.id(), service.requesterName(p), p.createdAt().toString()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/federation/requests/{id}/accept",
            methods = HttpMethod.POST,
            summary = "Accept a pair request",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationPartner.class)))
    private void acceptPairRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int requestId = ctx.pathParamAsClass("id", Integer.class).get();
        service.findRequestTo(requestId, session.stationId())
                .orElseThrow(Refusal.PAIR_REQUEST_NOT_HERE_TO_ACCEPT::raise);
        ctx.json(service.acceptPairRequest(requestId));
    }

    @OpenApi(
            path = "/api/v1/federation/requests/{id}/decline",
            methods = HttpMethod.POST,
            summary = "Decline a pair request",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void declinePairRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int requestId = ctx.pathParamAsClass("id", Integer.class).get();
        service.findRequestTo(requestId, session.stationId())
                .orElseThrow(Refusal.PAIR_REQUEST_NOT_HERE_TO_DECLINE::raise);
        service.declinePairRequest(requestId);
        ctx.json(new MessageResponse("Request declined"));
    }

    /**
     * Loads a partner by its path id and confirms it belongs to the caller's station,
     * so a federation manager of one station cannot address another station's partner
     * rows by enumerating ids.
     */
    private FederationPartner requireOwnedPartner(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var partner = service.findPartner(id).orElseThrow(Refusal.FEDERATION_PARTNER_NOT_HERE::raise);
        RouteSupport.requireSameStation(session, partner.stationId());
        return partner;
    }

    @OpenApi(
            path = "/api/v1/federation/partners/{id}",
            methods = HttpMethod.GET,
            summary = "Get a federation partner",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerResponse.class)))
    private void getPartner(Context ctx) {
        var partner = requireOwnedPartner(ctx);
        ctx.json(new PartnerResponse(partner, service.partnerName(partner)));
    }

    @OpenApi(
            path = "/api/v1/federation/partners/{id}/suspend",
            methods = HttpMethod.POST,
            summary = "Suspend a federation partner",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationPartner.class)))
    private void suspendPartner(Context ctx) {
        var partner = requireOwnedPartner(ctx);
        service.suspendPartner(partner.id());
        ctx.json(service.findPartner(partner.id())
                .orElseThrow(Refusal.FEDERATION_PARTNER_NOT_HERE_AFTER_SUSPENDING::raise));
    }

    @OpenApi(
            path = "/api/v1/federation/partners/{id}/resume",
            methods = HttpMethod.POST,
            summary = "Resume a suspended federation partner",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationPartner.class)))
    private void resumePartner(Context ctx) {
        var partner = requireOwnedPartner(ctx);
        service.resumePartner(partner.id());
        ctx.json(service.findPartner(partner.id())
                .orElseThrow(Refusal.FEDERATION_PARTNER_NOT_HERE_AFTER_RESUMING::raise));
    }

    @OpenApi(
            path = "/api/v1/federation/partners/{id}",
            methods = HttpMethod.DELETE,
            summary = "End a federation partnership",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void endFederation(Context ctx) {
        var partner = requireOwnedPartner(ctx);
        service.endFederation(partner.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/federation/partners/{id}/capabilities",
            methods = HttpMethod.GET,
            summary = "List what is exchanged with a federation partner",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationCapability[].class)))
    private void getCapabilities(Context ctx) {
        var partner = requireOwnedPartner(ctx);
        ctx.json(service.findCapabilities(partner.id()));
    }

    @OpenApi(
            path = "/api/v1/federation/partners/{id}/capabilities",
            methods = HttpMethod.PUT,
            summary = "Set what is exchanged with a federation partner",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CapabilityRequest[].class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationCapability[].class)))
    private void setCapabilities(Context ctx) {
        var partner = requireOwnedPartner(ctx);
        var req = ctx.bodyAsClass(CapabilityRequest[].class);
        for (var cap : req) {
            service.setCapability(partner.id(), cap.capability(), cap.direction(), cap.enabled());
        }
        ctx.json(service.findCapabilities(partner.id()));
    }

    @OpenApi(
            path = "/api/v1/federation/shares/kb",
            methods = HttpMethod.GET,
            summary = "List the station's knowledge base shares",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbShareResponse[].class)))
    private void listKbShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findKbShares(session.stationId()).stream()
                .map(share -> new KbShareResponse(
                        share.id(),
                        share.fileId(),
                        share.folderId(),
                        share.shareScope(),
                        service.findKbShareTargets(share.id())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/federation/shares/kb",
            methods = HttpMethod.POST,
            summary = "Share a knowledge base entry with partners",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = KbShareRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = FederationShare.class)))
    private void createKbShare(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(KbShareRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(kbFederationService.shareEntry(
                        session.stationId(),
                        req.fileId(),
                        req.folderId(),
                        req.shareScope() != null ? req.shareScope() : ShareScope.ALL_PARTNERS,
                        req.partnerIds() != null ? req.partnerIds() : List.of()));
    }

    @OpenApi(
            path = "/api/v1/federation/shares/kb/{id}",
            methods = HttpMethod.DELETE,
            summary = "Stop sharing a knowledge base entry",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteKbShare(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (!service.deleteKbShare(id, session.stationId())) {
            throw Refusal.KB_SHARE_NOT_HERE_TO_DELETE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/federation/shares/quiz",
            methods = HttpMethod.GET,
            summary = "List the station's quiz catalog shares",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShare[].class)))
    private void listQuizShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findQuizShares(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/federation/shares/quiz",
            methods = HttpMethod.POST,
            summary = "Share a quiz catalog with partners",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizShareRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = FederationShare.class)))
    private void createQuizShare(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(QuizShareRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createQuizShare(
                        session.stationId(),
                        req.catalogId(),
                        req.shareScope() != null ? req.shareScope() : ShareScope.ALL_PARTNERS));
    }

    @OpenApi(
            path = "/api/v1/federation/shares/quiz/{id}",
            methods = HttpMethod.DELETE,
            summary = "Stop sharing a quiz catalog",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteQuizShare(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (!service.deleteQuizShare(id, session.stationId())) {
            throw Refusal.QUIZ_SHARE_NOT_HERE_TO_DELETE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/federation/shares/protocol",
            methods = HttpMethod.GET,
            summary = "List the station's test protocol shares",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShare[].class)))
    private void listProtocolShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findProtocolShares(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/federation/shares/protocol",
            methods = HttpMethod.POST,
            summary = "Share a test protocol with partners",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolShareRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = FederationShare.class)))
    private void createProtocolShare(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(ProtocolShareRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createProtocolShare(
                        session.stationId(),
                        req.protocolId(),
                        req.shareScope() != null ? req.shareScope() : ShareScope.ALL_PARTNERS));
    }

    @OpenApi(
            path = "/api/v1/federation/shares/protocol/{id}",
            methods = HttpMethod.DELETE,
            summary = "Stop sharing a test protocol",
            tags = {"Federation"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteProtocolShare(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (!service.deleteProtocolShare(id, session.stationId())) {
            throw Refusal.PROTOCOL_SHARE_NOT_HERE_TO_DELETE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/federation/info",
            methods = HttpMethod.GET,
            summary = "Get the federation contract this instance speaks",
            tags = {"Federation"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationInfoResponse.class)))
    private void getInfo(Context ctx) {
        ctx.json(new FederationInfoResponse(FederationContractVersions.current()));
    }

    public record AcceptRequest(String inviteCode) {}

    public record CapabilityRequest(CapabilityType capability, Direction direction, boolean enabled) {}

    public record KbShareRequest(Integer fileId, Integer folderId, ShareScope shareScope, List<Integer> partnerIds) {}

    /**
     * A knowledge share as the screens read it, carrying the stations it names so the dialog can show
     * the audience it is about to change rather than starting blank every time.
     */
    public record KbShareResponse(
            int id,
            @Nullable Integer fileId,
            @Nullable Integer folderId,
            ShareScope shareScope,
            List<Integer> partnerIds) {}

    public record QuizShareRequest(int catalogId, ShareScope shareScope) {}

    public record ProtocolShareRequest(int protocolId, ShareScope shareScope) {}

    public record PartnerResponse(FederationPartner partner, String partnerStationName) {}

    public record PairRequestResponse(int id, String stationName, String createdAt) {}

    public record FederationInfoResponse(FederationContract contract) {}
}
