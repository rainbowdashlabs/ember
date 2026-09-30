/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import io.javalin.openapi.OpenApiName;

import java.util.List;

/**
 * A change to groups refused because of the members it would break a rule for, naming each of them.
 *
 * <p>A set allows a member in one of its groups and a binding allows only some user types. Where a
 * change would break either for somebody, the screen has to say for whom, so the manager can open
 * those members and decide. A sentence alone would leave them searching.
 */
public class GroupRuleRefused extends RefusalResponse {
    private final transient List<Conflict> conflicts;

    private GroupRuleRefused(Refusal refusal, List<Conflict> conflicts) {
        super(refusal, refusal.message());
        this.conflicts = List.copyOf(conflicts);
    }

    /**
     * Refuses a change for the members it would break a rule for.
     *
     * @param refusal   what refused the change
     * @param conflicts the members concerned, one entry each
     * @return the refusal to throw
     */
    public static GroupRuleRefused naming(Refusal refusal, List<Conflict> conflicts) {
        return new GroupRuleRefused(refusal, conflicts);
    }

    /**
     * The members concerned.
     *
     * @return one entry per member
     */
    public List<Conflict> conflicts() {
        return conflicts;
    }

    @Override
    public Object body() {
        var refusal = refusal();
        return new Body(refusal.status().getMessage(), getMessage(), refusal.code(), conflicts);
    }

    /**
     * One member a change to groups was refused for.
     *
     * @param memberId   the member
     * @param memberName what the station calls them
     * @param groups     the names of the groups the rule is about for them
     */
    @OpenApiName("GroupRuleConflict")
    public record Conflict(int memberId, String memberName, List<String> groups) {}

    /**
     * The error body of a refused change to groups: the usual error, and the members one by one.
     *
     * @param error     the error category
     * @param message   what was refused
     * @param code      the code of the refusal
     * @param conflicts the members concerned
     */
    @OpenApiName("GroupRuleRefusedBody")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String error, String message, String code, List<Conflict> conflicts) {}
}
