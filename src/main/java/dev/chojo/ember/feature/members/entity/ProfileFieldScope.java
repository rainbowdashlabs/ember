/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Which kind of member a profile field is asked of.
 *
 * <p>This is a role and nothing else. It used to sit on the field itself and stood for two things at
 * once: who was asked, and who could read the answer. A field is now assigned to the roles it is put
 * to, so this names only the first of those, and the second is read from the assignments a field
 * carries.
 *
 * <p>A group is no longer one of these. Asking one group something is an assignment naming that
 * group, which is a different target rather than another kind of member.
 */
public enum ProfileFieldScope {
    /** Somebody trying the station out, who is asked what a member is asked unless told otherwise. */
    TRIAL,
    /** An ordinary member. */
    MEMBER,
    /** Somebody answering on behalf of a member in their care. */
    GUARDIAN,
    /** Somebody who runs things at the station. */
    TEAM,
    /** Somebody who administers the station. */
    MANAGER
}
