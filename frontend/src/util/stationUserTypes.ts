/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationUserType, type StationUserTypeName} from '@/api/types'

/**
 * The user types among what an audience picker hands back.
 *
 * <p>The pickers speak plain strings, the server only knows its own user types, and a value it does
 * not know would be refused with the whole request. Anything else is left out here instead.
 */
export function userTypesOf(values: readonly string[]): StationUserTypeName[] {
    const known: readonly string[] = Object.values(StationUserType)
    return values.filter((value): value is StationUserTypeName => known.includes(value))
}
