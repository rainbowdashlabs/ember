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
import SelectInput from '@/components/input/select/SelectInput.vue'
import BatchCreateView from './BatchCreateView.vue'
import ManualRowsEditor from './batchcreateview/ManualRowsEditor.vue'
import BatchEditTable from './batchcreateview/BatchEditTable.vue'
import EventFormPanel from './eventshared/EventFormPanel.vue'
import {events} from '@/api'
import {localInputToInstant} from '@/util/format'

vi.mock('@/api', () => ({
    events: {
        listTemplates: vi.fn(async () => []),
        getTemplate: vi.fn(),
        createBatchEvents: vi.fn(async () => []),
        generateDates: vi.fn(),
        listTemplateAttachments: vi.fn(async () => []),
        getTemplateFieldDefaults: vi.fn(async () => []),
        getTemplateFederationShare: vi.fn(async () => ({shared: false, scope: null, partnerIds: null})),
        setFederationShare: vi.fn(async () => undefined),
    },
    attendance: {listTemplateFields: vi.fn(async () => [])},
    appointmentDocuments: {listRequirements: vi.fn(async () => [])},
    federation: {listPartners: vi.fn(async () => [])},
}))
const session = vi.hoisted(() => ({loaded: false, federate: false}))
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({loaded: ref(session.loaded), hasPermission: () => session.federate}),
}))
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
    'requiresConfirmation', 'registrationDeadline', 'restriction', 'viewRestriction', 'registrationLimit', 'queueOrder',
    'minRegistrations', 'thresholdDays', 'registrationDeadlineOffsetHours', 'reminderDays', 'eventTemplateId']
const ROW_KEYS = ['name', 'startTime', 'endTime', 'fieldValues']

/** Sets the registration deadline the way the details panel does: a switch and a local date and time. */
async function setDeadline(wrapper: VueWrapper, on: boolean, value: string) {
    const panel = wrapper.findComponent(EventFormPanel)
    panel.vm.$emit('update:requiresRegistration', true)
    panel.vm.$emit('update:registrationDeadline', value)
    panel.vm.$emit('update:hasDeadline', on)
    await flushPromises()
}

/** Writes one appointment by hand, on the fourth of December from half past four to seven. */
async function writeOneRow(wrapper: VueWrapper) {
    await openManualEntry(wrapper)
    const editor = wrapper.findComponent(ManualRowsEditor)
    editor.findComponent(DateInput).vm.$emit('update:modelValue', '2026-12-04')
    await flushPromises()
    editor.findAllComponents(TimeShortInput)[0]!.vm.$emit('update:modelValue', '16:30')
    await flushPromises()
    editor.findAllComponents(TimeShortInput)[1]!.vm.$emit('update:modelValue', '19:00')
    await flushPromises()
}

/** An appointment template for repeating drills that sets everything a batch can take from it. */
const DRILL_TEMPLATE = {
    template: {
        id: 9, stationId: 1, name: 'Atemschutz', title: 'Atemschutzübung', description: null, categoryId: null,
        eventType: 'RECURRING', requiresRegistration: true, registrationDeadlineOffsetHours: 24, requiresConfirmation: false,
        restrictionMode: null, viewRestrictionMode: 'AND', attendanceTemplateId: null, registrationLimit: 12,
        minRegistrations: 3, thresholdDays: 2, registrationCloseDays: null, queueOrder: 'FAIRNESS',
    },
    fields: [],
    registrationFields: [{id: 1, name: 'Größe', fieldType: 'TEXT', config: {}, overview: false}],
    reminderDays: [7, 1],
    restriction: null,
}

/** Mounts the batch screen with the drill template on offer and applies it. */
async function applyDrillTemplate() {
    session.loaded = true
    vi.mocked(events.listTemplates).mockResolvedValue([{id: 9, name: 'Atemschutz'}] as never)
    vi.mocked(events.getTemplate).mockResolvedValue(DRILL_TEMPLATE as never)
    const wrapper = await mountSuspended(BatchCreateView, {global: {stubs: {EventFormPanel: true}}})
    await flushPromises()
    wrapper.findComponent(SelectInput).vm.$emit('update:modelValue', '9')
    await flushPromises()
    session.loaded = false
    return wrapper
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

    it('sends what the applied template sets for every appointment and names what it leaves out', async () => {
        const wrapper = await applyDrillTemplate()

        expect(wrapper.find('[data-testid="batch-template-carried"]').text()).toContain('Fragen zur Anmeldung: 1')
        expect(wrapper.find('[data-testid="batch-template-carried"]').text()).toContain('Erinnerungen 1, 7 Tage vorher')
        expect(wrapper.find('[data-testid="batch-template-left-out"]').text()).toContain('keine wiederkehrenden Termine')

        await writeOneRow(wrapper)
        const sent = await create(wrapper)

        expectReadable(sent)
        expect(sent).toMatchObject({
            requiresRegistration: true,
            registrationLimit: 12,
            queueOrder: 'FAIRNESS',
            minRegistrations: 3,
            thresholdDays: 2,
            registrationDeadlineOffsetHours: 24,
            reminderDays: [7, 1],
            eventTemplateId: 9,
        })
    })

    it('shares every appointment of the batch the way the template shares, for somebody who may share', async () => {
        session.federate = true
        vi.mocked(events.getTemplateFederationShare).mockResolvedValue({shared: true, scope: 'ALL_PARTNERS', partnerIds: null} as never)
        vi.mocked(events.createBatchEvents).mockResolvedValue([{id: 31}, {id: 32}] as never)
        try {
            const wrapper = await applyDrillTemplate()
            await writeOneRow(wrapper)
            await create(wrapper)

            expect(vi.mocked(events.setFederationShare).mock.calls.map(call => call[0])).toEqual([31, 32])
        } finally {
            session.federate = false
        }
    })

    it('treats the batch as created and puts the form away where sharing the appointments fails', async () => {
        session.federate = true
        vi.mocked(events.getTemplateFederationShare).mockResolvedValue({shared: true, scope: 'ALL_PARTNERS', partnerIds: null} as never)
        vi.mocked(events.createBatchEvents).mockResolvedValue([{id: 41}] as never)
        vi.mocked(events.setFederationShare).mockRejectedValueOnce(new Error('refused'))
        try {
            const wrapper = await applyDrillTemplate()
            await writeOneRow(wrapper)
            await create(wrapper)

            expect(wrapper.text()).toContain('1 Termine erstellt')
            expect(wrapper.text()).toContain('nicht alle für Partnerwachen freigegeben')
            expect(wrapper.findComponent(BatchEditTable).exists()).toBe(false)
            expect(wrapper.find('[data-testid="batch-open-overview"]').exists()).toBe(true)
        } finally {
            session.federate = false
        }
    })
})
