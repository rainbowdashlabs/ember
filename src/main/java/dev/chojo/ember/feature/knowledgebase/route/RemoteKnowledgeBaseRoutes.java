/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.RemoteKbBrowse;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.RemoteKbSearchResultItem;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-server knowledge-base routes. A federated partner reads what this station shares with
 * it and writes comments on those files, through the serving functions of
 * {@code KnowledgeBaseFederationService}; the caller is the partner verified from the request
 * signature, never a logged-in user.
 */
@Singleton
public class RemoteKnowledgeBaseRoutes implements Routes {

    public static final FederationEndpoint BROWSE_KB =
            FederationEndpoint.get(FederationSurface.KB_SHARE, "/remote/kb/browse", RemoteKbBrowse.class);
    public static final FederationEndpoint BROWSE_KB_FOLDER =
            FederationEndpoint.get(FederationSurface.KB_SHARE, "/remote/kb/folders/{id}/browse", RemoteKbBrowse.class);
    public static final FederationEndpoint SEARCH_KB =
            FederationEndpoint.getList(FederationSurface.KB_SHARE, "/remote/kb/search", RemoteKbSearchResultItem.class);
    public static final FederationEndpoint GET_FILE =
            FederationEndpoint.get(FederationSurface.KB_SHARE, "/remote/kb/files/{id}", RemoteKbFile.class);
    public static final FederationEndpoint GET_FILE_CONTENT = FederationEndpoint.get(
            FederationSurface.KB_SHARE, "/remote/kb/files/{id}/content", FileContentResponse.class);
    public static final FederationEndpoint LIST_COMMENTS = FederationEndpoint.getList(
            FederationSurface.KB_SHARE, "/remote/kb/files/{fileId}/comments", CommentResponse.class);
    public static final FederationEndpoint CREATE_COMMENT = FederationEndpoint.post(
            FederationSurface.KB_SHARE,
            "/remote/kb/files/{fileId}/comments",
            RemoteKbCommentRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint UPDATE_COMMENT = FederationEndpoint.put(
            FederationSurface.KB_SHARE,
            "/remote/kb/comments/{commentId}",
            RemoteKbCommentUpdateRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint DELETE_COMMENT = FederationEndpoint.delete(
            FederationSurface.KB_SHARE,
            "/remote/kb/comments/{commentId}",
            RemoteKbCommentDeleteRequest.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            BROWSE_KB,
            BROWSE_KB_FOLDER,
            SEARCH_KB,
            GET_FILE,
            GET_FILE_CONTENT,
            LIST_COMMENTS,
            CREATE_COMMENT,
            UPDATE_COMMENT,
            DELETE_COMMENT);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteKnowledgeBaseRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(BROWSE_KB)
                .serve(BROWSE_KB_FOLDER)
                .serve(SEARCH_KB)
                .serve(GET_FILE)
                .serve(GET_FILE_CONTENT)
                .serve(LIST_COMMENTS)
                .serveCreated(CREATE_COMMENT)
                .serve(UPDATE_COMMENT)
                .serve(DELETE_COMMENT));
    }

    /**
     * A knowledge-base file as served to a requesting partner. It deliberately does not reuse the
     * {@link KbFile} entity: that record carries this instance's internal numeric station id, which
     * the API layer rewrites into a UUID on the way out and cannot map back on the way in, and the
     * folder, position and restriction fields mean nothing to the partner reading it.
     */
    public record RemoteKbFile(
            int id,
            UUID stationUid,
            String name,
            String description,
            KbFileType fileType,
            @Nullable String mimeType,
            long fileSize,
            @Nullable String youtubeUrl,
            @Nullable String linkUrl,
            Instant createdAt,
            Instant updatedAt,
            @Nullable ConversionStatus conversionStatus) {

        public static RemoteKbFile of(KbFile file, UUID stationUid) {
            return new RemoteKbFile(
                    file.id(),
                    stationUid,
                    file.name(),
                    file.description(),
                    file.fileType(),
                    file.mimeType(),
                    file.fileSize(),
                    file.youtubeUrl(),
                    file.linkUrl(),
                    file.createdAt(),
                    file.updatedAt(),
                    file.conversionStatus());
        }
    }

    public record FileContentResponse(int fileId, String content) {}

    public record RemoteKbCommentRequest(UUID remoteMemberUid, String displayName, Integer parentId, String content) {}

    public record RemoteKbCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteKbCommentDeleteRequest(UUID remoteMemberUid) {}
}
