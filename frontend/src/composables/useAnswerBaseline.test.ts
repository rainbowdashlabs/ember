/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {describe, expect, it} from 'vitest'
import type {FormAnswerValue} from '@/api/generated/schema'
import {useAnswerBaseline} from './useAnswerBaseline'

/** A ranking that starts full and an answer sent earlier, the way a station form opens for a correction. */
function openedForm() {
    const answers = ref<Record<number, FormAnswerValue>>({
        1: {type: 'RANKING', order: ['a', 'b']},
        2: {type: 'TEXT', text: 'schon gesendet'},
    })
    const path = ref(['p0'])
    const baseline = useAnswerBaseline(answers, path)
    baseline.settle()
    return {answers, path, baseline}
}

/** Only what the reader changed after the form opened is worth keeping as a draft. */
describe('useAnswerBaseline', () => {
    it('counts a form that opened full and was left alone as unchanged', () => {
        expect(openedForm().baseline.changed()).toBe(false)
    })

    it('counts a changed answer and a page walked as a change', () => {
        const answered = openedForm()
        answered.answers.value[2] = {type: 'TEXT', text: 'korrigiert'}
        expect(answered.baseline.changed()).toBe(true)

        const walked = openedForm()
        walked.path.value = ['p0', 'p1']
        expect(walked.baseline.changed()).toBe(true)
    })

    it('counts an answer changed and taken back as unchanged', () => {
        const {answers, baseline} = openedForm()
        answers.value[1] = {type: 'RANKING', order: ['b', 'a']}
        answers.value[1] = {type: 'RANKING', order: ['a', 'b']}

        expect(baseline.changed()).toBe(false)
    })
})
