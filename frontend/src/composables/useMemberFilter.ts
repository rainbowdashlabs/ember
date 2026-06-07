/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

export interface FilterCriteria {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    mode: 'AND' | 'OR'
}

export function useMemberFilter(
    _members: () => StationMember[],
    memberGroupsMap: () => Map<number, string[]>,
    memberTagsMap: () => Map<number, string[]>,
    allGroups: () => MemberGroup[],
    allTags: () => UserTag[],
) {
    const filterCriteria = ref<FilterCriteria>({userTypes: [], groupIds: [], tagIds: [], mode: 'AND'})

    function onFilter(criteria: FilterCriteria) {
        filterCriteria.value = criteria
    }

    function applyFilter(list: StationMember[]): StationMember[] {
        const fc = filterCriteria.value
        if (fc.userTypes.length === 0 && fc.groupIds.length === 0 && fc.tagIds.length === 0) return list

        const filterUserTypes = new Set(fc.userTypes)
        const filterGroupNames = new Set(allGroups().filter(g => fc.groupIds.includes(g.id)).map(g => g.name ?? ''))
        const filterTagNames = new Set(allTags().filter(t => fc.tagIds.includes(t.id)).map(t => t.name))

        return list.filter(m => {
            const matchesType = fc.userTypes.length === 0 || filterUserTypes.has(m.userType ?? '')
            const mGroups = memberGroupsMap().get(m.id) ?? []
            const mTags = memberTagsMap().get(m.id) ?? []
            const matchesGroup = fc.groupIds.length === 0 || (fc.mode === 'AND'
                ? [...filterGroupNames].every(g => mGroups.includes(g))
                : mGroups.some(g => filterGroupNames.has(g)))
            const matchesTag = fc.tagIds.length === 0 || (fc.mode === 'AND'
                ? [...filterTagNames].every(t => mTags.includes(t))
                : mTags.some(t => filterTagNames.has(t)))
            return matchesType && matchesGroup && matchesTag
        })
    }

    return {
        filterCriteria,
        onFilter,
        applyFilter,
    }
}
