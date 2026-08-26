/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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

import java.util.UUID;

/**
 * The cluster itself: what the caller may act for, and what the one named by the request is.
 *
 * <p>Listing sits at {@code /clusters} because a caller has to be able to ask what they may act for before
 * they can name one. Everything else works against the cluster the request names, which is how every other
 * cluster route in the system finds its subject.
 */
@Singleton
public class ClusterRoutes implements Routes {
    private final ClusterService clusterService;
    private final AccountRepository accountRepository;

    @Inject
    public ClusterRoutes(ClusterService clusterService, AccountRepository accountRepository) {
        this.clusterService = clusterService;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/clusters", this::listMine, StationPermission.LOGIN);
        routes.post(prefix + "/clusters", this::create, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/clusters/all", this::listAll, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/cluster", this::get, ClusterPermission.USER);
        routes.put(prefix + "/cluster", this::update, ClusterPermission.CLUSTER_GENERAL);
        routes.delete(prefix + "/clusters/{clusterUid}", this::delete, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/clusters/{clusterUid}/administrators",
                this::appointAdministrator,
                InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/clusters",
            methods = HttpMethod.GET,
            summary = "List the clusters the caller may act for",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterResponse[].class)))
    private void listMine(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(clusterService.findClustersForAccount(session.accountId()).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/clusters/all",
            methods = HttpMethod.GET,
            summary = "List every cluster on the instance",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterResponse[].class)))
    private void listAll(Context ctx) {
        ctx.json(clusterService.findAll().stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/clusters",
            methods = HttpMethod.POST,
            summary = "Create a cluster and the station shell it owns",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterResponse.class)))
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(ClusterRequest.class);
        ctx.status(HttpStatus.CREATED).json(toResponse(clusterService.create(request.name(), request.description())));
    }

    @OpenApi(
            path = "/api/v1/cluster",
            methods = HttpMethod.GET,
            summary = "Get the cluster this request is acting for",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        ctx.json(toResponse(requireActive(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster",
            methods = HttpMethod.PUT,
            summary = "Rename the cluster this request is acting for",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterResponse.class)))
    private void update(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterRequest.class);
        clusterService.rename(cluster.id(), request.name(), request.description());
        // Absent means unchanged, so a caller that only wanted to rename does not silently rewire the mesh
        if (request.autoFederate() != null && request.autoFederate() != cluster.autoFederate()) {
            clusterService.setAutoFederate(cluster.id(), request.autoFederate());
        }
        ctx.json(toResponse(clusterService.findById(cluster.id()).orElseThrow(NotFoundResponse::new)));
    }

    @OpenApi(
            path = "/api/v1/clusters/{clusterUid}",
            pathParams = @OpenApiParam(name = "clusterUid", type = String.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Delete a cluster, which must have no stations left",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        Cluster cluster =
                clusterService.findByUid(parseUid(ctx.pathParam("clusterUid"))).orElseThrow(NotFoundResponse::new);
        clusterService.delete(cluster.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Hands a cluster over to somebody who will run it.
     *
     * <p>The one cluster call an instance administrator may make from outside, and the reason it exists: a
     * cluster the instance has just created has nobody in it, and every other cluster call asks for a right
     * that only a member can hold. Without this a new cluster could never get its first person and would sit
     * there unusable. It appoints and nothing more, exactly as handing a station its owner does; who else
     * acts for the cluster afterwards is the cluster's own business.
     */
    @OpenApi(
            path = "/api/v1/clusters/{clusterUid}/administrators",
            pathParams = @OpenApiParam(name = "clusterUid", type = String.class, required = true),
            methods = HttpMethod.POST,
            summary = "Appoint the first person who may act for a cluster",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AppointRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void appointAdministrator(Context ctx) {
        Cluster cluster =
                clusterService.findByUid(parseUid(ctx.pathParam("clusterUid"))).orElseThrow(NotFoundResponse::new);
        var request = ctx.bodyAsClass(AppointRequest.class);
        if (request.accountUid() == null) throw new BadRequestResponse("Name the account to appoint");
        var account = accountRepository
                .findByUid(parseUid(request.accountUid()))
                .orElseThrow(() -> new NotFoundResponse("No such account"));
        clusterService.addMember(cluster.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The cluster the request named. Acting on nothing is a caller error rather than a not-found, because the
     * route exists and the header is what is missing.
     */
    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a cluster identity: " + raw);
        }
    }

    private ClusterResponse toResponse(Cluster cluster) {
        return new ClusterResponse(
                cluster.uid(),
                cluster.name(),
                cluster.description(),
                cluster.homeStationId(),
                cluster.autoFederate(),
                cluster.themeLocked(),
                cluster.colorsLocked(),
                cluster.feelLocked(),
                cluster.logoLocked(),
                cluster.storagePoolBytes());
    }

    /**
     * @param autoFederate whether member stations should be connected to each other, or {@code null} to
     *                     leave the setting as it is
     */
    public record ClusterRequest(String name, String description, Boolean autoFederate) {}

    /**
     * @param accountUid the account to make this cluster's first administrator
     */
    public record AppointRequest(String accountUid) {}

    /**
     * @param homeStationId the shell the cluster owns, on the wire as its station identity
     */
    public record ClusterResponse(
            UUID uid,
            String name,
            String description,
            int homeStationId,
            boolean autoFederate,
            boolean themeLocked,
            boolean colorsLocked,
            boolean feelLocked,
            boolean logoLocked,
            Long storagePoolBytes) {}
}
