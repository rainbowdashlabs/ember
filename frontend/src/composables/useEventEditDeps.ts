/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {attendance, events, memberGroups as memberGroupsApi, stationMembers, userTags as userTagsApi} from '@/api'
import type {
    AttendanceTemplate,
    EventCategory,
    MemberGroup,
    StationMember,
    UserTag,
} from '@/api/types'
import type {RegistrationCount} from '@/api/events'
import {useAsyncLoader} from './useAsyncLoader'

export interface UseEventEditDepsOptions {
    /** Also load the station's member roster. Defaults to false. */
    withMembers?: boolean
    /** Also load aggregated registration counts per event. Defaults to false. */
    withCounts?: boolean
    /** Run on mount. Defaults to true; set false to defer until {@link reload} is called. */
    autoLoad?: boolean
}

export interface EventEditDeps {
    categories: Ref<EventCategory[]>
    templates: Ref<AttendanceTemplate[]>
    groups: Ref<MemberGroup[]>
    tags: Ref<UserTag[]>
    /** Empty unless `withMembers: true` was passed. */
    members: Ref<StationMember[]>
    /** Empty unless `withCounts: true` was passed. */
    registrationCounts: Ref<RegistrationCount[]>
    loading: Ref<boolean>
    error: Ref<string>
    reload: () => Promise<void>
}

/**
 * Loads the common bundle of resources every event-edit / event-list view depends on
 * (categories, attendance templates, member groups, user tags) in a single
 * {@link Promise.all}. Opt-in flags extend the bundle with the member roster and
 * registration counts so views that need them can share the same loader lifecycle
 * instead of stacking their own.
 */
export function useEventEditDeps(options: UseEventEditDepsOptions = {}): EventEditDeps {
    const categories = ref<EventCategory[]>([])
    const templates = ref<AttendanceTemplate[]>([])
    const groups = ref<MemberGroup[]>([])
    const tags = ref<UserTag[]>([])
    const members = ref<StationMember[]>([])
    const registrationCounts = ref<RegistrationCount[]>([])

    const {loading, error, reload} = useAsyncLoader(async () => {
        const core = [
            events.listCategories().catch(() => [] as EventCategory[]),
            attendance.listTemplates().catch(() => [] as AttendanceTemplate[]),
            memberGroupsApi.listGroups().catch(() => [] as MemberGroup[]),
            userTagsApi.listTags().catch(() => [] as UserTag[]),
        ] as const
        const tasks: Promise<unknown>[] = [...core]
        if (options.withMembers) tasks.push(stationMembers.listMembers().catch(() => [] as StationMember[]))
        if (options.withCounts) tasks.push(events.listRegistrationCounts().catch(() => [] as RegistrationCount[]))
        const results = await Promise.all(tasks)
        categories.value = results[0] as EventCategory[]
        templates.value = results[1] as AttendanceTemplate[]
        groups.value = results[2] as MemberGroup[]
        tags.value = results[3] as UserTag[]
        let idx = 4
        if (options.withMembers) {
            members.value = results[idx++] as StationMember[]
        }
        if (options.withCounts) {
            registrationCounts.value = results[idx++] as RegistrationCount[]
        }
    }, {autoLoad: options.autoLoad})

    return {categories, templates, groups, tags, members, registrationCounts, loading, error, reload}
}
