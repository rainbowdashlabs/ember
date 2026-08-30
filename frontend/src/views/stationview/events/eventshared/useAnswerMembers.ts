/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {stationMembers as stationMembersApi} from '@/api'
import type {EventRegistrationField} from '@/api/events'
import type {StationMember} from '@/api/types'

/**
 * The members a set of questions needs before it can be answered.
 *
 * <p>A question that asks for a member is answered by picking one, and the picker needs the station's
 * members to pick from. Without them the input falls back to a plain text box, so the answer stored is
 * whatever was typed rather than the member it was meant to name, and every list that reads it back
 * shows nonsense. That happened because one of the two dialogs that ask an appointment's questions
 * loaded the members and the other did not, which is why the loading lives here now.
 *
 * <p>Nothing is fetched for questions that do not ask for a member, and nothing is fetched twice.
 *
 * @param fields the questions the dialog is showing
 */
export function useAnswerMembers(fields: Ref<EventRegistrationField[]>) {
    const allMembers = ref<StationMember[]>([])

    /** Whether any question asks for a member, which is the only reason to fetch them. */
    const needsMembers = computed(() => fields.value.some(field => field.fieldType.startsWith('MEMBER')))

    /**
     * Fetches the members where a question needs them.
     *
     * <p>A failure leaves the list empty rather than raising: the dialog is still worth opening for
     * every other question on it.
     */
    async function loadMembers() {
        if (!needsMembers.value || allMembers.value.length > 0) return
        try {
            allMembers.value = await stationMembersApi.listMembers()
        } catch {
            allMembers.value = []
        }
    }

    return {allMembers, needsMembers, loadMembers}
}
