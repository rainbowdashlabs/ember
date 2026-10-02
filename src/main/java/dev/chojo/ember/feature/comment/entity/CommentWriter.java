/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

/**
 * Who writes or changes a comment.
 *
 * @param identity the author, {@code null} where nobody can be named
 * @param name     the name notifications show for the author
 * @param origin   whether the author writes here or from a partner station
 */
public record CommentWriter(@Nullable MemberIdentity identity, String name, CommentOrigin origin) {

    /**
     * A member writing on this instance's own pages.
     *
     * @param identity the member
     * @param name     what they are called
     * @return the writer
     */
    public static CommentWriter local(MemberIdentity identity, String name) {
        return new CommentWriter(identity, name, CommentOrigin.LOCAL);
    }

    /**
     * A member of a partner station, writing through the federation.
     *
     * @param identity the member, as their own station names them
     * @param name     the name their station sent along
     * @return the writer
     */
    public static CommentWriter partner(MemberIdentity identity, String name) {
        return new CommentWriter(identity, name, CommentOrigin.PARTNER);
    }
}
