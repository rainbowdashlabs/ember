/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import EventFormPanel from './EventFormPanel.vue'

const NO_RESTRICTION = {mode: 'OR' as const, userTypes: [], groupIds: [], tagIds: [], memberIds: []}

function panel(eventType: string, extra: Record<string, unknown> = {}) {
    return mount(EventFormPanel, {
        props: {
            categories: [],
            templates: [],
            name: '',
            description: '',
            categoryId: '',
            templateId: '',
            fields: [],
            restriction: NO_RESTRICTION,
            viewRestriction: NO_RESTRICTION,
            eventType,
            dayOfWeek: '5',
            startTime: '2026-11-20T19:00',
            endTime: '2026-11-20T21:00',
            showSchedule: true,
            ...extra,
        },
        global: {stubs: {'font-awesome-icon': true, MarkdownEditor: true, EventFieldList: true, RestrictionsField: true}},
    })
}

/**
 * Whether an appointment repeats is one switch on the line under its times, with the rhythm and the
 * weekday beside it once it does.
 */
describe('EventFormPanel repeat line', () => {
    it('offers rhythm and weekday only once the appointment repeats', () => {
        const single = panel('ONE_TIME')
        expect(single.find('[aria-label="Wochentag"]').exists()).toBe(false)
        expect(single.find('[aria-label="Art"]').exists()).toBe(false)

        const series = panel('RECURRING')
        expect(series.find('[aria-label="Wochentag"]').exists()).toBe(true)
        expect(series.find('[aria-label="Art"]').exists()).toBe(true)
    })

    it('turns a single appointment into a weekly series and back', async () => {
        const wrapper = panel('ONE_TIME')
        const toggle = wrapper.get('[data-testid="event-type-toggle"] [role="switch"]')

        await toggle.trigger('click')
        expect(wrapper.emitted('update:eventType')?.at(-1)).toEqual(['RECURRING'])

        await wrapper.setProps({eventType: 'RECURRING'})
        await toggle.trigger('click')
        expect(wrapper.emitted('update:eventType')?.at(-1)).toEqual(['ONE_TIME'])
    })

    it('leaves the repeat line out for a part of an event group', () => {
        expect(panel('ONE_TIME', {eventGroupPart: true}).find('[data-testid="event-type-toggle"]').exists()).toBe(false)
    })

    it('keeps the description with the essentials and the attendance template with the fields in their folded section', () => {
        const wrapper = panel('ONE_TIME')
        const html = wrapper.html()

        const description = html.indexOf('markdown-editor-stub')
        const section = html.indexOf('form-section-fields')
        const template = html.indexOf('event-attendance-template')
        const fields = html.indexOf('event-field-list-stub')

        expect(description).toBeGreaterThan(-1)
        expect(description).toBeLessThan(section)
        expect(section).toBeLessThan(template)
        expect(template).toBeLessThan(fields)
        expect(wrapper.get('[data-testid="form-section-fields"] [data-section-toggle]').attributes('aria-expanded')).toBe('false')
    })

    it('keeps the explanations of the sign-up settings behind hint icons', () => {
        const wrapper = panel('RECURRING', {
            requiresRegistration: true, requiresConfirmation: false,
            registrationDeadline: {kind: 'NONE', at: null, offsetMinutes: null, anchor: null},
            minRegistrations: 2,
        })

        const paragraphs = wrapper.findAll('p').map(paragraph => paragraph.text())

        expect(wrapper.find('input[placeholder="Leer = unbegrenzt"]').exists()).toBe(true)
        expect(paragraphs.some(text => text.includes('Leer = unbegrenzt'))).toBe(false)
        expect(paragraphs.some(text => text.includes('von einem Verwalter bestätigt'))).toBe(false)
        expect(wrapper.findAll('[data-testid="hint-icon"]').length).toBeGreaterThanOrEqual(3)
    })

    it('offers the restrictions as buttons and shows a picker only once one is added', async () => {
        const wrapper = panel('ONE_TIME', {groups: [], tags: []})
        expect(wrapper.find('[data-testid="restriction-registration"]').exists()).toBe(false)

        await wrapper.get('[data-testid="restrict-registration"]').trigger('click')

        expect(wrapper.find('[data-testid="restriction-registration"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="restrict-registration"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="restrict-visibility"]').exists()).toBe(true)
    })

    it('shows a restriction that names somebody straight away and clears it on removal', async () => {
        const wrapper = panel('ONE_TIME', {groups: [], tags: [], viewRestriction: {...NO_RESTRICTION, groupIds: [4]}})
        expect(wrapper.find('[data-testid="restriction-visibility"]').exists()).toBe(true)

        await wrapper.get('[data-testid="restriction-visibility"] [data-testid="restriction-remove"]').trigger('click')

        expect(wrapper.emitted('update:viewRestriction')?.at(-1)?.[0]).toMatchObject({groupIds: [], userTypes: []})
    })

    it('keeps a restriction open while its selection is emptied to pick another', async () => {
        const wrapper = panel('ONE_TIME', {groups: [], tags: [], viewRestriction: {...NO_RESTRICTION, groupIds: [4]}})

        await wrapper.setProps({viewRestriction: NO_RESTRICTION})

        expect(wrapper.find('[data-testid="restriction-visibility"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="restrict-visibility"]').exists()).toBe(false)
    })
})

/**
 * The waiting list order stands next to the participant limit, and only once there is a limit:
 * without one, nothing waits.
 */
describe('EventFormPanel waiting list order', () => {
    const registration = {requiresRegistration: true, requiresConfirmation: false, queueOrder: 'TIME'}

    it('offers the order once a limit is set', async () => {
        const wrapper = panel('ONE_TIME', {...registration, registrationLimit: 12})

        expect(wrapper.text()).toContain('weitere Anmeldungen auf die Nachrückliste')
        const select = wrapper.get('[data-testid="queue-order"] select')
        await select.setValue('FAIRNESS')

        expect(wrapper.emitted('update:queueOrder')?.at(-1)).toEqual(['FAIRNESS'])
    })

    it('offers no order without a limit', () => {
        const wrapper = panel('ONE_TIME', {...registration, registrationLimit: undefined})

        expect(wrapper.find('[data-testid="queue-order"]').exists()).toBe(false)
    })

    /** While questions are asked, sign-up cannot be switched off, and the form says why. */
    it('holds sign-up on while questions are asked and says why', () => {
        const held = panel('ONE_TIME', {requiresRegistration: true, signUpHeld: true})
        const toggle = held.get('[aria-labelledby$="-registration"]')

        expect(toggle.attributes('disabled')).toBeDefined()
        expect(held.find('[aria-label="Fragen werden bei der Anmeldung beantwortet. Solange es Fragen gibt, bleibt die Anmeldung eingeschaltet."]').exists()).toBe(true)

        const free = panel('ONE_TIME', {requiresRegistration: true})
        expect(free.get('[aria-labelledby$="-registration"]').attributes('disabled')).toBeUndefined()
    })
})

/**
 * The deadline is one rule in the open sign-up block, shown while sign-up is on. A series whose first
 * date has passed previews the deadline of the next day its pattern falls on.
 */
describe('EventFormPanel registration deadline', () => {
    const weekBefore = {kind: 'RELATIVE', at: null, offsetMinutes: 10080, anchor: 'MIDNIGHT'}

    it('edits the rule while sign-up is on and leaves it out of the minimum section', async () => {
        expect(panel('ONE_TIME', {requiresRegistration: false, registrationDeadline: weekBefore})
            .find('[data-testid="registration-deadline-kind"]').exists()).toBe(false)
        const wrapper = panel('ONE_TIME', {requiresRegistration: true, registrationDeadline: weekBefore})

        expect(wrapper.find('[data-testid="form-section-turnout"] [data-testid="registration-deadline-kind"]').exists()).toBe(false)
        await wrapper.get('[data-testid="registration-deadline-kind"]').setValue('NONE')

        expect(wrapper.emitted('update:registrationDeadline')?.at(-1))
            .toEqual([{kind: 'NONE', at: null, offsetMinutes: null, anchor: null}])
    })

    it('previews the deadline of the start entered', () => {
        const wrapper = panel('ONE_TIME', {
            requiresRegistration: true, registrationDeadline: weekBefore, startTime: '2099-11-21T10:00', timeZone: 'Europe/Berlin',
        })

        expect(wrapper.get('[data-testid="registration-deadline-next"]').text()).toContain('14.11.2099, 00:00')
    })

    it('previews the next date of a series whose start has passed', () => {
        const wrapper = panel('RECURRING', {
            requiresRegistration: true, registrationDeadline: weekBefore, startTime: '2020-01-03T19:00',
            dayOfWeek: '5', timeZone: 'Europe/Berlin',
        })

        const line = wrapper.get('[data-testid="registration-deadline-next"]').text()
        expect(line).not.toContain('2019')
        expect(line).toContain('Fr')
    })
})
