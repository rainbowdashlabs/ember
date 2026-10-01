/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A change to groups refused because of the members it would break a rule for, naming each of them.
 *
 * <p>A set allows a member in one of its groups and a binding allows only some user types. Where a
 * change would break either for somebody, the screen has to say for whom, so the manager can open
 * those members and decide. A sentence alone would leave them searching.
 */
public class GroupRuleRefused extends RefusalResponse {
    private final transient List<GroupConflict> conflicts;

    private GroupRuleRefused(Refusal refusal, List<GroupConflict> conflicts) {
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
    public static GroupRuleRefused naming(Refusal refusal, List<GroupConflict> conflicts) {
        return new GroupRuleRefused(refusal, conflicts);
    }

    /**
     * The members concerned.
     *
     * @return one entry per member
     */
    public List<GroupConflict> conflicts() {
        return conflicts;
    }

    @Override
    public Object body() {
        var refusal = refusal();
        return new GroupRuleRefusedBody(refusal.status().getMessage(), getMessage(), refusal.code(), conflicts);
    }

    /**
     * One member a change to groups was refused for.
     *
     * @param memberId   the member
     * @param memberName what the station calls them
     * @param groups     the names of the groups the rule is about for them
     */
    public record GroupConflict(int memberId, String memberName, List<String> groups) {}

    /**
     * The error body of a route that changes groups: the usual error, and where a rule refused the
     * change, the members one by one.
     *
     * <p>A route describes one body per status, and its other refusals share the status with this one.
     * Those leave {@code conflicts} out, which is why it is optional here although a refusal for the
     * members always carries it.
     *
     * @param error     the error category
     * @param message   what was refused
     * @param code      the code of the refusal
     * @param conflicts the members concerned, or {@code null} for a refusal of something else
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupRuleRefusedBody(
            String error,
            @Nullable String message,
            @Nullable String code,
            @Nullable List<GroupConflict> conflicts) {}
}
