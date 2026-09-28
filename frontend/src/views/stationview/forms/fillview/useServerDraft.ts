/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {forms} from '@/api'
import type {FormQuestion} from '@/api/forms'
import {typedAnswers, type AnswerValue} from '@/util/formAnswers'

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
    answers: Ref<Record<number, AnswerValue>>,
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
        for (const [id, value] of Object.entries(draft.answers)) {
            const {type: _type, ...answer} = value
            answers.value[Number(id)] = answer
        }
        if (draft.path.length > 0) walk.showAt(draft.path, {})
        resumedFrom.value = draft.updatedAt
    }

    /** Keeps what is filled in so far. */
    async function keep() {
        const draft = {
            answers: typedAnswers(questions.value, answers.value, question => question.formQuestionType),
            path: walk.path.value,
        }
        await forms.saveDraft(formId.value, memberId.value, draft).catch(() => undefined)
    }

    /** Throws the draft away, so the form starts fresh. */
    async function discard() {
        resumedFrom.value = null
        await forms.discardDraft(formId.value, memberId.value).catch(() => undefined)
    }

    return {resumedFrom, resume, keep, discard}
}
