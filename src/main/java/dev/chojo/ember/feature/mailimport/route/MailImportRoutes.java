/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.mailimport.entity.MailImportEntry;
import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import dev.chojo.ember.feature.mailimport.service.ContentSniffer;
import dev.chojo.ember.feature.mailimport.service.MailboxService;
import dev.chojo.ember.feature.mailimport.service.MailboxService.TestResult;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
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

import java.time.Instant;
import java.util.List;

/**
 * The mailboxes a station reads for paperwork, the rules under them, and what actually happened.
 *
 * <p>Two permissions, because they are two different rights. Connecting a mailbox is a mail setting and
 * takes the station's mail permission, the same as the sending side. Writing a rule puts files into the
 * document store and says which member they land on, so it takes the permission for member paperwork as
 * well: the right to connect a mailbox is not the right to file documents with it. The log is readable
 * with document read, since it says what arrived and from whom rather than what is in it.
 *
 * <p>The whole feature rides on the documents module. Module off, no import and no page.
 */
@Singleton
public class MailImportRoutes implements Routes {
    private static final int PAGE_SIZE = 50;

    private final MailboxService mailboxService;
    private final StationService stationService;
    private final MailImport settings;

    @Inject
    public MailImportRoutes(MailboxService mailboxService, StationService stationService, MailImport settings) {
        this.mailboxService = mailboxService;
        this.stationService = stationService;
        this.settings = settings;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/mail-import/settings", this::instanceSettings, StationPermission.STATION_MAIL);
        routes.get(prefix + "/station/mail-import/mailboxes", this::listMailboxes, StationPermission.STATION_MAIL);
        routes.post(prefix + "/station/mail-import/mailboxes", this::createMailbox, StationPermission.STATION_MAIL);
        routes.put(prefix + "/station/mail-import/mailboxes/{id}", this::updateMailbox, StationPermission.STATION_MAIL);
        routes.put(
                prefix + "/station/mail-import/mailboxes/{id}/password",
                this::updatePassword,
                StationPermission.STATION_MAIL);
        routes.delete(
                prefix + "/station/mail-import/mailboxes/{id}", this::deleteMailbox, StationPermission.STATION_MAIL);
        routes.post(prefix + "/station/mail-import/mailboxes/{id}/test", this::test, StationPermission.STATION_MAIL);
        routes.post(prefix + "/station/mail-import/mailboxes/{id}/run", this::runNow, StationPermission.STATION_MAIL);
        routes.post(
                prefix + "/station/mail-import/mailboxes/{id}/resume", this::resume, StationPermission.STATION_MAIL);

        routes.get(
                prefix + "/station/mail-import/mailboxes/{id}/rules", this::listRules, StationPermission.STATION_MAIL);
        routes.post(
                prefix + "/station/mail-import/mailboxes/{id}/rules", this::createRule, StationPermission.STATION_MAIL);
        routes.put(prefix + "/station/mail-import/rules/{ruleId}", this::updateRule, StationPermission.STATION_MAIL);
        routes.delete(prefix + "/station/mail-import/rules/{ruleId}", this::deleteRule, StationPermission.STATION_MAIL);

        routes.get(prefix + "/station/mail-import/log", this::log, StationPermission.DOCUMENT_READ);
    }

    /**
     * What the operator has decided, which the page needs before it can offer anything sensible.
     *
     * <p>The floor and the kill switch are not the station's to set, and a page that did not know them
     * would offer an interval the server then quietly overrode, which is the worst of both.
     */
    @OpenApi(
            path = "/api/v1/station/mail-import/settings",
            methods = HttpMethod.GET,
            summary = "What the operator decided about mail import",
            tags = {"Mail Import"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceSettingsResponse.class)))
    private void instanceSettings(Context ctx) {
        requireModule(stationOf(ctx));
        ctx.json(new InstanceSettingsResponse(
                settings.enabled(),
                settings.minimumIntervalMinutes(),
                settings.maxAttachmentsPerCycle(),
                settings.logRetentionDays(),
                mailboxService.canStorePasswords(),
                ContentSniffer.SUPPORTED_TYPES));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes",
            methods = HttpMethod.GET,
            summary = "List the station's mailboxes",
            tags = {"Mail Import"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailboxResponse[].class)))
    private void listMailboxes(Context ctx) {
        int stationId = stationOf(ctx);
        requireModule(stationId);
        ctx.json(mailboxService.findByStation(stationId).stream()
                .map(MailImportRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes",
            methods = HttpMethod.POST,
            summary = "Connect a mailbox",
            tags = {"Mail Import"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MailboxRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = MailboxResponse.class)))
    private void createMailbox(Context ctx) {
        int stationId = stationOf(ctx);
        requireModule(stationId);
        var request = ctx.bodyValidator(MailboxRequest.class).get();
        var mailbox = mailboxService.create(
                stationId,
                request.name(),
                request.host(),
                request.port(),
                request.security(),
                request.username(),
                request.password(),
                request.folder(),
                request.verifyDkim(),
                request.intervalMinutes(),
                request.importFrom());
        ctx.status(HttpStatus.CREATED).json(toResponse(mailbox));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a mailbox",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MailboxRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailboxResponse.class)))
    private void updateMailbox(Context ctx) {
        var mailbox = requireOwned(ctx);
        var request = ctx.bodyValidator(MailboxRequest.class).get();
        mailboxService.update(
                mailbox.id(),
                request.name(),
                request.host(),
                request.port(),
                request.security(),
                request.username(),
                request.folder(),
                request.verifyDkim(),
                request.enabled(),
                request.intervalMinutes(),
                request.importFrom());
        ctx.json(toResponse(mailboxService.require(mailbox.id())));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}/password",
            methods = HttpMethod.PUT,
            summary = "Replace a mailbox's password",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PasswordRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updatePassword(Context ctx) {
        var mailbox = requireOwned(ctx);
        var request = ctx.bodyValidator(PasswordRequest.class).get();
        mailboxService.updatePassword(mailbox.id(), request.password());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}",
            methods = HttpMethod.DELETE,
            summary = "Disconnect a mailbox",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteMailbox(Context ctx) {
        var mailbox = requireOwned(ctx);
        mailboxService.delete(mailbox.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Connects, lists the folders, and sends nothing.
     *
     * <p>Reporting the folders is the point rather than a nicety: the commonest mistake in setting one of
     * these up is a folder spelled the way the person says it rather than the way the provider does, and a
     * test that only said "connected" would not catch it.
     */
    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}/test",
            methods = HttpMethod.POST,
            summary = "Test a mailbox connection and list its folders",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestResult.class)))
    private void test(Context ctx) {
        var mailbox = requireOwned(ctx);
        ctx.json(mailboxService.test(mailbox));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}/run",
            methods = HttpMethod.POST,
            summary = "Read a mailbox now",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CycleResponse.class)))
    private void runNow(Context ctx) {
        var mailbox = requireOwned(ctx);
        var cycle = mailboxService.runNow(mailbox, Instant.now());
        ctx.json(new CycleResponse(cycle.looked(), cycle.imported(), cycle.refused()));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}/resume",
            methods = HttpMethod.POST,
            summary = "Resume a suspended mailbox",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailboxResponse.class)))
    private void resume(Context ctx) {
        var mailbox = requireOwned(ctx);
        mailboxService.resume(mailbox.id());
        ctx.json(toResponse(mailboxService.require(mailbox.id())));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}/rules",
            methods = HttpMethod.GET,
            summary = "List the rules of a mailbox",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RuleResponse[].class)))
    private void listRules(Context ctx) {
        var mailbox = requireOwned(ctx);
        ctx.json(mailboxService.rulesOf(mailbox.id()).stream()
                .map(MailImportRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/mailboxes/{id}/rules",
            methods = HttpMethod.POST,
            summary = "Add a rule to a mailbox",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RuleRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = RuleResponse.class)))
    private void createRule(Context ctx) {
        var mailbox = requireOwned(ctx);
        requireMayFile(UserSession.from(ctx));
        var request = ctx.bodyValidator(RuleRequest.class).get();
        ctx.status(HttpStatus.CREATED).json(toResponse(mailboxService.createRule(mailbox, request)));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/rules/{ruleId}",
            methods = HttpMethod.PUT,
            summary = "Update a mailbox rule",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "ruleId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RuleRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RuleResponse.class)))
    private void updateRule(Context ctx) {
        var rule = requireOwnedRule(ctx);
        requireMayFile(UserSession.from(ctx));
        var request = ctx.bodyValidator(RuleRequest.class).get();
        mailboxService.updateRule(rule.id(), request);
        ctx.json(toResponse(mailboxService.requireRule(rule.id())));
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/rules/{ruleId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a mailbox rule",
            tags = {"Mail Import"},
            pathParams = @OpenApiParam(name = "ruleId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteRule(Context ctx) {
        var rule = requireOwnedRule(ctx);
        requireMayFile(UserSession.from(ctx));
        mailboxService.deleteRule(rule.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station/mail-import/log",
            methods = HttpMethod.GET,
            summary = "What became of each attachment the mailboxes read",
            tags = {"Mail Import"},
            queryParams = {
                @OpenApiParam(name = "page", type = Integer.class),
                @OpenApiParam(name = "size", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LogPageResponse.class)))
    private void log(Context ctx) {
        int stationId = stationOf(ctx);
        requireModule(stationId);
        int size = Math.clamp(ctx.queryParamAsClass("size", Integer.class).getOrDefault(PAGE_SIZE), 1, 200);
        int page = Math.max(ctx.queryParamAsClass("page", Integer.class).getOrDefault(0), 0);
        ctx.json(new LogPageResponse(
                mailboxService.log(stationId, size, page * size).stream()
                        .map(MailImportRoutes::toResponse)
                        .toList(),
                mailboxService.countLog(stationId)));
    }

    private int stationOf(Context ctx) {
        return UserSession.from(ctx).stationId();
    }

    /**
     * A rule decides which member an attachment lands on, so writing one takes the right to manage the
     * documents that name a member and not only the right to connect a mailbox.
     */
    private void requireMayFile(UserSession session) {
        if (!session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) {
            throw Refusal.DOCUMENT_NOT_YOURS_TO_FILE.raise();
        }
    }

    private void requireModule(int stationId) {
        if (stationService.findDisabledModules(stationId).contains(StationModule.DOCUMENTS)) {
            throw Refusal.MAIL_IMPORT_SWITCHED_OFF.raise();
        }
    }

    /** A mailbox of another station does not exist as far as this station is concerned. */
    private MailMailbox requireOwned(Context ctx) {
        int stationId = stationOf(ctx);
        requireModule(stationId);
        var mailbox =
                mailboxService.find(ctx.pathParamAsClass("id", Integer.class).get());
        if (mailbox.isEmpty() || mailbox.get().stationId() != stationId) throw Refusal.MAILBOX_NOT_HERE.raise();
        return mailbox.get();
    }

    private MailRule requireOwnedRule(Context ctx) {
        int stationId = stationOf(ctx);
        requireModule(stationId);
        var rule = mailboxService.findRule(
                ctx.pathParamAsClass("ruleId", Integer.class).get());
        if (rule.isEmpty()) throw Refusal.MAILBOX_RULE_NOT_HERE.raise();
        var mailbox = mailboxService.find(rule.get().mailboxId());
        if (mailbox.isEmpty() || mailbox.get().stationId() != stationId) throw Refusal.MAILBOX_RULE_NOT_HERE.raise();
        return rule.get();
    }

    /**
     * A mailbox as the page reads it. The password is not among the fields and never is: it goes in and is
     * never handed back, not even as a length.
     */
    private static MailboxResponse toResponse(MailMailbox mailbox) {
        return new MailboxResponse(
                mailbox.id(),
                mailbox.name(),
                mailbox.host(),
                mailbox.port(),
                mailbox.security(),
                mailbox.username(),
                mailbox.folder(),
                mailbox.verifyDkim(),
                mailbox.enabled(),
                mailbox.intervalMinutes(),
                mailbox.importFrom(),
                mailbox.lastCheckAt(),
                mailbox.lastError(),
                mailbox.failureCount(),
                mailbox.suspended());
    }

    private static RuleResponse toResponse(MailRule rule) {
        return new RuleResponse(
                rule.id(),
                rule.mailboxId(),
                rule.name(),
                rule.position(),
                rule.enabled(),
                rule.subjectFilter(),
                rule.attachmentNameFilter(),
                rule.acceptedTypes(),
                rule.minSizeBytes(),
                rule.includeInline(),
                rule.titleSource(),
                rule.hidden(),
                rule.keepOnArchive(),
                rule.readSubjectForMember(),
                rule.action(),
                rule.moveToFolder(),
                rule.senderPatterns(),
                rule.tags());
    }

    private static LogEntryResponse toResponse(MailImportEntry entry) {
        return new LogEntryResponse(
                entry.id(),
                entry.mailboxId(),
                entry.ruleId(),
                entry.ruleName(),
                entry.sender(),
                entry.subject(),
                entry.attachmentName(),
                entry.outcome(),
                entry.reason(),
                entry.documentId(),
                entry.pruned(),
                entry.createdAt());
    }

    /**
     * What the operator decided, so the page can say why it will not let somebody ask for one minute.
     *
     * @param canStorePasswords whether an encryption key is configured at all. Without one a mailbox
     *                          cannot be saved, and the page says so rather than storing a password in
     *                          the clear
     */
    public record InstanceSettingsResponse(
            boolean enabled,
            int minimumIntervalMinutes,
            int maxAttachmentsPerCycle,
            int logRetentionDays,
            boolean canStorePasswords,
            List<String> supportedTypes) {}

    public record MailboxResponse(
            int id,
            String name,
            String host,
            int port,
            MailSecurity security,
            String username,
            String folder,
            boolean verifyDkim,
            boolean enabled,
            int intervalMinutes,
            Instant importFrom,
            @Nullable Instant lastCheckAt,
            @Nullable String lastError,
            int failureCount,
            boolean suspended) {}

    /**
     * A mailbox as it arrives over the wire.
     *
     * @param password   the password in the clear, which is the only moment it exists as text on this
     *                   side. Null on an update, which leaves whatever is stored alone
     * @param verifyDkim whether a message has to carry a signature of the domain its address claims.
     *                   Off unless the station asks for it, since a correspondent who does not sign
     *                   would be refused
     */
    public record MailboxRequest(
            String name,
            String host,
            int port,
            MailSecurity security,
            String username,
            @Nullable String password,
            String folder,
            boolean verifyDkim,
            boolean enabled,
            int intervalMinutes,
            Instant importFrom) {}

    public record PasswordRequest(String password) {}

    public record RuleResponse(
            int id,
            int mailboxId,
            String name,
            int position,
            boolean enabled,
            @Nullable String subjectFilter,
            @Nullable String attachmentNameFilter,
            List<String> acceptedTypes,
            long minSizeBytes,
            boolean includeInline,
            MailTitleSource titleSource,
            boolean hidden,
            boolean keepOnArchive,
            boolean readSubjectForMember,
            MailRuleAction action,
            @Nullable String moveToFolder,
            List<String> senderPatterns,
            List<String> tags) {}

    public record RuleRequest(
            String name,
            int position,
            boolean enabled,
            @Nullable String subjectFilter,
            @Nullable String attachmentNameFilter,
            List<String> acceptedTypes,
            long minSizeBytes,
            boolean includeInline,
            MailTitleSource titleSource,
            boolean hidden,
            boolean keepOnArchive,
            boolean readSubjectForMember,
            MailRuleAction action,
            @Nullable String moveToFolder,
            List<String> senderPatterns,
            List<String> tags) {}

    public record LogEntryResponse(
            int id,
            int mailboxId,
            @Nullable Integer ruleId,
            @Nullable String ruleName,
            @Nullable String sender,
            @Nullable String subject,
            @Nullable String attachmentName,
            MailImportOutcome outcome,
            @Nullable String reason,
            @Nullable Integer documentId,
            boolean pruned,
            Instant createdAt) {}

    public record LogPageResponse(List<LogEntryResponse> entries, int total) {}

    public record CycleResponse(int looked, int imported, int refused) {}
}
