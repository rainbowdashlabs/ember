/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;

import java.util.HashMap;
import java.util.Map;

/**
 * Which templates are meant for which current members of one station, as their audiences say.
 *
 * <p>The station's members are read once, and each template's audience the first time it is asked about,
 * so a list of templates or of participants asks the database a fixed number of times rather than once per
 * template and member. A member who is not a current member of the station is meant for nothing.
 */
public final class TemplateFit {
    /** Reads the audience of a template at the station. */
    @FunctionalInterface
    interface AudienceSource {
        RestrictionAudience of(int templateId, boolean ofAssociation);
    }

    private final Map<Integer, RestrictionMember> members;
    private final AudienceSource source;
    private final Map<Integer, RestrictionAudience> audiences = new HashMap<>();

    TemplateFit(Map<Integer, RestrictionMember> members, AudienceSource source) {
        this.members = members;
        this.source = source;
    }

    /**
     * Whether a template is meant for a member.
     *
     * @param templateId    the template
     * @param ofAssociation whether the template is the association's rather than the station's own
     * @param memberId      the member
     * @return whether the member is a current member of the station the template's audience takes in
     */
    public boolean meantFor(int templateId, boolean ofAssociation, int memberId) {
        var member = members.get(memberId);
        if (member == null) return false;
        return audiences
                .computeIfAbsent(templateId, id -> source.of(id, ofAssociation))
                .includes(member);
    }

    /**
     * Whether a template is meant for a member.
     *
     * @param template the template
     * @param memberId the member
     * @return whether the member is a current member of the station the template's audience takes in
     */
    public boolean meantFor(DocumentTemplate template, int memberId) {
        return meantFor(template.id(), template.ofAssociation(), memberId);
    }
}
