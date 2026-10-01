/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {forms} from '@/api'
import type {FormAnswerValue, FormQuestion} from '@/api/generated/schema'

/** The part of the page walk a draft needs: where the reader is, and how to put them back there. */
interface DraftWalk {
    path: Readonly<Ref<string[]>>
    showAt: (walked: string[], marked: Record<number, string>) => void
}

/**
 * Keeps a half-filled station form on the server, per member the answer is for, so it continues on
 * another device.
 *
 * <p>Kept on every step to the next page and when the fill screen is left. Opening the form again
 * continues on the page where the reader stopped, with a note saying so and a way to start over. A
 * draft is never an answer, and saving one that fails costs the reader nothing they had not typed
 * anyway, so it fails quietly.
 *
 * @param formId    the form
 * @param memberId  the member in the reader's care the answer is for, or null for the reader
 * @param questions the form's questions
 * @param answers   the answers filled in
 * @param walk      the page walk
 */
export function useServerDraft(
    formId: Ref<number>,
    memberId: Ref<number | null>,
    questions: Ref<FormQuestion[]>,
    answers: Ref<Record<number, FormAnswerValue>>,
    walk: DraftWalk,
) {
    /** When the draft the form continues from was kept, or null where it started fresh. */
    const resumedFrom = ref<string | null>(null)

    /** Continues from the kept draft, where there is one, over the answers already filled in. */
    async function resume() {
        resumedFrom.value = null
        const forWhom = memberId.value
        const draft = await forms.getDraft(formId.value, forWhom).catch(() => null)
        if (!draft || forWhom !== memberId.value) return
        for (const question of questions.value) {
            const answer = draft.answers[question.id]
            if (answer?.type === question.formQuestionType) answers.value[question.id] = answer
        }
        if (draft.path.length > 0) walk.showAt(draft.path, {})
        resumedFrom.value = draft.updatedAt
    }

    /** The saves still on their way, one after the other, so a later one never lands before an earlier one. */
    let saving: Promise<void> = Promise.resolve()

    /** Keeps what is filled in so far, as it stands when this is called. */
    function keep(): Promise<void> {
        const form = formId.value
        const forWhom = memberId.value
        const draft = {
            answers: Object.fromEntries(Object.entries(answers.value).map(([id, answer]) => [id, {...answer}])),
            path: walk.path.value,
        }
        saving = saving.then(() => forms.saveDraft(form, forWhom, draft).catch(() => undefined))
        return saving
    }

    /**
     * Waits for every save still on its way. Sending the answer ends the draft on the server, so a save
     * that arrived after it would bring the draft back for an answer already sent.
     */
    async function settled() {
        await saving
    }

    /** Throws the draft away, so the form starts fresh, once any save still on its way has landed. */
    async function discard() {
        resumedFrom.value = null
        await settled()
        await forms.discardDraft(formId.value, memberId.value).catch(() => undefined)
    }

    return {resumedFrom, resume, keep, settled, discard}
}
