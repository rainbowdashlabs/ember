/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/** A set as the event group editor holds it: an id for a stored set, null for a new one. */
export interface SetRow {
    id: number | null
    name: string
    minimum: number
}
