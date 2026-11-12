/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import TemplateDeadlineOffset from './TemplateDeadlineOffset.vue'

function field(modelValue: number | null) {
    return mount(TemplateDeadlineOffset, {props: {modelValue}})
}

function lastOffset(wrapper: ReturnType<typeof field>) {
    return wrapper.emitted('update:modelValue')?.at(-1)?.[0]
}

/** The deadline of a template, held as hours before the start and shown as days and hours. */
describe('TemplateDeadlineOffset', () => {
    it('shows the offset as days and hours', () => {
        const wrapper = field(53)

        expect((wrapper.get('[data-testid="template-deadline-days"]').element as HTMLInputElement).value).toBe('2')
        expect((wrapper.get('[data-testid="template-deadline-hours"]').element as HTMLInputElement).value).toBe('5')
    })

    it('writes days and hours back as hours', async () => {
        const wrapper = field(53)

        await wrapper.get('[data-testid="template-deadline-days"]').setValue('3')

        expect(lastOffset(wrapper)).toBe(77)
    })

    it('takes a negative entry as none', async () => {
        const wrapper = field(53)

        await wrapper.get('[data-testid="template-deadline-hours"]').setValue('-4')

        expect(lastOffset(wrapper)).toBe(48)
    })

    it('shows no inputs while there is no deadline', () => {
        const wrapper = field(null)

        expect(wrapper.find('[data-testid="template-deadline-days"]').exists()).toBe(false)
    })
})
