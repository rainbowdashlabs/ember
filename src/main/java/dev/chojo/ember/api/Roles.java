/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.security.RouteRole;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Hierarchical role system used for route-level access control.
 * Each role can include child roles, forming a tree where higher roles transitively grant
 * all permissions of their children (e.g. {@link #MANAGER} includes all management roles).
 */
public enum Roles implements RouteRole {
    /**
     * Allows logging in
     */
    LOGIN,
    /**
     * A general user on a station
     */
    USER,
    /**
     * General access to edit own data
     */
    MEMBER(USER),
    /**
     * Manage data of other members that are assigned to this member
     */
    GUARDIAN(USER, LOGIN),
    /**
     * Team member with elevated access (e.g. team leads, instructors)
     */
    TEAM(LOGIN, USER),
    /**
     * Manage attendence
     */
    ATTENDENCE_MANAGEMENT(TEAM),
    /**
     * Export attendance reports
     */
    ATTENDENCE_EXPORT_MANAGER(TEAM),
    /**
     * Manage inventory
     */
    INVENTORY_MANAGEMENT(TEAM),
    /**
     * Manage events and breaks
     */
    EVENT_MANAGEMENT(TEAM),

    /**
     * Manage member data
     */
    MEMBER_MANAGEMENT(TEAM),
    /**
     * Create and manage news entries
     */
    NEWS_MANAGEMENT(TEAM),
    /**
     * Create and manage polls/forms
     */
    POLL_MANAGEMENT(TEAM),
    /**
     * Manage lost and found items
     */
    LOST_AND_FOUND_MANAGEMENT(TEAM),
    /**
     * Manage waiting lists
     */
    WAITLIST_MANAGEMENT(TEAM),
    /**
     * Manage quiz catalogs, tests and grading
     */
    QUIZ_MANAGEMENT(TEAM),
    /**
     * Manage knowledge base content
     */
    KNOWLEDGE_MANAGEMENT(TEAM),
    /**
     * Manage everything. Includes all other management roles
     */
    MANAGER(
            TEAM,
            ATTENDENCE_MANAGEMENT,
            ATTENDENCE_EXPORT_MANAGER,
            INVENTORY_MANAGEMENT,
            EVENT_MANAGEMENT,
            MEMBER_MANAGEMENT,
            NEWS_MANAGEMENT,
            POLL_MANAGEMENT,
            LOST_AND_FOUND_MANAGEMENT,
            WAITLIST_MANAGEMENT,
            QUIZ_MANAGEMENT,
            KNOWLEDGE_MANAGEMENT),
    /**
     * Manange the software itself.
     */
    ADMIN(MANAGER);

    /**
     * Roles that must not be removed from a member to prevent lockouts.
     */
    public static final Set<Roles> PROTECTED_ROLES = Set.of(MEMBER_MANAGEMENT, MANAGER);

    /**
     * Role pairs that are mutually exclusive and cannot be assigned to the same member.
     */
    public static final Map<Roles, Set<Roles>> CONFLICTING_ROLES = Map.of(
            MEMBER,
            Set.of(
                    GUARDIAN,
                    TEAM,
                    MANAGER,
                    ADMIN,
                    ATTENDENCE_MANAGEMENT,
                    ATTENDENCE_EXPORT_MANAGER,
                    INVENTORY_MANAGEMENT,
                    EVENT_MANAGEMENT,
                    MEMBER_MANAGEMENT,
                    NEWS_MANAGEMENT,
                    POLL_MANAGEMENT,
                    LOST_AND_FOUND_MANAGEMENT,
                    WAITLIST_MANAGEMENT,
                    QUIZ_MANAGEMENT,
                    KNOWLEDGE_MANAGEMENT),
            GUARDIAN,
            Set.of(MEMBER));

    private final Roles[] children;
    private Set<Roles> allChildren;

    Roles() {
        this.children = new Roles[0];
    }

    Roles(Roles... children) {
        this.children = children;
    }

    /**
     * Maps a database role name to the corresponding enum value.
     */
    public static Roles fromDbName(String dbName) {
        try {
            return valueOf(dbName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Expands a set of roles to include all transitively contained child roles.
     */
    public static Set<Roles> expand(Set<Roles> roles) {
        Set<Roles> expanded = EnumSet.copyOf(roles);
        for (Roles role : roles) {
            expanded.addAll(role.allChildren());
        }
        return expanded;
    }

    /**
     * Returns the direct child roles of this role.
     *
     * @return array of direct child roles
     */
    public Roles[] getChildren() {
        return children;
    }

    /**
     * Returns all roles transitively included by this role (direct + indirect children).
     */
    public Set<Roles> allChildren() {
        if (allChildren == null) {
            allChildren = EnumSet.noneOf(Roles.class);
            for (Roles child : children) {
                allChildren.add(child);
                allChildren.addAll(child.allChildren());
            }
        }
        return allChildren;
    }

    /**
     * Checks whether this role transitively includes the given role.
     */
    public boolean includes(Roles role) {
        return allChildren().contains(role);
    }
}
