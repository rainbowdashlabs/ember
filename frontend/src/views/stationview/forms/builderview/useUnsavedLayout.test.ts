/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {readDraft, saveDraft} from '@/util/pageDrafts'
import {useFormLayout} from './useFormLayout'
import {useUnsavedLayout} from './useUnsavedLayout'
import type {PageDraft} from './types'

type LeaveGuard = (to: string) => boolean

let leaveGuard: LeaveGuard = () => true

vi.mock('vue-router', () => ({
    onBeforeRouteLeave: (guard: LeaveGuard) => { leaveGuard = guard },
}))

const KEY = 'form-layout-5'

function unsavedQuestion(id: string) {
    return {id, questionType: 'TEXT' as const, title: id, description: '', required: false, shuffle: false, config: {}, branch: null}
}

/** The editor as the builder uses it: its pages, and the unsaved list kept beside them. */
function openEditor() {
    let editor: {layout: ReturnType<typeof useFormLayout>, unsaved: ReturnType<typeof useUnsavedLayout>} | null = null
    mount(defineComponent({
        setup() {
            const layout = useFormLayout()
            editor = {layout, unsaved: useUnsavedLayout(() => KEY, layout.pages)}
            return () => null
        },
    }))
    return editor as unknown as {layout: ReturnType<typeof useFormLayout>, unsaved: ReturnType<typeof useUnsavedLayout>}
}

/**
 * Questions the server does not hold yet are kept in the browser, offered back when the editor opens
 * again, and leaving them behind is asked about first.
 *
 * @vitest-environment happy-dom
 */
describe('useUnsavedLayout', () => {
    beforeEach(() => {
        localStorage.clear()
        localStorage.setItem('storage_consent', 'accepted')
        vi.useFakeTimers()
        leaveGuard = () => true
    })

    afterEach(() => vi.useRealTimers())

    it('lets the editor go while nothing changed', () => {
        const {unsaved} = openEditor()
        unsaved.settle(true)

        expect(unsaved.dirty.value).toBe(false)
        expect(leaveGuard('/forms')).toBe(true)
    })

    it('asks before leaving changed questions behind, and goes where the reader wanted once agreed', () => {
        const {layout, unsaved} = openEditor()
        unsaved.settle(true)
        layout.addQuestion(0, 'TEXT')

        expect(unsaved.dirty.value).toBe(true)
        expect(leaveGuard('/forms')).toBe(false)
        expect(unsaved.askingToLeave.value).toBe(true)

        expect(unsaved.leaveAnyway()).toBe('/forms')
        expect(leaveGuard('/forms')).toBe(true)
    })

    it('keeps the changed questions in the browser once the editing pauses', async () => {
        const {layout, unsaved} = openEditor()
        unsaved.settle(true)
        layout.addQuestion(0, 'TEXT')

        await vi.advanceTimersByTimeAsync(1000)

        expect(readDraft<PageDraft[]>(KEY)?.content[0]?.questions).toHaveLength(1)
    })

    it('offers the kept questions back, and a question added then gets an id of its own', () => {
        const kept: PageDraft[] = [{
            key: 'p0', title: '', description: '', after: {kind: 'NEXT'},
            questions: [unsavedQuestion('temp-1'), unsavedQuestion('temp-2'), unsavedQuestion('temp-3')],
        }]
        saveDraft(KEY, kept)
        const {layout, unsaved} = openEditor()

        unsaved.settle(true)
        expect(unsaved.offered.value?.content).toEqual(kept)
        unsaved.restore()
        layout.addQuestion(0, 'TEXT')

        const ids = layout.allQuestions.value.map(question => question.id)
        expect(ids).toEqual(['temp-1', 'temp-2', 'temp-3', 'temp-4'])
    })

    it('forgets the kept questions where the reader discards them', () => {
        saveDraft(KEY, [])
        const {unsaved} = openEditor()
        unsaved.settle(true)

        unsaved.discard()

        expect(unsaved.offered.value).toBeNull()
        expect(readDraft(KEY)).toBeNull()
    })
})
