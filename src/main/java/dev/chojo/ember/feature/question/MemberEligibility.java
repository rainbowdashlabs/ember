/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.api.auth.StationUserType;

/**
 * Whether a member passes the constraint of a field that names members.
 *
 * <p>The check asks this rather than knowing it, because who is in which group, of which user type
 * and carrying which tag is the members feature's knowledge. It is implemented once there, so a group
 * field refuses a member outside the group wherever the field is answered.
 */
public interface MemberEligibility {

    /**
     * Whether the member belongs to the group.
     *
     * @param memberId the station member named in the answer
     * @param groupId  the group the field is narrowed to
     */
    boolean inGroup(int memberId, int groupId);

    /**
     * Whether the member is of the user type.
     *
     * @param memberId the station member named in the answer
     * @param userType the user type the field is narrowed to
     */
    boolean ofType(int memberId, StationUserType userType);

    /**
     * Whether the member carries the tag.
     *
     * @param memberId the station member named in the answer
     * @param tagId    the tag the field is narrowed to
     */
    boolean hasTag(int memberId, int tagId);
}
