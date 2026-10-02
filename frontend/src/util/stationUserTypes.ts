/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationUserType} from '@/api/generated/schema'

/**
 * The station user types from the lowest rank to the highest, the order every picker, filter and
 * list offers them in.
 */
export const STATION_USER_TYPES: readonly StationUserType[] = [
    StationUserType.TRIAL,
    StationUserType.MEMBER,
    StationUserType.GUARDIAN,
    StationUserType.TEAM,
    StationUserType.MANAGER,
]

/**
 * The user types among what an audience picker hands back.
 *
 * <p>The pickers speak plain strings, the server only knows its own user types, and a value it does
 * not know would be refused with the whole request. Anything else is left out here instead.
 */
export function userTypesOf(values: readonly string[]): StationUserType[] {
    const known: readonly string[] = STATION_USER_TYPES
    return values.filter((value): value is StationUserType => known.includes(value))
}
