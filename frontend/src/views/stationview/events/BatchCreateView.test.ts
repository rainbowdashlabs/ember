/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ref} from 'vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import StepProgressBar from '@/components/display/StepProgressBar.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import BatchCreateView from './BatchCreateView.vue'
import ManualRowsEditor from './batchcreateview/ManualRowsEditor.vue'
import BatchEditTable from './batchcreateview/BatchEditTable.vue'

vi.mock('@/api', () => ({
    events: {listTemplates: vi.fn(async () => []), getTemplate: vi.fn(), createBatchEvents: vi.fn(), generateDates: vi.fn()},
    attendance: {listTemplateFields: vi.fn(async () => [])},
}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({loaded: ref(false)})}))
vi.mock('@/composables/useEventRoutes', () => ({useEventRoutes: () => ({index: 'events'})}))
vi.mock('@/composables/useEventEditDeps', () => ({
    useEventEditDeps: () => ({
        categories: ref([]),
        templates: ref([]),
        groups: ref([]),
        tags: ref([]),
        reload: vi.fn(async () => undefined),
    }),
}))
vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRouter: () => ({push: vi.fn()}),
}))

async function clickNext(wrapper: VueWrapper) {
    const next = wrapper.findAllComponents(PrimaryButton).filter(button => button.isVisible()).at(-1)!
    await next.trigger('click')
    await flushPromises()
}

async function openManualEntry(wrapper: VueWrapper) {
    await clickNext(wrapper)
    await wrapper.findAllComponents(SelectionToggleButton).find(button => button.text() === 'Eigene Termine')!.trigger('click')
    await flushPromises()
}

/** Creating appointments in a batch, from the details through the dates to the table. */
describe('BatchCreateView', () => {
    it('shows the rows written by hand as typed after going back from the table', async () => {
        const wrapper = await mountSuspended(BatchCreateView, {global: {stubs: {EventFormPanel: true}}})
        await openManualEntry(wrapper)
        const editor = wrapper.findComponent(ManualRowsEditor)
        editor.findComponent(DateInput).vm.$emit('update:modelValue', '2026-12-04')
        await flushPromises()
        editor.findAllComponents(TimeShortInput)[0]!.vm.$emit('update:modelValue', '16:30')
        await flushPromises()
        editor.findAllComponents(TimeShortInput)[1]!.vm.$emit('update:modelValue', '19:00')
        await flushPromises()
        editor.findComponent(TextInput).vm.$emit('update:modelValue', 'Adventszauber Freitag 1')
        await flushPromises()
        await clickNext(wrapper)
        expect(wrapper.findComponent(BatchEditTable).exists()).toBe(true)

        wrapper.findComponent(StepProgressBar).vm.$emit('update:currentStep', 1)
        await flushPromises()

        const back = wrapper.findComponent(ManualRowsEditor)
        expect(back.isVisible()).toBe(true)
        expect(back.findComponent(DateInput).props('modelValue')).toBe('2026-12-04')
        expect(back.findAllComponents(TimeShortInput).map(input => input.props('modelValue'))).toEqual(['16:30', '19:00'])
        expect(back.findComponent(TextInput).props('modelValue')).toBe('Adventszauber Freitag 1')
    })

    it('keeps the rows written by hand when switching to another way of entry and back', async () => {
        const wrapper = await mountSuspended(BatchCreateView, {global: {stubs: {EventFormPanel: true}}})
        await openManualEntry(wrapper)
        wrapper.findComponent(ManualRowsEditor).findComponent(DateInput).vm.$emit('update:modelValue', '2026-12-05')
        await flushPromises()

        const chips = wrapper.findAllComponents(SelectionToggleButton)
        await chips.find(button => button.text() === 'CSV-Import')!.trigger('click')
        await chips.find(button => button.text() === 'Eigene Termine')!.trigger('click')
        await flushPromises()

        expect(wrapper.findComponent(ManualRowsEditor).findComponent(DateInput).props('modelValue')).toBe('2026-12-05')
    })
})
