/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {memberGroups} from '@/api'
import type {MemberGroup} from '@/api/types'
import type {AssignableMember} from '@/composables/useGroupsConfig'
import {describeFailure, type Failure} from '@/util/failure'

/** A member about to be moved into the open group out of another group of its set. */
export interface PendingMove {
    memberId: number
    from: string
}

/**
 * Adding somebody to a group of a set who is already in another group of it, which is a move.
 *
 * <p>Whoever is in a sibling group is known before they are picked, so the picker can say so beside
 * their name, and adding them asks "move from X?" instead of being refused by the server.
 *
 * @param groups      every group of the station
 * @param open        the group the page has open
 * @param members     the open group's members, replaced by every change
 * @param addDirectly adds somebody who is in no other group of the set
 * @param failure     the page's failure channel
 */
export function useSetMoves(
    groups: Ref<MemberGroup[]>,
    open: Ref<MemberGroup | null>,
    members: Ref<AssignableMember[]>,
    addDirectly: (memberId: number) => Promise<void>,
    failure: Ref<Failure | null>,
) {
    const {t} = useI18n()
    const inSibling = ref(new Map<number, string>())
    const pending = ref<PendingMove | null>(null)

    async function loadSiblings() {
        const group = open.value
        const siblings = groups.value.filter(candidate =>
            group?.groupSetId != null && candidate.groupSetId === group.groupSetId && candidate.id !== group.id)
        const lists = await Promise.all(siblings.map(sibling => memberGroups.getGroupMembers(sibling.id)))
        const found = new Map<number, string>()
        siblings.forEach((sibling, i) => {
            for (const member of lists[i] ?? []) found.set(member.id, sibling.name ?? '')
        })
        inSibling.value = found
    }

    watch(open, () => {
        loadSiblings().catch(e => { failure.value = describeFailure(e, t) })
    })

    async function add(memberId: number) {
        const from = inSibling.value.get(memberId)
        if (from === undefined) {
            await addDirectly(memberId)
            return
        }
        pending.value = {memberId, from}
    }

    async function confirm() {
        const move = pending.value
        const group = open.value
        pending.value = null
        if (!move || !group) return
        failure.value = null
        try {
            members.value = await memberGroups.setGroupMembers(group.id, {
                memberIds: [...members.value.map(member => member.id), move.memberId],
                move: true,
            })
            await loadSiblings()
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    return {inSibling, pending, add, confirm}
}
