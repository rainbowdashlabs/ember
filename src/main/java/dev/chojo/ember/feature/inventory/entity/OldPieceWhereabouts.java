/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Where the piece a movement sent out is, in the terms calling the movement off has to ask about.
 *
 * <p>Custody alone does not answer it: the store of whoever owns a piece is the station's own shelf
 * when the station owns it, and the association's when the association does.
 */
public enum OldPieceWhereabouts {
    /**
     * Nothing to ask about: the movement sent nothing out, reported it lost, or the piece is somewhere
     * calling off never touched, such as with a partner.
     */
    NONE,
    /**
     * The member still holds it, so calling off leaves it where it is.
     */
    WITH_MEMBER,
    /**
     * It is at the station, on its own shelf or held for its owner, and can be put back into the
     * member's hands on the spot.
     */
    AT_STATION,
    /**
     * It has left the station: in the post, or back in the store of an owner that is not the station.
     * Only a movement of its own brings it back.
     */
    AWAY
}
