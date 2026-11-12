/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {GroupRulesRequest, StationUserType} from '@/api/generated/schema'
import {groupConflictOf, type GroupConflict, type GroupRow} from '@/util/groupRules'

/** Somebody in the group being edited, as far as the misfits care: who they are and their type. */
export type GroupHolder = {id: number; name?: string | null; email?: string | null; userType: string}

/**
 * The rules of the group open in the form: which set it belongs to and which member types it takes,
 * and the members a save was refused for.
 *
 * <p>A save refused because the new binding leaves members out can be answered by taking them out of
 * the group, which is the same save sent again with that asked for. A save refused because members
 * would be in two groups of a set has no such answer; the list is there to open those members.
 *
 * <p>The members of the group are fetched after the form opens. Every form opened gets a number of its
 * own, and members arriving for a form that is no longer the open one are dropped, so a slow answer
 * never lists one group's members against another group's types.
 */
export function useGroupRulesForm() {
    const groupSetId = ref<number | null>(null)
    const userTypes = ref<StationUserType[]>([])
    const removeNonMatching = ref(false)
    const conflict = ref<GroupConflict | null>(null)
    const members = ref<GroupHolder[]>([])
    let opened = 0

    /**
     * The members of the group being edited who are of none of the ticked types, worked out while the
     * types are chosen, so nobody learns of them only once the save is refused.
     */
    const misfits = computed(() => userTypes.value.length === 0
        ? []
        : members.value.filter(member => !userTypes.value.some(type => type === member.userType)))

    /**
     * Fills the form with a group's rules, or empties it for a new group.
     *
     * @return the number of this opening, which the members fetched for it are handed over with
     */
    function load(group: GroupRow | null): number {
        groupSetId.value = group?.groupSetId ?? null
        userTypes.value = [...(group?.userTypes ?? [])]
        removeNonMatching.value = false
        conflict.value = null
        members.value = []
        opened += 1
        return opened
    }

    /**
     * Hands over who is in the group being edited, which is what the misfits are read from, unless
     * another form was opened since they were asked for.
     */
    function holding(current: GroupHolder[], opening: number) {
        if (opening === opened) members.value = current
    }

    /** Whether the save asks for the members of no ticked type to be taken out, said up front or after a refusal. */
    function removesMisfits(): boolean {
        return removeNonMatching.value || misfits.value.length > 0
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

    return {groupSetId, userTypes, removeNonMatching, conflict, misfits, load, holding, removesMisfits, payload, guarded}
}
