/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

/**
 * Which members a field that names members may name.
 *
 * <p>The field type says which of these applies, and the field's settings say which group, user type
 * or tag it is. Whether a given member passes is asked of {@link MemberEligibility}, because only the
 * members feature knows who is in which group.
 */
public enum MemberConstraint {
    /** Any member of the station. */
    NONE,
    /** Only members of one group. */
    GROUP,
    /** Only members of one user type. */
    USER_TYPE,
    /** Only members carrying one tag. */
    TAG
}
