/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {memberGroups, stationMembers as stationMembersApi, userTags} from '@/api'
import {memberConstraintOf, namesMembers} from '@/api/fieldTypes'
import type {MemberWithName, EventRegistrationField} from '@/api/generated/schema'

/**
 * The members a set of questions needs before it can be answered.
 *
 * <p>A question that asks for a member is answered by picking one, and the picker needs the station's
 * members to pick from. Without them the input falls back to a plain text box, so the answer stored is
 * whatever was typed rather than the member it was meant to name, and every list that reads it back
 * shows nonsense. That happened because one of the two dialogs that ask an appointment's questions
 * loaded the members and the other did not, which is why the loading lives here now.
 *
 * <p>A question narrowed to a group or a tag offers only its members, so those are fetched too: the
 * dialog offered every member of the station for such a question, and the server then refused the
 * answer.
 *
 * <p>Nothing is fetched for questions that do not ask for a member, and nothing is fetched twice.
 *
 * @param fields the questions the dialog is showing
 */
export function useAnswerMembers(fields: Ref<EventRegistrationField[]>) {
    const allMembers = ref<MemberWithName[]>([])
    const groupMembers = ref(new Map<number, MemberWithName[]>())
    const tagMembers = ref(new Map<number, MemberWithName[]>())

    /** Whether any question asks for a member, which is the only reason to fetch them. */
    const needsMembers = computed(() => fields.value.some(field => namesMembers(field.fieldType)))

    /** The groups and tags the questions are narrowed to. */
    function narrowedTo(constraint: 'group' | 'tag'): number[] {
        return fields.value
            .filter(field => memberConstraintOf(field.fieldType) === constraint)
            .map(field => (constraint === 'group' ? field.config.groupId : field.config.tagId))
            .filter((id): id is number => id != null)
    }

    /** The members of each id, fetched once per id. */
    async function membersOf(ids: number[], fetch: (id: number) => Promise<MemberWithName[]>) {
        const found = new Map<number, MemberWithName[]>()
        for (const id of new Set(ids)) found.set(id, await fetch(id))
        return found
    }

    /**
     * Fetches the members where a question needs them.
     *
     * <p>A failure leaves the lists empty rather than raising: the dialog is still worth opening for
     * every other question on it, and a narrowed question then offers the whole station.
     */
    async function loadMembers() {
        if (!needsMembers.value || allMembers.value.length > 0) return
        try {
            allMembers.value = await stationMembersApi.listMembers()
            groupMembers.value = await membersOf(narrowedTo('group'), memberGroups.getGroupMembers)
            tagMembers.value = await membersOf(narrowedTo('tag'), userTags.getTagMembers)
        } catch {
            allMembers.value = []
        }
    }

    return {allMembers, groupMembers, tagMembers, needsMembers, loadMembers}
}
