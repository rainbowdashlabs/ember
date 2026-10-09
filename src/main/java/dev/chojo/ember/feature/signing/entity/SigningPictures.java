/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.Map;

/**
 * The signature pictures of one confirmation, one per person who signs in it.
 *
 * <p>The account holder signs every field they sign for themselves or as a guardian with one picture of
 * their own; each member who signs their own field through the account signs with a picture of theirs,
 * made for the act, since the account's picture is not theirs.
 *
 * @param accountHolder the account holder's picture, the saved one unless one was made for the act
 * @param members       the picture made for the act by each member who signs through the account, by
 *                      member id
 */
public record SigningPictures(SigningPicture accountHolder, Map<Integer, SigningPicture> members) {
    /** Copies the members' pictures, so they cannot change after the fact. */
    public SigningPictures {
        members = Map.copyOf(members);
    }

    /**
     * @param picture the one picture an act on a single field leaves
     * @param signer  who signs the field
     * @return the pictures of that act
     */
    public static SigningPictures single(SigningPicture picture, Signer signer) {
        Integer memberId = signer.memberId();
        if (signer.throughAnotherAccount() && memberId != null) {
            return new SigningPictures(SigningPicture.SAVED, Map.of(memberId, picture));
        }
        return new SigningPictures(picture, Map.of());
    }

    /**
     * @param signer who signs a field
     * @return the picture the field is signed with; {@link SigningPicture#SAVED} for a member who sent none,
     *     which signing through another account refuses
     */
    public SigningPicture of(Signer signer) {
        Integer memberId = signer.memberId();
        if (signer.throughAnotherAccount() && memberId != null) {
            return members.getOrDefault(memberId, SigningPicture.SAVED);
        }
        return accountHolder;
    }
}
