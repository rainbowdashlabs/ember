/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount, shallowMount} from '@vue/test-utils'
import {h, nextTick} from 'vue'
import {describe, expect, it} from 'vitest'
import EventEditBody from './EventEditBody.vue'

const i18n = {global: {stubs: {'font-awesome-icon': true}}}

const props = {
    isEdit: true,
    saving: false,
    canFederate: false,
    eventTemplates: [],
    categories: [],
    templates: [],
    attendanceFields: [],
    groups: [],
    tags: [],
    allMembers: [],
    groupMembers: new Map(),
    currentTemplateFields: [],
    fieldDefaults: new Map(),
    partners: [],
    name: 'Dienstabend',
    description: '',
    categoryId: '',
    templateId: '',
    eventType: 'ONE_TIME',
    dayOfWeek: '1',
    startTime: '2026-11-20T19:00',
    endTime: '2026-11-20T21:00',
    repeatUntil: '',
    requiresRegistration: false,
    registrationFields: [],
    requiresConfirmation: false,
    hasDeadline: false,
    registrationDeadline: '',
    restriction: {mode: 'OR' as const, userTypes: [], groupIds: [], tagIds: [], memberIds: []},
    viewRestriction: {mode: 'OR' as const, userTypes: [], groupIds: [], tagIds: [], memberIds: []},
    fields: [],
    reminders: [],
    federationShared: false,
    federationScope: 'ALL',
    federationPartnerIds: [],
    federationPlaces: {},
}

/**
 * The appointment form is one sheet. What the page places into it, the documents and the files, comes
 * before Cancel and Save, so saving is the last thing the form asks for.
 */
describe('EventEditBody', () => {
    it('keeps what the page adds inside the form and above Save', () => {
        const wrapper = shallowMount(EventEditBody, {
            props,
            slots: {default: () => h('div', {'data-testid': 'added'})},
            global: {
                ...i18n.global,
                stubs: {...i18n.global.stubs, NeutralContainer: false, BaseContainer: false, ButtonRow: false, FormSection: false},
            },
        })

        const sheet = wrapper.get('[data-testid="event-form-sheet"]').element
        const added = sheet.querySelector('[data-testid="added"]')
        const actions = sheet.querySelector('[data-testid="event-form-actions"]')

        expect(added).not.toBeNull()
        expect(actions).not.toBeNull()
        expect(added!.compareDocumentPosition(actions!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
        expect(sheet.lastElementChild).toBe(actions)
    })

    it('shows the template\'s field defaults with the fields, in their section after the description', () => {
        const wrapper = mount(EventEditBody, {
            props: {...props, currentTemplateFields: [{id: 1, name: 'Fahrzeug', fieldType: 'TEXT', config: {}} as never]},
            global: {
                ...i18n.global,
                stubs: {
                    ...i18n.global.stubs,
                    EventFieldList: true,
                    MarkdownEditor: true,
                    FieldDefaultsCard: true,
                    FederationCard: true,
                    RestrictionsField: true,
                    EventReminderEditor: true,
                },
            },
        })
        const section = wrapper.get('[data-testid="form-section-fields"]').html()

        expect(section.indexOf('field-defaults-card-stub')).toBeGreaterThan(section.indexOf('event-field-list-stub'))
        expect(wrapper.html().indexOf('markdown-editor-stub')).toBeLessThan(wrapper.html().indexOf('form-section-fields'))
    })

    it('asks about sharing with partner stations last, after the documents and files', () => {
        const wrapper = shallowMount(EventEditBody, {
            props,
            slots: {default: () => h('div', {'data-testid': 'added'})},
            global: {
                ...i18n.global,
                stubs: {...i18n.global.stubs, NeutralContainer: false, BaseContainer: false, FormSection: false},
            },
        })
        const html = wrapper.html()

        expect(html.indexOf('data-testid="added"')).toBeLessThan(html.indexOf('federation-card-stub'))
        expect(wrapper.findComponent({name: 'FederationCard'}).attributes('compact')).toBeDefined()
    })

    /** A part shows the date settings its event group hands down once, in the inherited card, until it overrides one. */
    it('leaves the inherited category, attendance template and reminders to the inherited card on a part', async () => {
        const inheritedSettings = {categoryId: 3, templateId: 4, reminderDays: [2], fieldDefaults: []}
        const partProps = {
            ...props, eventGroupPart: true, inheritedSettings,
            categories: [{id: 3, name: 'Übung'}, {id: 5, name: 'Ausbildung'}] as never,
            templates: [{id: 4, name: 'Dienstabend'}] as never,
        }
        const wrapper = mount(EventEditBody, {
            props: partProps,
            global: {...i18n.global, stubs: {...i18n.global.stubs, EventFieldList: true, MarkdownEditor: true}},
        })

        expect(wrapper.findAll('[data-testid="inherited-setting"]')).toHaveLength(3)
        expect(wrapper.find('[data-testid="event-attendance-template"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="form-section-reminders"]').exists()).toBe(false)
        expect(wrapper.find('option[value="5"]').exists()).toBe(false)

        await wrapper.get('[data-testid="override-setting"]').trigger('click')

        expect(wrapper.emitted('update:categoryId')?.[0]).toEqual(['3'])
        await wrapper.setProps({categoryId: '3'})
        expect(wrapper.find('option[value="5"]').exists()).toBe(true)
    })

    it('opens the sharing section by itself where the template\'s share could not be taken over', () => {
        const wrapper = mount(EventEditBody, {
            props: {...props, federationNote: 'nicht übernommen'},
            global: {...i18n.global, stubs: {...i18n.global.stubs, EventFieldList: true, MarkdownEditor: true, FederationCard: true}},
        })

        expect(wrapper.get('[data-testid="form-section-sharing"] [data-section-toggle]').attributes('aria-expanded')).toBe('true')
    })

    /** Overriding a setting of the event group reveals its own control, and the section holding it opens to show it. */
    it('opens the section of the attendance template and of the reminders once they are overridden', async () => {
        const inheritedSettings = {categoryId: null, templateId: 4, reminderDays: [2], fieldDefaults: []}
        const wrapper = mount(EventEditBody, {
            props: {...props, eventGroupPart: true, inheritedSettings, templates: [{id: 4, name: 'Dienstabend'}] as never},
            global: {...i18n.global, stubs: {...i18n.global.stubs, EventFieldList: true, MarkdownEditor: true}},
        })
        const expanded = (key: string) =>
            wrapper.get(`[data-testid="form-section-${key}"] [data-section-toggle]`).attributes('aria-expanded')

        expect(expanded('fields')).toBe('false')
        await wrapper.get('[data-testid="override-setting"][data-setting="template"]').trigger('click')
        await wrapper.setProps({templateId: '4'})
        expect(expanded('fields')).toBe('true')

        await wrapper.get('[data-testid="override-setting"][data-setting="reminders"]').trigger('click')
        await wrapper.setProps({reminders: [2]})
        expect(expanded('reminders')).toBe('true')
    })

    it('opens who may sign up and the sharing when the page asks for them after a refused save', async () => {
        const wrapper = mount(EventEditBody, {
            props: {...props, groups: [], tags: []},
            global: {...i18n.global, stubs: {...i18n.global.stubs, EventFieldList: true, MarkdownEditor: true, FederationCard: true}},
        })
        const expanded = (key: string) =>
            wrapper.get(`[data-testid="form-section-${key}"] [data-section-toggle]`).attributes('aria-expanded')
        expect(expanded('audience')).toBe('false')
        expect(expanded('sharing')).toBe('false')

        await wrapper.setProps({attention: ['audience', 'sharing']})

        expect(expanded('audience')).toBe('true')
        expect(expanded('sharing')).toBe('true')
    })

    it('hands the focus to the override button once clearing an own setting takes its control away', async () => {
        const inheritedSettings = {categoryId: 3, templateId: null, reminderDays: [], fieldDefaults: []}
        const wrapper = mount(EventEditBody, {
            props: {...props, eventGroupPart: true, inheritedSettings, categoryId: '3', categories: [{id: 3, name: 'Übung'}] as never},
            global: {...i18n.global, stubs: {...i18n.global.stubs, EventFieldList: true, MarkdownEditor: true}},
            attachTo: document.body,
        })

        await wrapper.setProps({categoryId: ''})
        await nextTick()
        await nextTick()

        expect(document.activeElement?.getAttribute('data-setting')).toBe('category')
        wrapper.unmount()
    })
})
