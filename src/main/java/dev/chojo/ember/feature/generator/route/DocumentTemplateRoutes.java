/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DateFormatCheck;
import dev.chojo.ember.feature.generator.entity.DateFormatCheckRequest;
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
import dev.chojo.ember.feature.generator.service.TemplateStationUseService;
import dev.chojo.ember.feature.generator.service.TemplateStationUseService.TemplateUseRequest;
import dev.chojo.ember.feature.generator.service.TemplateStationUseService.TemplateUseResponse;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The station's document templates, for whoever may write them.
 *
 * <p>A template holds no member data, so writing one needs only the right to edit templates. Looking
 * at one rendered for a member is generating, which has routes of its own.
 *
 * <p>The list also holds the templates of the station's association, which the station uses but only
 * the association changes. For one of those the station sets whether its members generate it through
 * self service, and who of them ({@code /document-templates/{id}/use}). Any template of the list can be
 * copied into a new one of the station, which the station then changes like its own.
 */
@Singleton
public class DocumentTemplateRoutes implements Routes {
    private final DocumentTemplateService templates;
    private final LetterImportService imports;
    private final PdfTemplateService pdfs;
    private final TemplateStationUseService uses;
    private final DocumentTemplateCopyService copies;

    @Inject
    public DocumentTemplateRoutes(
            DocumentTemplateService templates,
            LetterImportService imports,
            PdfTemplateService pdfs,
            TemplateStationUseService uses,
            DocumentTemplateCopyService copies) {
        this.templates = templates;
        this.imports = imports;
        this.pdfs = pdfs;
        this.uses = uses;
        this.copies = copies;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/document-placeholders", this::catalogue, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(
                prefix + "/document-placeholders/date-format",
                this::checkDateFormat,
                StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(prefix + "/document-template-import", this::importLetter, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/document-templates", this::list, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(prefix + "/document-templates", this::create, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/document-templates/{id}", this::detail, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.put(prefix + "/document-templates/{id}", this::update, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(
                prefix + "/document-templates/{id}/archive", this::archive, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(
                prefix + "/document-templates/{id}/restore", this::restore, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(
                prefix + "/document-templates/{id}/duplicate",
                this::duplicate,
                StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/document-templates/{id}/pdf", this::pdf, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(prefix + "/document-templates/{id}/pdf", this::uploadPdf, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/document-templates/{id}/use", this::use, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.put(prefix + "/document-templates/{id}/use", this::setUse, StationPermission.DOCUMENT_TEMPLATE_EDIT);
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/use",
            methods = HttpMethod.GET,
            summary = "How the station uses a template of its association",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateUseResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void use(Context ctx) {
        ctx.json(uses.useOf(StationSession.from(ctx).stationId(), pathInt(ctx, "id")));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/use",
            methods = HttpMethod.PUT,
            summary = "Set whether and for whom the station offers a template of its association for self service",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TemplateUseRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateUseResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setUse(Context ctx) {
        var request = ctx.bodyAsClass(TemplateUseRequest.class);
        ctx.json(uses.setUse(StationSession.from(ctx).stationId(), pathInt(ctx, "id"), request));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/pdf",
            methods = HttpMethod.GET,
            summary = "The PDF a PDF template fills now, as it was uploaded",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void pdf(Context ctx) {
        var download = pdfs.current(StationSession.from(ctx).owner(), pathInt(ctx, "id"))
                .orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING::raise);
        FileResponse.send(ctx, "application/pdf", download.fileName(), download.data());
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/pdf",
            methods = HttpMethod.POST,
            summary = "Upload a new version of the PDF a PDF template fills, keeping its fields",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadPdf(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(pdfs.upload(session.owner(), pathInt(ctx, "id"), ctx.uploadedFile("file"), session.accountId()));
    }

    @OpenApi(
            path = "/api/v1/document-placeholders",
            methods = HttpMethod.GET,
            summary = "The placeholders a document template of the station can name",
            tags = {"Documents"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = PlaceholderCatalogueResponse.class)))
    private void catalogue(Context ctx) {
        ctx.json(templates.catalogue(StationSession.from(ctx).owner()));
    }

    @OpenApi(
            path = "/api/v1/document-placeholders/date-format",
            methods = HttpMethod.POST,
            summary = "The example day in an own date format, or why the format cannot be printed",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DateFormatCheckRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DateFormatCheck.class)))
    private void checkDateFormat(Context ctx) {
        ctx.json(ctx.bodyAsClass(DateFormatCheckRequest.class).check());
    }

    @OpenApi(
            path = "/api/v1/document-template-import",
            methods = HttpMethod.POST,
            summary =
                    "Read the body of a Word or OpenDocument text into a letter template, gaps in brackets as placeholders",
            tags = {"Documents"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LetterImport.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void importLetter(Context ctx) {
        ctx.json(imports.read(Importer.of(StationSession.from(ctx)), ctx.uploadedFile("file")));
    }

    @OpenApi(
            path = "/api/v1/document-templates",
            methods = HttpMethod.GET,
            summary = "The document templates of the station",
            tags = {"Documents"},
            queryParams =
                    @OpenApiParam(
                            name = "archived",
                            type = Boolean.class,
                            description = "List the archived templates instead of those in use"),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateSummary[].class)))
    private void list(Context ctx) {
        boolean archived = ctx.queryParamAsClass("archived", Boolean.class).getOrDefault(false);
        ctx.json(templates.list(StationSession.from(ctx).owner(), archived));
    }

    @OpenApi(
            path = "/api/v1/document-templates",
            methods = HttpMethod.POST,
            summary = "Create a document template",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DocumentTemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentTemplateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(DocumentTemplateRequest.class);
        ctx.status(HttpStatus.CREATED).json(templates.create(session.owner(), request, session.accountId()));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}",
            methods = HttpMethod.GET,
            summary = "A document template with its letter",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)))
    private void detail(Context ctx) {
        ctx.json(templates.detail(StationSession.from(ctx).owner(), pathInt(ctx, "id")));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}",
            methods = HttpMethod.PUT,
            summary = "Change a document template, counting its version up",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DocumentTemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(DocumentTemplateRequest.class);
        ctx.json(templates.update(session.owner(), pathInt(ctx, "id"), request, session.accountId()));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/archive",
            methods = HttpMethod.POST,
            summary = "Archive a document template, which then generates nothing more",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)))
    private void archive(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(templates.setArchived(session.owner(), pathInt(ctx, "id"), true, session.accountId()));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/restore",
            methods = HttpMethod.POST,
            summary = "Take an archived document template back into use",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateResponse.class)))
    private void restore(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(templates.setArchived(session.owner(), pathInt(ctx, "id"), false, session.accountId()));
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/duplicate",
            methods = HttpMethod.POST,
            summary = "Copy a template of the station, or one of its association, as a new template of the station",
            description =
                    "Everything the template says is copied, the PDF of a PDF template as a file of its own;"
                            + " the generation log and the appointments that ask for it are not. The copy starts at version one.",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DocumentTemplateCopyRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentTemplateCopy.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void duplicate(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(DocumentTemplateCopyRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(copies.duplicate(session.owner(), pathInt(ctx, "id"), request.name(), session.accountId()));
    }
}
