/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {TagResponse} from '@/api/generated/schema'

/** The words a station has put on its things so far. */
export const tags: TagResponse[] = [
    {id: 1, name: 'Funk', color: '#3694FF', position: 0, itemCount: 4},
    {id: 2, name: 'Gemeinde', color: null, position: 1, itemCount: 2},
]

/** A piece that already wears one word and is about to wear a second. */
export const itemName = 'Funkgerät orange'

/** The words already on that piece. */
export const tagNames = ['Funk']
