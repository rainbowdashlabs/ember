/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance, lostAndFound, movements} from '@/api'

/**
 * What is outstanding for the people on one sheet, and the two things that can be settled from it.
 *
 * <p>The notes are read once for the whole sheet rather than once a member: the walk steps through
 * every name, and a read a step would be a read a member. A reader allowed none of it gets an empty
 * answer, which is why a failure here is quiet: the notes are a convenience beside the check, and
 * losing them must not stop the check.
 *
 * @param sessionId the sheet being worked through
 * @param error     the view's error channel, set when settling something fails
 */
export function useSessionNotes(sessionId: Ref<number>, error: Ref<string>) {
    const {t} = useI18n()

    const memberNotes = ref<Map<number, attendance.MemberNotes>>(new Map())

    async function loadNotes() {
        try {
            const notes = await attendance.getMemberNotes(sessionId.value)
            memberNotes.value = new Map(notes.map(note => [note.memberId, note]))
        } catch {
            memberNotes.value = new Map()
        }
    }

    /**
     * Moving a movement on is acknowledging the step it stands on, which is the same thing the page
     * about one movement does. The piece set aside travels with it, because the step that hands one
     * over refuses to run without being told which piece it is: the movement already knows, and asking
     * whoever is ticking off names to pick it out of a list would be asking a question that has been
     * answered.
     */
    async function moveSwap(movementId: number, stepId: number, replacementItemId: number | null) {
        error.value = ''
        try {
            await movements.acknowledgeStep(movementId, {stepId, pickedItemId: replacementItemId})
            await loadNotes()
        } catch {
            error.value = t('common.error')
        }
    }

    /**
     * Calls a swap off from the sheet, removing it rather than closing it. Only swaps where nothing
     * has changed hands stand beside a name here, so there is never anything to put back.
     */
    async function dropSwap(movementId: number) {
        error.value = ''
        try {
            await movements.deleteMovement(movementId)
            await loadNotes()
        } catch {
            error.value = t('common.error')
        }
    }

    async function signOffFound(itemId: number) {
        error.value = ''
        try {
            await lostAndFound.markProvided(itemId)
            await loadNotes()
        } catch {
            error.value = t('common.error')
        }
    }

    return {memberNotes, loadNotes, moveSwap, dropSwap, signOffFound}
}
