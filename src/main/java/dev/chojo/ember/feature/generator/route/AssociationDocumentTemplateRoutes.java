/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DateFormatCheck;
import dev.chojo.ember.feature.generator.entity.DateFormatCheckRequest;
import dev.chojo.ember.feature.generator.route.DocumentGenerationRoutes.DraftPreviewRequest;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateCopyService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateCopyService.DocumentTemplateCopy;
import dev.chojo.ember.feature.generator.service.DocumentTemplateCopyService.DocumentTemplateCopyRequest;
import dev.chojo.ember.feature.generator.service.DocumentTemplateRequest;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.PlaceholderCatalogueResponse;
import dev.chojo.ember.feature.generator.service.LetterImportService;
import dev.chojo.ember.feature.generator.service.LetterImportService.Importer;
import dev.chojo.ember.feature.generator.service.LetterImportService.LetterImport;
import dev.chojo.ember.feature.generator.service.PdfTemplateService;
import dev.chojo.ember.owner.Owner;
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

import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The document templates an association keeps for all its stations, for whoever may write them there.
 *
 * <p>The same templates, editor and checks as a station's, with the association as the owner, which comes
 * from the session and never from the request. An association has no members of its own, so its template
 * is looked at without one; its stations generate it for their members. Pictures go into the media library
 * of the association's home station.
 */
@Singleton
public class AssociationDocumentTemplateRoutes implements Routes {
    private static final String KEPT_BY_THE_ASSOCIATION =
            "a template of the association is looked up among the association's own, which the session names";

    private final DocumentTemplateService templates;
    private final DocumentGenerationService generation;
    private final LetterImportService imports;
    private final PdfTemplateService pdfs;
    private final DocumentTemplateCopyService copies;

    @Inject
    public AssociationDocumentTemplateRoutes(
            DocumentTemplateService templates,
            DocumentGenerationService generation,
            LetterImportService imports,
            PdfTemplateService pdfs,
            DocumentTemplateCopyService copies) {
        this.templates = templates;
        this.generation = generation;
        this.imports = imports;
        this.pdfs = pdfs;
        this.copies = copies;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        var edit = ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT;
        String base = prefix + "/cluster/document-templates";
        routes.get(prefix + "/cluster/document-placeholders", this::catalogue, edit);
        routes.post(prefix + "/cluster/document-placeholders/date-format", this::checkDateFormat, edit);
        routes.post(prefix + "/cluster/document-template-import", this::importLetter, edit);
        routes.post(prefix + "/cluster/document-template-preview", this::preview, edit);
        routes.get(base, this::list, edit);
        routes.post(base, this::create, edit);
        routes.get(base + "/{id}", this::detail, edit);
        routes.put(base + "/{id}", this::update, edit);
        routes.post(base + "/{id}/archive", this::archive, edit);
        routes.post(base + "/{id}/restore", this::restore, edit);
        routes.post(base + "/{id}/duplicate", this::duplicate, edit);
        routes.get(base + "/{id}/pdf", this::pdf, edit);
        routes.post(base + "/{id}/pdf", this::uploadPdf, edit);
    }

    @OpenApi(
            path = "/api/v1/cluster/document-placeholders",
            methods = HttpMethod.GET,
            summary = "The placeholders a document template of the association can name",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = PlaceholderCatalogueResponse.class)))
    private void catalogue(Context ctx) {
        ctx.json(templates.catalogue(association(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-placeholders/date-format",
            methods = HttpMethod.POST,
            summary = "The example day in an own date format of an association's template, or why it cannot be printed",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DateFormatCheckRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DateFormatCheck.class)))
    private void checkDateFormat(Context ctx) {
        ctx.json(ctx.bodyAsClass(DateFormatCheckRequest.class).check());
    }

    @OpenApi(
            path = "/api/v1/cluster/document-template-import",
            methods = HttpMethod.POST,
            summary = "Read the body of a Word or OpenDocument text into a letter template of the association",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LetterImport.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void importLetter(Context ctx) {
        ctx.json(imports.read(new Importer(association(ctx), null), ctx.uploadedFile("file")));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-template-preview",
            methods = HttpMethod.POST,
            summary = "Draw a document template of the association as the editor holds it, with its placeholders",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DraftPreviewRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PreviewResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void preview(Context ctx) {
        var request = ctx.bodyAsClass(DraftPreviewRequest.class);
        var template = Objects.requireNonNullElseGet(request.template(), DocumentGenerationRoutes::emptyTemplate);
        ctx.json(generation.previewAssociationDraft(
                association(ctx), template, request.templateId(), request.memberId()));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates",
            methods = HttpMethod.GET,
            summary = "The document templates of the association",
            tags = {"Cluster"},
            queryParams =
                    @OpenApiParam(
                            name = "archived",
                            type = Boolean.class,
                            description = "List the archived templates instead of those in use"),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateSummary[].class)))
    private void list(Context ctx) {
        boolean archived = ctx.queryParamAsClass("archived", Boolean.class).getOrDefault(false);
        ctx.json(templates.list(association(ctx), archived));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates",
            methods = HttpMethod.POST,
            summary = "Create a document template of the association",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DocumentTemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentTemplateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(DocumentTemplateRequest.class);
        ctx.status(HttpStatus.CREATED).json(templates.create(association(ctx), request, accountId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}",
            methods = HttpMethod.GET,
            summary = "A document template of the association",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)))
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void detail(Context ctx) {
        ctx.json(templates.detail(association(ctx), pathInt(ctx, "id")));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}",
            methods = HttpMethod.PUT,
            summary = "Change a document template of the association, counting its version up",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DocumentTemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void update(Context ctx) {
        var request = ctx.bodyAsClass(DocumentTemplateRequest.class);
        ctx.json(templates.update(association(ctx), pathInt(ctx, "id"), request, accountId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}/archive",
            methods = HttpMethod.POST,
            summary = "Archive a document template of the association, which its stations then no longer use",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)))
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void archive(Context ctx) {
        ctx.json(templates.setArchived(association(ctx), pathInt(ctx, "id"), true, accountId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}/restore",
            methods = HttpMethod.POST,
            summary = "Take an archived document template of the association back into use",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)))
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void restore(Context ctx) {
        ctx.json(templates.setArchived(association(ctx), pathInt(ctx, "id"), false, accountId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}/duplicate",
            methods = HttpMethod.POST,
            summary = "Copy a document template of the association as a new template of the association",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DocumentTemplateCopyRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentTemplateCopy.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void duplicate(Context ctx) {
        var request = ctx.bodyAsClass(DocumentTemplateCopyRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(copies.duplicate(association(ctx), pathInt(ctx, "id"), request.name(), accountId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}/pdf",
            methods = HttpMethod.GET,
            summary = "The PDF a PDF template of the association fills now, as it was uploaded",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void pdf(Context ctx) {
        var download = pdfs.current(association(ctx), pathInt(ctx, "id"))
                .orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING::raise);
        FileResponse.send(ctx, "application/pdf", download.fileName(), download.data());
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}/pdf",
            methods = HttpMethod.POST,
            summary = "Upload a new version of the PDF a PDF template of the association fills, keeping its fields",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void uploadPdf(Context ctx) {
        ctx.json(pdfs.upload(association(ctx), pathInt(ctx, "id"), ctx.uploadedFile("file"), accountId(ctx)));
    }

    private static Owner.Association association(Context ctx) {
        return UserSession.from(ctx).association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_DOCUMENT_TEMPLATES);
    }

    private static int accountId(Context ctx) {
        return UserSession.from(ctx).accountId();
    }
}
