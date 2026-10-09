/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Whether a template can be the one document every partner's signer signs alike, for an appointment shared
 * with partner stations.
 *
 * <p>The organiser knows nothing of a partner's member but an id: no name, no guardians, no answers. So such a
 * document is drawn once per date and about nobody in particular, and every signer signs the same content.
 * That holds only for a template that
 * <ul>
 *     <li>names the appointment, today's date, the station and its association, and no person: no member, no
 *     guardian, no issuer, nobody who generated it, no answer to a question. That holds for what the document
 *     prints and its title; the file name it is filed under is no part of the signed bytes, and a value of a
 *     person there stays empty;</li>
 *     <li>reads the same for everybody: no block of a letter is meant for some members only or depends on the
 *     guardians a member has;</li>
 *     <li>asks only signers whose field stands in every member's document: the participant, the first
 *     guardian and any one guardian. A second guardian's field or one per guardian depends on the member, and
 *     the issuer is a member of the organiser.</li>
 * </ul>
 *
 * <p>Only a template that asks the member or a guardian to sign is such an agreement; a document to bring
 * that asks no signature from them does not travel to partners at all.
 */
@Singleton
public class MemberNeutralTemplates {
    private static final Set<SignatureRole> SHARED_SIGNERS =
            Set.of(SignatureRole.PARTICIPANT, SignatureRole.GUARDIAN_1, SignatureRole.ANY_GUARDIAN);

    private final DocumentTemplateService templates;

    @Inject
    public MemberNeutralTemplates(DocumentTemplateService templates) {
        this.templates = templates;
    }

    /**
     * @param template a template
     * @return whether its document asks the member or a guardian to sign, which is what makes it an agreement
     *         partners are asked to sign
     */
    public boolean asksMemberSideToSign(DocumentTemplate template) {
        return signers(templates.contentOf(template)).anyMatch(role -> role != SignatureRole.ISSUER);
    }

    /**
     * @param template a template
     * @return whether partners can sign its document: it asks the member side to sign and is about nobody
     *         in particular
     */
    public boolean signableByPartners(DocumentTemplate template) {
        return asksMemberSideToSign(template) && refusalOf(template).isEmpty();
    }

    /**
     * Refuses a template that asks the member side to sign but is no document about nobody in particular. A
     * template that asks them nothing passes, since it never travels.
     *
     * @param template the template
     */
    public void requireSignableByPartners(DocumentTemplate template) {
        if (!asksMemberSideToSign(template)) return;
        refusalOf(template).ifPresent(refusal -> {
            throw refusal.raise(RefusalDetail.text(template.name()));
        });
    }

    private Optional<DocumentRefusal> refusalOf(DocumentTemplate template) {
        var content = templates.contentOf(template);
        var keys = PlaceholderCatalogue.keysOf(template.titlePattern(), "", content.texts());
        if (!keys.stream().allMatch(MemberNeutralTemplates::namesNobody)) {
            return Optional.of(DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON);
        }
        if (cells(content).anyMatch(MemberNeutralTemplates::dependsOnTheMember)) {
            return Optional.of(DocumentRefusal.PARTNER_AGREEMENT_DEPENDS_ON_THE_MEMBER);
        }
        if (!signers(content).allMatch(SHARED_SIGNERS::contains)) {
            return Optional.of(DocumentRefusal.PARTNER_AGREEMENT_SIGNER_NOT_SHARED);
        }
        return Optional.empty();
    }

    private static boolean namesNobody(String key) {
        return BuiltInPlaceholder.of(key)
                .filter(BuiltInPlaceholder::needsNoPerson)
                .isPresent();
    }

    private static boolean dependsOnTheMember(ContentCell cell) {
        return cell.restriction() != null || cell.guardianCondition() != null;
    }

    private static Stream<SignatureRole> signers(TemplateContent content) {
        return switch (content) {
            case LetterContent letter ->
                cells(letter).map(MemberNeutralTemplates::signerOf).filter(Objects::nonNull);
            case PdfContent pdf ->
                pdf.layout().fields().stream().map(PdfField::role).filter(Objects::nonNull);
        };
    }

    private static @Nullable SignatureRole signerOf(ContentCell cell) {
        return cell.config() instanceof CellConfig.SignatureConfig signature ? signature.signer() : null;
    }

    private static Stream<ContentCell> cells(TemplateContent content) {
        if (!(content instanceof LetterContent letter)) return Stream.empty();
        return Stream.of(letter.header(), letter.footer(), letter.body()).flatMap(MemberNeutralTemplates::blocksOf);
    }

    /** Every block of some rows, the stacks themselves and the blocks stacked in them. */
    private static Stream<ContentCell> blocksOf(List<ContentRow> rows) {
        return rows.stream().flatMap(row -> row.cells().stream()).flatMap(cell -> {
            if (cell.config() instanceof CellConfig.NestedRowsConfig nested) {
                return Stream.concat(Stream.of(cell), blocksOf(ContentRows.read(nested.rows())));
            }
            return Stream.of(cell);
        });
    }
}
