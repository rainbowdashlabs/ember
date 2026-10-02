/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ItemNameCount} from '@/api/generated/schema'

/** One word written two ways, which is the case the tidying screen exists for, and one bystander. */
export const names: ItemNameCount[] = [
    {name: 'Funkgerät orange', pieces: 4, unassigned: 4},
    {name: 'Funkgerät organge', pieces: 1, unassigned: 1},
    {name: 'Ladestation', pieces: 1, unassigned: 1},
]

/** The two spellings of the radio are ticked, the charging station is left alone. */
export const selected: ReadonlySet<string> = new Set(['Funkgerät orange', 'Funkgerät organge'])

/** No kind exists yet, so the merge writes a new one under the commoner spelling. */
export const targetName = 'Funkgerät orange'

/** The pieces behind both ticked spellings. */
export const selectedPieces = 5
