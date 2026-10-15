/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Whom a witness may collect signatures from, and on which fields.
 *
 * <p>A witness holds {@link StationPermission#DOCUMENT_SIGN_OTHERS}. They reach the members and trial
 * users of their own station who are still there, never themselves. A station may narrow that by one
 * audience of its own, set next to the permission ({@link RestrictionType#WITNESS_REACH}); without one the
 * witnesses reach all of them. A document is collected for the person it is about, so that person decides
 * the reach, also where a guardian of theirs signs.
 *
 * <p>The fields a witness collects are the member's own and their guardians', never the issuer's, which
 * keeps its own consent rule. Nobody witnesses a field they would sign themselves: their own, the guardian
 * place they hold, or a field any guardian may sign of a member in their care.
 *
 * <p>Whoever signs in front of the witness is one of the people the field names: the member for their own
 * field, the guardian at that place for a numbered guardian field, any of the member's guardians for a
 * field any guardian may sign.
 */
@Singleton
public class WitnessReach {
    private static final Set<StationUserType> REACHED = Set.of(StationUserType.MEMBER, StationUserType.TRIAL);

    private final StationMemberRepository members;
    private final RestrictionService restrictions;

    @Inject
    public WitnessReach(StationMemberRepository members, RestrictionService restrictions) {
        this.members = members;
        this.restrictions = restrictions;
    }

    /**
     * Refuses a caller who may not collect signatures in person.
     *
     * @param session the caller
     */
    public void requireWitness(StationSession session) {
        if (!session.hasPermission(StationPermission.DOCUMENT_SIGN_OTHERS)) {
            throw DocumentRefusal.SIGNING_WITNESS_OUT_OF_REACH.raise();
        }
    }

    /**
     * Whether the witness reaches a member.
     *
     * @param session the witness
     * @param member  the member a document is about
     * @return whether they may collect signatures for them
     */
    public boolean reaches(StationSession session, StationMember member) {
        if (!withinStation(session, member)) return false;
        var audience = audienceOf(session);
        if (audience.namesNobody()) return true;
        return restrictions.memberOf(member.id()).map(audience::includes).orElse(false);
    }

    /**
     * The members the witness reaches.
     *
     * @param session the witness
     * @return the members, current ones only
     */
    public List<StationMember> reachable(StationSession session) {
        var candidates = members.findByStation(session.stationId()).stream()
                .filter(member -> withinStation(session, member))
                .toList();
        var audience = audienceOf(session);
        if (audience.namesNobody()) return candidates;
        var identities = restrictions.membersOf(
                candidates.stream().map(StationMember::id).toList());
        return candidates.stream()
                .filter(member -> {
                    var identity = identities.get(member.id());
                    return identity != null && audience.includes(identity);
                })
                .toList();
    }

    /**
     * Whom the station's witnesses reach beyond the rule that holds for all of them, as the station set
     * it next to the permission; an audience naming nobody narrows nothing.
     *
     * @param session the witness
     * @return the audience of the witness's station
     */
    private RestrictionAudience audienceOf(StationSession session) {
        return restrictions.audienceOf(RestrictionType.WITNESS_REACH, session.stationId());
    }

    /** The rule for every witness: a current member or trial user of their station, never themselves. */
    private static boolean withinStation(StationSession session, StationMember member) {
        return member.stationId() == session.stationId()
                && !member.former()
                && REACHED.contains(member.userType())
                && member.id() != session.member().id();
    }

    /**
     * The member a document is about, refused where the witness does not reach them.
     *
     * @param session  the witness
     * @param memberId the member
     * @return the member
     */
    public StationMember requireReachable(StationSession session, int memberId) {
        var member = members.findById(memberId).orElseThrow(DocumentRefusal.SIGNING_WITNESS_OUT_OF_REACH::raise);
        if (member.id() == session.member().id()) throw DocumentRefusal.SIGNING_WITNESS_SELF.raise();
        if (!reaches(session, member)) throw DocumentRefusal.SIGNING_WITNESS_OUT_OF_REACH.raise();
        return member;
    }

    /**
     * Refuses a field the witness may not collect: one of the issuer, one of a member out of reach, and one
     * the witness would sign themselves.
     *
     * @param session the witness
     * @param field   the field
     * @return the member the document is about
     */
    public StationMember requireCollectable(StationSession session, RequestedSignature field) {
        if (field.role() == FieldRole.ISSUER) throw DocumentRefusal.SIGNING_WITNESS_FIELD_NOT_OFFERED.raise();
        Integer documentMember = field.memberId();
        if (documentMember == null) throw DocumentRefusal.SIGNING_MEMBER_GONE.raise();
        var member = requireReachable(session, documentMember);
        if (wouldSignThemselves(session.member().id(), field)) throw DocumentRefusal.SIGNING_WITNESS_SELF.raise();
        return member;
    }

    /**
     * Whether a field of a member the witness reaches is one they collect: not the issuer's, and not one
     * they would sign themselves.
     *
     * @param session the witness
     * @param field   the field
     * @return whether it is offered to them
     */
    public boolean offers(StationSession session, RequestedSignature field) {
        return field.role() != FieldRole.ISSUER
                && !wouldSignThemselves(session.member().id(), field);
    }

    /**
     * The people a field names, one of whom signs in front of the witness: the member, the guardian at the
     * place, or every guardian of the member for a field any of them may sign. The witness is never among
     * them.
     *
     * @param session the witness
     * @param field   the field
     * @return the people, none where the field names nobody who can sign
     */
    public List<StationMember> signersOf(StationSession session, RequestedSignature field) {
        int me = session.member().id();
        List<StationMember> named =
                switch (field.role()) {
                    case PARTICIPANT, GUARDIAN -> {
                        Integer signer = field.signerId();
                        yield signer == null
                                ? List.of()
                                : members.findById(signer).stream().toList();
                    }
                    case ANY_GUARDIAN -> {
                        Integer member = field.memberId();
                        yield member == null ? List.of() : members.findManagers(member);
                    }
                    case ISSUER -> List.of();
                };
        return named.stream()
                .filter(person -> !person.former() && person.id() != me)
                .toList();
    }

    /**
     * The person who signs in front of the witness, refused where the field does not name them.
     *
     * @param session  the witness
     * @param field    the field
     * @param signerId the person the witness picked
     * @return the person
     */
    public StationMember requireSigner(StationSession session, RequestedSignature field, int signerId) {
        if (signerId == session.member().id()) throw DocumentRefusal.SIGNING_WITNESS_SELF.raise();
        return signersOf(session, field).stream()
                .filter(person -> person.id() == signerId)
                .findFirst()
                .orElseThrow(DocumentRefusal.SIGNING_WITNESS_SIGNER_NOT_NAMED::raise);
    }

    private boolean wouldSignThemselves(int me, RequestedSignature field) {
        if (Objects.equals(field.signerId(), me)) return true;
        if (field.role() != FieldRole.ANY_GUARDIAN) return false;
        Integer member = field.memberId();
        return member != null && members.findManagers(member).stream().anyMatch(guardian -> guardian.id() == me);
    }
}
