/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

/**
 * The people of the instance by member type, counted over the memberships of regular stations that
 * are not archived.
 *
 * <p>A person is their account; a membership without an account is a person of its own. A person
 * holding a type in at least one station counts once under that type, so somebody who is a member in
 * one station and on the team in another counts under both, and the types can add up to more than
 * {@link #people()}. Archived memberships are in none of these figures.
 *
 * @param people      distinct people with at least one membership
 * @param trial       distinct people on trial in at least one station
 * @param member      distinct people who are members in at least one station
 * @param guardian    distinct people whose type is guardian in at least one station
 * @param team        distinct people of the team in at least one station
 * @param manager     distinct people who manage at least one station
 * @param memberships the memberships themselves, so somebody in two stations counts twice
 */
public record PeopleByType(int people, int trial, int member, int guardian, int team, int manager, int memberships) {}
