/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.TemplateStationUse;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Who issues the documents of a template at a station, and whom a station may name for it.
 *
 * <p>A station's template names its issuer itself; a template of an association names none, and each
 * station that uses it names its own in how it uses it ({@link TemplateStationUseService}), since an
 * association has no members to name. A manager who generates a document, or starts a run, may pick
 * another member for that occasion. Whoever is named has to be a current member of the station; a name
 * that is not is refused with {@link DocumentRefusal#DOCUMENT_ISSUER_NOT_HERE}. An issuer who leaves or is
 * deleted afterwards is not refused anywhere: the documents then miss the issuer's name like any other
 * value.
 */
@Singleton
public class DocumentIssuerService {
    /** The longest function an issuer may be given. */
    static final int MAX_FUNCTION = 80;

    private final StationMemberRepository members;
    private final TemplateStationUseRepository uses;

    @Inject
    public DocumentIssuerService(StationMemberRepository members, TemplateStationUseRepository uses) {
        this.members = members;
        this.uses = uses;
    }

    /**
     * An issuer a manager picks for one document or one run.
     *
     * @param memberId the member of the station who issues it
     * @param function what they do at the station, or null where nothing is said
     */
    public record IssuerChoice(int memberId, @Nullable String function) {}

    /**
     * The issuer a template names for its owner, checked. The issuer it named already is kept unchecked,
     * so a template whose issuer has left can still be saved; its documents miss the issuer's name until
     * another is named.
     *
     * @param owner    the station or the association that keeps the template
     * @param memberId the member named, or null for nobody
     * @param function what they do, or null
     * @param kept     the member the template named before, or null
     * @return the issuer, its function trimmed and an empty one dropped
     */
    public DocumentIssuer checked(
            Owner owner, @Nullable Integer memberId, @Nullable String function, @Nullable Integer kept) {
        if (memberId != null && !memberId.equals(kept)) {
            if (!(owner instanceof Owner.Station station)) throw DocumentRefusal.DOCUMENT_ISSUER_NOT_HERE.raise();
            requireCurrentMember(station.stationId(), memberId);
        }
        return drafted(memberId, function);
    }

    /**
     * The issuer of a template still in the editor, unchecked: a look at a draft shows an issuer who is no
     * current member as missing rather than refusing it.
     *
     * @param memberId the member named, or null for nobody
     * @param function what they do, or null
     * @return the issuer, its function trimmed and an empty one dropped
     */
    public static DocumentIssuer drafted(@Nullable Integer memberId, @Nullable String function) {
        return DocumentIssuer.ofTemplate(memberId, function(function));
    }

    /**
     * The issuer of a template at a station: the template's own for a station's template, and what the
     * station named for one of its association, nobody where it named none.
     *
     * @param template  the template
     * @param stationId the station that generates it
     * @return the issuer the template names there
     */
    public DocumentIssuer ofTemplate(DocumentTemplate template, int stationId) {
        if (!template.ofAssociation()) return template.issuer();
        return uses.find(template.id(), stationId)
                .map(TemplateStationUse::issuer)
                .orElse(DocumentIssuer.NONE);
    }

    /**
     * The issuer of a document a manager generates: the template's where the manager picked nobody else.
     *
     * @param template  the template
     * @param stationId the station that generates it
     * @param picked    the member the manager picked instead, or null to keep the template's
     * @return the issuer
     */
    public DocumentIssuer forManager(DocumentTemplate template, int stationId, @Nullable IssuerChoice picked) {
        if (picked == null) return ofTemplate(template, stationId);
        requireCurrentMember(stationId, picked.memberId());
        return DocumentIssuer.picked(picked.memberId(), function(picked.function()));
    }

    private void requireCurrentMember(int stationId, int memberId) {
        boolean current = members.findById(memberId)
                .filter(member -> member.stationId() == stationId && !member.former())
                .isPresent();
        if (!current) throw DocumentRefusal.DOCUMENT_ISSUER_NOT_HERE.raise();
    }

    private static @Nullable String function(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String function = raw.strip();
        if (function.length() > MAX_FUNCTION) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(MAX_FUNCTION));
        }
        return function;
    }
}
