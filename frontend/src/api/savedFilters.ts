/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createCrudResource} from './crud'

export interface SavedFilter {
    id: number
    accountId: number
    tableType: string
    name: string
    filterData: string
    position: number
}

export interface CreateFilterRequest {
    tableType: string
    name: string
    filterData: string
}

const filters = createCrudResource<SavedFilter, CreateFilterRequest>('/saved-filters')

export async function listFilters(tableType: string): Promise<SavedFilter[]> {
    return filters.list({tableType})
}

export const createFilter = filters.create
export const deleteFilter = filters.remove
