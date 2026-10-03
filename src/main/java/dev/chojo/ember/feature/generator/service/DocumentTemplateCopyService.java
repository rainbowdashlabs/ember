/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

/**
 * Copies a document template, so a template that works can be the start of the next one.
 *
 * <p>The copy is a new template of whoever asks for it, starting at version one and in use. It carries
 * everything the template says: how its documents are filed, its flags, its language, its letter or the
 * fields over its PDF, and its self service with the wait and the audience. The PDF of a PDF template is
 * stored again for the copy, in its owner's storage and counted against its owner's room. What the
 * template did is not copied: the generation log, the self service waits it set off, the appointments
 * that ask for it.
 *
 * <p>A station copies its own templates and those of its association it uses; the copy of an
 * association's template is the station's own, which it changes like any other, and takes the self
 * service and the issuer the station chose for the original. A station's copy of its own template keeps
 * the template's issuer. An association copies its own templates. Nothing is
 * checked again: fonts and pictures the new owner does not reach print as they would anywhere they
 * are missing, and the answer names them so the editor can say so.
 */
@Singleton
public class DocumentTemplateCopyService {
    private static final Logger log = LoggerFactory.getLogger(DocumentTemplateCopyService.class);

    private final DocumentTemplateService templates;
    private final PdfTemplateService pdfs;
    private final TemplateStationUseService stationUses;
    private final TemplateChecks checks;
    private final LetterChecks letters;
    private final FontLibrary fonts;
    private final OwnerStores stores;
    private final DocumentIssuerService issuers;

    @Inject
    public DocumentTemplateCopyService(
            DocumentTemplateService templates,
            PdfTemplateService pdfs,
            TemplateStationUseService stationUses,
            TemplateChecks checks,
            LetterChecks letters,
            FontLibrary fonts,
            OwnerStores stores,
            DocumentIssuerService issuers) {
        this.templates = templates;
        this.pdfs = pdfs;
        this.stationUses = stationUses;
        this.checks = checks;
        this.letters = letters;
        this.fonts = fonts;
        this.stores = stores;
        this.issuers = issuers;
    }

    /**
     * Copies a template for the owner.
     *
     * @param owner      the station or the association the copy is for
     * @param templateId the template to copy
     * @param name       the name the screen worded for the copy, counted on where another template
     *                   carries it
     * @param authorId   the account that makes the copy
     * @return the copy, with what of it the owner cannot print
     */
    public DocumentTemplateCopy duplicate(Owner owner, int templateId, @Nullable String name, int authorId) {
        var source = templates.requireCopyable(owner, templateId);
        var content = templates.contentOf(source);
        var selfService = selfServiceFor(owner, source);
        var issuer = issuerFor(owner, source);
        var draft = new DocumentTemplateDraft(
                checks.freeCopyName(owner, name),
                source.titlePattern(),
                source.fileNamePattern(),
                source.tags(),
                source.hidden(),
                source.keepOnArchive(),
                source.legal(),
                source.forAppointments(),
                selfService.offered(),
                source.cooldownDays(),
                selfService.audience().mode(),
                source.language(),
                issuer.memberId(),
                issuer.function(),
                content);
        var copy = Transactions.call(() -> {
            var written = templates.write(owner, draft, selfService.audience(), authorId);
            pdfs.copyCurrent(source, owner, written.id(), authorId);
            return written;
        });
        log.info("Document template {} copied as {} for {}", source.id(), copy.id(), owner);
        return new DocumentTemplateCopy(
                templates.detail(owner, copy.id()),
                fonts.outOfReach(owner, content),
                picturesOutOfReach(owner, content));
    }

    /**
     * The self service a copy starts with: the template's own where the owner copies one of its own, and
     * for a template of the association copied by a station what the station chose for it.
     */
    private SelfService selfServiceFor(Owner owner, DocumentTemplate source) {
        if (source.owner().equals(owner) || !(owner instanceof Owner.Station station)) {
            return new SelfService(source.selfService(), templates.audienceOf(source));
        }
        var use = stationUses.useOf(station.stationId(), source.id());
        return new SelfService(use.offered() && use.selfService(), use.audience());
    }

    /**
     * The issuer a copy starts with: for a station the one the template names at that station, its own
     * template's or what it chose for one of its association, and nobody for an association, which names
     * none.
     */
    private DocumentIssuer issuerFor(Owner owner, DocumentTemplate source) {
        if (!(owner instanceof Owner.Station station)) return DocumentIssuer.NONE;
        return issuers.ofTemplate(source, station.stationId());
    }

    /** How many pictures of a letter are not in the media library the owner's templates draw on. */
    private int picturesOutOfReach(Owner owner, TemplateContent content) {
        if (!(content instanceof LetterContent letter)) return 0;
        int library = stores.libraryOf(owner);
        return (int) Stream.of(letter.header(), letter.footer(), letter.body())
                .flatMap(LetterContent::blocks)
                .filter(block -> block.contentType() == CellContentType.IMAGE)
                .filter(block -> !letters.pictureReachable(library, block.content()))
                .count();
    }

    private record SelfService(boolean offered, RestrictionAudience audience) {}

    /**
     * The name a screen words for a copy, in the reader's language.
     *
     * @param name what the copy is to be called, counted on where another template carries it
     */
    public record DocumentTemplateCopyRequest(@Nullable String name) {}

    /**
     * A copy of a template.
     *
     * @param template           the copy as written
     * @param fontsOutOfReach    the font families it names that its owner cannot print with
     * @param picturesOutOfReach how many of its pictures are not in its owner's media library
     */
    public record DocumentTemplateCopy(
            DocumentTemplateResponse template, List<String> fontsOutOfReach, int picturesOutOfReach) {}
}
