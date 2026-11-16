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
import EventFormPanel from './eventshared/EventFormPanel.vue'
import {events} from '@/api'
import {localInputToInstant} from '@/util/format'

vi.mock('@/api', () => ({
    events: {listTemplates: vi.fn(async () => []), getTemplate: vi.fn(), createBatchEvents: vi.fn(async () => []), generateDates: vi.fn()},
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

const ADVENT = '\uFEFF ImprDatum,Von,Bis,Titel\n'
    + '04.12.26,16:30,19:00,Adventszauber Freitag 1\n'
    + '04.12.26,19:00,21:30,Adventszauber Freitag 2\n'
    + '05.12.26,16:30,19:00,Adventszauber Samstag 1\n'
    + '05.12.26,19:00,21:30,Adventszauber Samstag 2\n'

const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/

/** The keys the server reads in a batch and in each of its rows; any other key, or a value of another shape, is refused. */
const REQUEST_KEYS = ['name', 'description', 'templateId', 'categoryId', 'inlineFields', 'rows', 'requiresRegistration',
    'requiresConfirmation', 'registrationDeadline', 'restriction', 'viewRestriction']
const ROW_KEYS = ['name', 'startTime', 'endTime', 'fieldValues']

/** Sets the registration deadline the way the details panel does: a switch and a local date and time. */
async function setDeadline(wrapper: VueWrapper, on: boolean, value: string) {
    const panel = wrapper.findComponent(EventFormPanel)
    panel.vm.$emit('update:requiresRegistration', true)
    panel.vm.$emit('update:registrationDeadline', value)
    panel.vm.$emit('update:hasDeadline', on)
    await flushPromises()
}

async function create(wrapper: VueWrapper) {
    await clickNext(wrapper)
    expect(wrapper.findComponent(BatchEditTable).exists()).toBe(true)
    await clickNext(wrapper)
    const sent = vi.mocked(events.createBatchEvents).mock.calls.at(-1)![0]
    return JSON.parse(JSON.stringify(sent)) as Record<string, unknown> & {rows: Record<string, unknown>[]}
}

/** Checks a request the way the server reads it: only known keys, and every moment as an instant. */
function expectReadable(sent: Record<string, unknown> & {rows: Record<string, unknown>[]}) {
    expect(Object.keys(sent).filter(key => !REQUEST_KEYS.includes(key))).toEqual([])
    for (const row of sent.rows) {
        expect(Object.keys(row).filter(key => !ROW_KEYS.includes(key))).toEqual([])
        expect(row.startTime).toMatch(INSTANT)
        expect(row.endTime).toMatch(INSTANT)
    }
    if (sent.registrationDeadline !== undefined) expect(sent.registrationDeadline).toMatch(INSTANT)
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

    it('sends the imported file and the registration deadline in a shape the server reads', async () => {
        const wrapper = await mountSuspended(BatchCreateView, {global: {stubs: {EventFormPanel: true}}})
        await setDeadline(wrapper, true, '2026-12-01T18:00')
        await clickNext(wrapper)
        await wrapper.findAllComponents(SelectionToggleButton).find(button => button.text() === 'CSV-Import')!.trigger('click')
        await flushPromises()
        const input = wrapper.find('input[type="file"]')
        const file = new File([new TextEncoder().encode(ADVENT)], 'advent.csv', {type: 'text/csv'})
        Object.defineProperty(input.element, 'files', {value: [file], configurable: true})
        await input.trigger('change')
        await vi.waitFor(() => expect(wrapper.text()).toContain('Spalten zuordnen'))

        const sent = await create(wrapper)

        expectReadable(sent)
        expect(sent.registrationDeadline).toBe(localInputToInstant('2026-12-01T18:00'))
        expect(sent.rows).toEqual([
            {name: 'Adventszauber Freitag 1', startTime: localInputToInstant('2026-12-04T16:30'), endTime: localInputToInstant('2026-12-04T19:00'), fieldValues: {}},
            {name: 'Adventszauber Freitag 2', startTime: localInputToInstant('2026-12-04T19:00'), endTime: localInputToInstant('2026-12-04T21:30'), fieldValues: {}},
            {name: 'Adventszauber Samstag 1', startTime: localInputToInstant('2026-12-05T16:30'), endTime: localInputToInstant('2026-12-05T19:00'), fieldValues: {}},
            {name: 'Adventszauber Samstag 2', startTime: localInputToInstant('2026-12-05T19:00'), endTime: localInputToInstant('2026-12-05T21:30'), fieldValues: {}},
        ])
    })

    it('sends a row written by hand without a deadline that was switched off again', async () => {
        const wrapper = await mountSuspended(BatchCreateView, {global: {stubs: {EventFormPanel: true}}})
        await setDeadline(wrapper, false, '2026-12-01T18:00')
        await openManualEntry(wrapper)
        const editor = wrapper.findComponent(ManualRowsEditor)
        editor.findComponent(DateInput).vm.$emit('update:modelValue', '2026-12-04')
        await flushPromises()
        editor.findAllComponents(TimeShortInput)[0]!.vm.$emit('update:modelValue', '16:30')
        await flushPromises()
        editor.findAllComponents(TimeShortInput)[1]!.vm.$emit('update:modelValue', '19:00')
        await flushPromises()

        const sent = await create(wrapper)

        expectReadable(sent)
        expect(sent.registrationDeadline).toBeUndefined()
        expect(sent.rows).toEqual([
            {name: null, startTime: localInputToInstant('2026-12-04T16:30'), endTime: localInputToInstant('2026-12-04T19:00'), fieldValues: {}},
        ])
    })
})
