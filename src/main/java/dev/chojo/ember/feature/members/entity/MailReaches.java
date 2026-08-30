/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Who a letter about a member actually arrives at.
 *
 * <p>Three answers rather than a yes and a no, because the two ways of being reachable read
 * differently on screen and a list that only knows "reachable" cannot say which it is. A member with
 * no address of their own but a guardian who has one is written to, and the offer to send them the
 * setup mail is right: it is simply their guardian who receives it. Shown as a plain yes, that offer
 * sits beside a dead-looking address and reads as a mistake.
 */
public enum MailReaches {
    /** The member's own address, which is a real one. */
    SELF,
    /** Not the member, who has no address of their own, but a guardian who has. */
    GUARDIANS,
    /** Nobody. Nothing written about this member can be delivered anywhere. */
    NOBODY
}
