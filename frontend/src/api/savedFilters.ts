/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createCrudResource} from './crud'
import type {CreateFilterRequest, FilterTableType, SavedFilter} from './generated/schema'

const filters = createCrudResource<SavedFilter, CreateFilterRequest>('/saved-filters')

export async function listFilters(tableType: FilterTableType): Promise<SavedFilter[]> {
    return filters.list({tableType})
}

export const createFilter = filters.create
export const deleteFilter = filters.remove
