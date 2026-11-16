/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount, shallowMount} from '@vue/test-utils'
import {h} from 'vue'
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
                stubs: {...i18n.global.stubs, NeutralContainer: false, BaseContainer: false, ButtonRow: false},
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

    it('shows the template\'s field defaults with the fields, before the description', () => {
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
        const html = wrapper.html()

        const fields = html.indexOf('event-field-list-stub')
        const defaults = html.indexOf('field-defaults-card-stub')
        const description = html.indexOf('markdown-editor-stub')

        expect(defaults).toBeGreaterThan(fields)
        expect(defaults).toBeLessThan(description)
    })
})
