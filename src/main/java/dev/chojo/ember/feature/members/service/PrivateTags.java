/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

/**
 * The one rule about private tags, asked by everything that shows a tag or chooses people by one.
 *
 * <p>A private tag is seen only by readers allowed to view members, which is {@link
 * StationPermission#MEMBER_READ}; managing tags alone is not enough, and the tagged member does not
 * see it either. It labels people and never chooses them: an audience, restriction or access rule
 * chosen by it would show who carries it to everybody it lets in.
 */
@Singleton
public class PrivateTags {
    private final UserTagRepository tagRepository;

    @Inject
    public PrivateTags(UserTagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Whether the reader may see private tags.
     *
     * @param session the reader
     * @return {@code true} where the reader may view members
     */
    public boolean seenBy(StationSession session) {
        return session.hasPermission(StationPermission.MEMBER_READ);
    }

    /**
     * Whether the reader may see this tag.
     *
     * @param session the reader
     * @param tag     the tag
     * @return {@code true} for every tag that is not private, and for a private one where the reader
     * may view members
     */
    public boolean visibleTo(StationSession session, UserTag tag) {
        return !tag.visibility().restricted() || seenBy(session);
    }

    /**
     * The tags the reader may see, in the order given.
     *
     * @param session the reader
     * @param tags    the tags
     * @return the tags without the private ones, unless the reader may view members
     */
    public List<UserTag> visibleTo(StationSession session, List<UserTag> tags) {
        if (seenBy(session)) return tags;
        return withoutPrivate(tags);
    }

    /**
     * The tags without the private ones, for a reader nobody asked about: the tagged member
     * themselves, or anybody looking at a list that is not about members.
     *
     * @param tags the tags
     * @return the tags that are not private, in the order given
     */
    public static List<UserTag> withoutPrivate(List<UserTag> tags) {
        return tags.stream().filter(tag -> !tag.visibility().restricted()).toList();
    }

    /**
     * Refuses a choice of people that names a private tag.
     *
     * @param tagIds the tags a selection chooses people by
     * @throws dev.chojo.ember.api.refusal.RefusalResponse where any of them is private
     */
    public void requireChoosable(Collection<Integer> tagIds) {
        if (!tagRepository.findPrivateIds(tagIds).isEmpty()) {
            throw MemberRefusal.TAG_PRIVATE_CHOOSES_NOBODY.raise();
        }
    }

    /**
     * Whether people may be chosen by this tag. A private tag found inside a stored configuration,
     * such as a member question, is treated as a reference that is gone, so it lets nobody through.
     *
     * @param tagId the tag
     * @return {@code false} where the tag is private
     */
    public boolean choosable(int tagId) {
        return tagRepository.findPrivateIds(List.of(tagId)).isEmpty();
    }
}
