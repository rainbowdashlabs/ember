/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance, lostAndFound, movements} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * What is outstanding for the people on one sheet, and the two things that can be settled from it.
 *
 * <p>The notes are read once for the whole sheet rather than once a member: the walk steps through
 * every name, and a read a step would be a read a member. A reader allowed none of it gets an empty
 * answer, which is why a failure here is quiet: the notes are a convenience beside the check, and
 * losing them must not stop the check.
 *
 * @param sessionId the sheet being worked through
 * @param failure   the view's failure channel, set when settling something fails
 */
export function useSessionNotes(sessionId: Ref<number>, failure: Ref<Failure | null>) {
    const {t} = useI18n()

    const memberNotes = ref<Map<number, attendance.MemberNotes>>(new Map())

    async function refreshNotes() {
        const notes = await attendance.getMemberNotes(sessionId.value)
        memberNotes.value = new Map(notes.map(note => [note.memberId, note]))
    }

    /**
     * The notes as the sheet opens, where a failure stays quiet on purpose: they sit beside the check
     * as a convenience, and a reader who may not see them must still be able to take attendance.
     */
    async function loadNotes() {
        try {
            await refreshNotes()
        } catch {
            memberNotes.value = new Map()
        }
    }

    /**
     * Settling something from beside a name, then reading the notes again, which are two things and
     * not one. They shared an attempt, so a swap that really was handed over, followed by notes that
     * failed to come back, read as a swap that had not gone through, and the same step was
     * acknowledged twice.
     */
    async function settle(change: () => Promise<unknown>) {
        failure.value = null
        try {
            await change()
        } catch (e) {
            failure.value = describeFailure(e, t)
            return
        }
        try {
            await refreshNotes()
        } catch (e) {
            failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
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
        await settle(() => movements.acknowledgeStep(movementId, {stepId, pickedItemId: replacementItemId}))
    }

    /**
     * Calls a swap off from the sheet, removing it rather than closing it. Only swaps where nothing
     * has changed hands stand beside a name here, so there is never anything to put back.
     */
    async function dropSwap(movementId: number) {
        await settle(() => movements.deleteMovement(movementId))
    }

    async function signOffFound(itemId: number) {
        await settle(() => lostAndFound.markProvided(itemId))
    }

    return {memberNotes, loadNotes, moveSwap, dropSwap, signOffFound}
}
