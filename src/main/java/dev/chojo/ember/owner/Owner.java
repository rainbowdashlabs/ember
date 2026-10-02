/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.owner;

/**
 * Whose data a shared core touches: a station, an association, or the instance itself.
 *
 * <p>Several systems keep one copy of their rules and one set of tables per owner (profile fields,
 * storage backends). The rules are written once and told by this type whose tables, permissions and
 * refusal codes apply. It is built at the route edge from the session, through
 * {@code StationSession.owner()}, {@code UserSession.association(...)} and {@code UserSession.instance()},
 * and never from anything the request sends: a route that built one from a path or body would let a
 * caller name somebody else's data.
 *
 * <p>It names an owner and nothing more. It is not a storage scope (which also knows accounts and
 * builds key prefixes) and not a recipient (which names a person).
 */
public sealed interface Owner {

    /**
     * A station and its own tables.
     *
     * @param stationId the station
     */
    record Station(int stationId) implements Owner {}

    /**
     * An association (a cluster in the code) and its own tables.
     *
     * @param clusterId the association
     */
    record Association(int clusterId) implements Owner {}

    /** The instance, whose settings live in its configuration rather than in a table of its own. */
    record Instance() implements Owner {}
}
