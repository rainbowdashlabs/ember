/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch, type ComputedRef} from 'vue'
import {managedMembers as managedMembersApi} from '@/api'
import type {ManagedMember} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * One person whose documents the reader sees: themselves or a member in their care.
 *
 * @property id   the station member
 * @property name the name documents and lists call them by
 * @property own  whether it is the reader themselves
 */
export interface ReaderPerson {
    id: number
    name: string
    own: boolean
}

/**
 * The reader and every member in their care at the current station, the reader first. A screen
 * shows one person's documents at a time from this list, and names the people a document is bound
 * to from it, since a reader may not read the station's member list.
 *
 * @return the people, empty until the session names the reader's membership
 */
export function useReaderPeople(): ComputedRef<ReaderPerson[]> {
    const {sessionInfo, isGuardian} = useSession()
    const managed = ref<ManagedMember[]>([])

    const ownId = computed(() => sessionInfo.value?.member?.id ?? null)

    watch(ownId, async id => {
        managed.value = id && isGuardian() ? await managedMembersApi.listManaged().catch(() => []) : []
    }, {immediate: true})

    return computed(() => {
        const id = ownId.value
        if (id === null) return []
        const account = sessionInfo.value?.account
        const name = [account?.firstName, account?.lastName].filter(Boolean).join(' ')
            || sessionInfo.value?.member?.calledName || ''
        return [
            {id, name, own: true},
            ...managed.value.map(child => ({id: child.id, name: child.name, own: false})),
        ]
    })
}
