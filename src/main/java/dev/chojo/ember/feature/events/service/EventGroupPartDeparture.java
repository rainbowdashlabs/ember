/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

/**
 * What else has to happen when a part leaves its event group, by being taken out or deleted, for what the
 * event group asks every part for. Signing owns it: the documents the event group asks for may have been
 * asked for on the leaving part, and they belong to the event group, not to that part.
 */
public interface EventGroupPartDeparture {

    /** Nothing else happens. */
    EventGroupPartDeparture NONE = (eventGroupId, eventId) -> () -> {};

    /**
     * Called under the event group's lock, inside the transaction that takes the part out, while it still
     * belongs to the event group. Lets go of what is still open there and answers what to do once the
     * transaction is kept.
     *
     * @param eventGroupId the event group
     * @param eventId      the leaving part
     * @return what to run after the part has left
     */
    Runnable leaving(int eventGroupId, int eventId);
}
