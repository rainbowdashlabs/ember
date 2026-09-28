/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {FormQuestion} from '@/api/forms'
import type {AnswerValue} from '@/util/formAnswers'
import {useServerDraft} from './useServerDraft'

const getDraft = vi.fn()
const saveDraft = vi.fn()
const discardDraft = vi.fn()

vi.mock('@/api', () => ({
    forms: {
        getDraft: (...args: unknown[]) => getDraft(...args),
        saveDraft: (...args: unknown[]) => saveDraft(...args),
        discardDraft: (...args: unknown[]) => discardDraft(...args),
    },
}))

const question = {id: 7, formQuestionType: 'TEXT'} as FormQuestion

/** A save the test lets land when it chooses. */
function heldSave() {
    let land: () => void = () => undefined
    saveDraft.mockImplementationOnce(() => new Promise<void>(resolve => { land = resolve }))
    return () => land()
}

function draftFor(memberId: number | null = null) {
    const answers = ref<Record<number, AnswerValue>>({7: {text: ''}})
    const path = ref(['p0'])
    const showAt = vi.fn((walked: string[]) => { path.value = walked })
    const draft = useServerDraft(ref(3), ref(memberId), ref([question]), answers, {path, showAt})
    return {answers, path, showAt, draft}
}

/**
 * A station form kept on the server continues where it stopped, and a save still on its way is
 * waited for before anything that would end the draft.
 */
describe('useServerDraft', () => {
    beforeEach(() => {
        getDraft.mockReset()
        saveDraft.mockReset().mockResolvedValue(undefined)
        discardDraft.mockReset().mockResolvedValue(undefined)
    })

    it('continues on the page and with the answers kept', async () => {
        getDraft.mockResolvedValue({answers: {7: {type: 'TEXT', text: 'halb'}}, path: ['p0', 'p1'], updatedAt: '2026-09-28T10:00:00Z'})
        const {answers, path, draft} = draftFor(12)

        await draft.resume()

        expect(getDraft).toHaveBeenCalledWith(3, 12)
        expect(answers.value[7]).toEqual({text: 'halb'})
        expect(path.value).toEqual(['p0', 'p1'])
        expect(draft.resumedFrom.value).toBe('2026-09-28T10:00:00Z')
    })

    it('starts fresh where nothing was kept or it could not be read', async () => {
        getDraft.mockRejectedValue(new Error('403'))
        const {answers, showAt, draft} = draftFor(12)

        await draft.resume()

        expect(answers.value[7]).toEqual({text: ''})
        expect(showAt).not.toHaveBeenCalled()
        expect(draft.resumedFrom.value).toBeNull()
    })

    it('keeps the answers as they stood when asked to', async () => {
        const {answers, draft} = draftFor()
        answers.value[7] = {text: 'erst'}

        const kept = draft.keep()
        answers.value[7] = {text: 'später'}
        await kept

        expect(saveDraft).toHaveBeenCalledWith(3, null, {answers: {7: {type: 'TEXT', text: 'erst'}}, path: ['p0']})
    })

    it('is settled only once every save on its way has landed', async () => {
        const land = heldSave()
        const {draft} = draftFor()
        void draft.keep()
        let settled = false

        const waiting = draft.settled().then(() => { settled = true })
        await Promise.resolve()
        expect(settled).toBe(false)

        land()
        await waiting
        expect(settled).toBe(true)
    })

    it('throws the draft away only after a save on its way has landed', async () => {
        const land = heldSave()
        const {draft} = draftFor()
        void draft.keep()

        const discarding = draft.discard()
        await Promise.resolve()
        expect(discardDraft).not.toHaveBeenCalled()

        land()
        await discarding
        expect(discardDraft).toHaveBeenCalledWith(3, null)
    })
})
