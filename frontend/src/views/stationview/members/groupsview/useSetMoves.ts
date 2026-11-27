/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {memberGroups} from '@/api'
import {useConfirmAction} from '@/composables/useConfirmAction'
import type {AssignableMember} from '@/composables/useGroupsConfig'
import type {GroupRow} from '@/util/groupRules'
import {describeFailure, type Failure} from '@/util/failure'

/** A member about to be moved into the open group out of another group of its set. */
export interface PendingMove {
    memberId: number
    from: string
}

/**
 * Adding somebody to a group of a set who is already in another group of it, which is a move.
 *
 * <p>Whoever is in a sibling group is known before they are picked, so the picker can show that beside
 * their name, and adding them asks "move from X?" instead of being refused by the server. Shift
 * skips the question and moves them at once.
 *
 * @param groups      every group of the station
 * @param open        the group the page has open
 * @param members     the open group's members, replaced by every change
 * @param addDirectly adds somebody who is in no other group of the set
 * @param failure     the page's failure channel
 */
export function useSetMoves(
    groups: Ref<GroupRow[]>,
    open: Ref<GroupRow | null>,
    members: Ref<AssignableMember[]>,
    addDirectly: (memberId: number) => Promise<void>,
    failure: Ref<Failure | null>,
) {
    const {t} = useI18n()
    const inSibling = ref(new Map<number, string>())

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

    const move = useConfirmAction<PendingMove>({
        onConfirm: async ({memberId}) => {
            const group = open.value
            if (!group) return
            failure.value = null
            members.value = await memberGroups.setGroupMembers(group.id, {
                memberIds: [...members.value.map(member => member.id), memberId],
                move: true,
            })
        },
        onSuccess: () => loadSiblings(),
        failure,
    })

    async function add(memberId: number) {
        const from = inSibling.value.get(memberId)
        if (from === undefined) {
            await addDirectly(memberId)
            return
        }
        move.request({memberId, from})
    }

    return {inSibling, pending: move.target, show: move.show, add, confirm: move.confirm}
}
