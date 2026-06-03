/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'
import {StationUserType} from '@/api/types'
import type {FilterCriteria, FilterOption} from '@/components/input/filter/MemberFilterBar.vue'

const USER_TYPE_LABELS: Record<string, string> = {
    MEMBER: 'Mitglied',
    GUARDIAN: 'Erziehungsberechtigter',
    TEAM: 'Team',
    MANAGER: 'Leitung',
    TRIAL: 'Probe',
}

const USER_TYPES = [
    StationUserType.MEMBER,
    StationUserType.GUARDIAN,
    StationUserType.TEAM,
    StationUserType.MANAGER,
    StationUserType.TRIAL,
]

export function useMemberFilter(
    members: () => StationMember[],
    memberGroupsMap: () => Map<number, string[]>,
    memberTagsMap: () => Map<number, string[]>,
    allGroups: () => MemberGroup[],
    allTags: () => UserTag[],
) {
    const filterCriteria = ref<FilterCriteria>({roleIds: [], groupIds: [], tagIds: [], mode: 'AND'})

    // User type filter options (reuses roleIds slot in FilterCriteria)
    const userTypeOptions = computed<FilterOption[]>(() =>
        USER_TYPES.map((ut, i) => ({id: i + 1, name: USER_TYPE_LABELS[ut] ?? ut}))
    )

    const userTypeIdMap = computed(() => {
        const map = new Map<number, string>()
        USER_TYPES.forEach((ut, i) => map.set(i + 1, ut))
        return map
    })

    const groupOptions = computed<FilterOption[]>(() =>
        allGroups().map(g => ({id: g.id, name: g.name ?? ''}))
    )

    const tagOptions = computed<FilterOption[]>(() =>
        allTags().map(t => ({id: t.id, name: t.name}))
    )

    function onFilter(criteria: FilterCriteria) {
        filterCriteria.value = criteria
    }

    function applyFilter(list: StationMember[]): StationMember[] {
        const fc = filterCriteria.value
        if (fc.roleIds.length === 0 && fc.groupIds.length === 0 && fc.tagIds.length === 0) return list

        const filterUserTypes = new Set(fc.roleIds.map(id => userTypeIdMap.value.get(id)).filter(Boolean))
        const filterGroupNames = new Set(allGroups().filter(g => fc.groupIds.includes(g.id)).map(g => g.name ?? ''))
        const filterTagNames = new Set(allTags().filter(t => fc.tagIds.includes(t.id)).map(t => t.name))

        return list.filter(m => {
            // Type: always OR (a member has exactly one type)
            const matchesType = fc.roleIds.length === 0 || filterUserTypes.has(m.userType ?? '')
            // Groups/Tags: AND mode = must match ALL selected, OR mode = must match ANY
            const mGroups = memberGroupsMap().get(m.id) ?? []
            const mTags = memberTagsMap().get(m.id) ?? []
            const matchesGroup = fc.groupIds.length === 0 || (fc.mode === 'AND'
                ? [...filterGroupNames].every(g => mGroups.includes(g))
                : mGroups.some(g => filterGroupNames.has(g)))
            const matchesTag = fc.tagIds.length === 0 || (fc.mode === 'AND'
                ? [...filterTagNames].every(t => mTags.includes(t))
                : mTags.some(t => filterTagNames.has(t)))
            // Cross-category: always AND
            return matchesType && matchesGroup && matchesTag
        })
    }

    return {
        filterCriteria,
        userTypeOptions,
        groupOptions,
        tagOptions,
        onFilter,
        applyFilter,
    }
}
