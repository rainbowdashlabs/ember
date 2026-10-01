/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {GroupRulesRequest, StationUserType} from '@/api/generated/schema'
import {groupConflictOf, type GroupConflict, type GroupRow} from '@/util/groupRules'

/**
 * The rules of the group open in the form: which set it belongs to and which member types it takes,
 * and the members a save was refused for.
 *
 * <p>A save refused because the new binding leaves members out can be answered by taking them out of
 * the group, which is the same save sent again with that asked for. A save refused because members
 * would be in two groups of a set has no such answer; the list is there to open those members.
 */
export function useGroupRulesForm() {
    const groupSetId = ref<number | null>(null)
    const userTypes = ref<StationUserType[]>([])
    const removeNonMatching = ref(false)
    const conflict = ref<GroupConflict | null>(null)

    function load(group: GroupRow | null) {
        groupSetId.value = group?.groupSetId ?? null
        userTypes.value = [...(group?.userTypes ?? [])]
        removeNonMatching.value = false
        conflict.value = null
    }

    function payload(): GroupRulesRequest {
        return {groupSetId: groupSetId.value ?? undefined, userTypes: [...userTypes.value]}
    }

    /**
     * Sends a save and keeps the members it was refused for, so the page can list them. The refusal is
     * passed on as well, so the form stays open.
     */
    async function guarded<T>(write: () => Promise<T>): Promise<T> {
        conflict.value = null
        try {
            const written = await write()
            removeNonMatching.value = false
            return written
        } catch (e) {
            conflict.value = groupConflictOf(e)
            removeNonMatching.value = false
            throw e
        }
    }

    return {groupSetId, userTypes, removeNonMatching, conflict, load, payload, guarded}
}
