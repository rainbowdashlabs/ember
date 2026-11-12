/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import TemplateManageView from './TemplateManageView.vue'
import {
    EventType,
    RestrictionMode,
    ShareScope,
    StationPermission,
    type EventAttachment,
    type EventTemplate,
    type RestrictionAudience,
    type TemplateDetailResponse,
} from '@/api/generated/schema'

const api = vi.hoisted(() => ({
    events: {
        listTemplates: vi.fn(),
        getTemplate: vi.fn(),
        createTemplate: vi.fn(),
        updateTemplate: vi.fn(),
        setTemplateFields: vi.fn(),
        setTemplateRestrictions: vi.fn(),
        setTemplateReminders: vi.fn(),
        listTemplateAttachments: vi.fn(),
        attachTemplateFile: vi.fn(),
        getTemplateFieldDefaults: vi.fn(),
        setTemplateFieldDefaults: vi.fn(),
        getTemplateFederationShare: vi.fn(),
        setTemplateFederationShare: vi.fn(),
        deleteTemplate: vi.fn(),
    },
}))
vi.mock('@/api', () => api)

const held = ref<string[]>([])
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({
        loaded: ref(true),
        hasPermission: (permission: string) => held.value.includes(permission),
    }),
}))
vi.mock('vue-router', () => ({useRouter: () => ({push: vi.fn()})}))

const NOBODY: RestrictionAudience = {groupIds: [], memberIds: [], mode: RestrictionMode.OR, tagIds: [], userTypes: []}

const ORIGINAL: EventTemplate = {
    id: 31,
    stationId: '00000000-0000-0000-0000-000000000001',
    name: 'Übungsabend',
    title: null,
    description: null,
    categoryId: null,
    eventType: EventType.RECURRING,
    requiresRegistration: true,
    requiresConfirmation: null,
    registrationLimit: null,
    registrationDeadlineOffsetHours: null,
    minRegistrations: null,
    thresholdDays: null,
    registrationCloseDays: null,
    attendanceTemplateId: 4,
    restrictionMode: null,
    viewRestrictionMode: RestrictionMode.OR,
}

function attachment(id: number, fileId: number, label: string | null, internal: boolean): EventAttachment {
    return {
        id,
        eventId: 31,
        fileId,
        label,
        internal,
        sortOrder: id,
        createdAt: '2026-05-12T10:00:00Z',
        fileName: `datei-${id}.pdf`,
        mimeType: 'application/pdf',
        fileSize: 1024,
        contentHash: `hash-${id}`,
    }
}

async function duplicate() {
    const wrapper = mount(TemplateManageView, {
        global: {stubs: {ViewContent: {template: '<div><slot/></div>'}, Modal: true}},
    })
    await flushPromises()
    await wrapper.get('[data-testid="duplicate-template"]').trigger('click')
    await flushPromises()
}

/**
 * A copy of a template is opened straight away to change the part that differs, so everything the
 * template hands its appointments has to arrive on the copy: its files in their order, the starting
 * values of its attendance fields and, for somebody who may share appointments, its partner share.
 */
describe('TemplateManageView duplicating a template', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        held.value = [StationPermission.EVENTS_FEDERATE]
        api.events.listTemplates.mockResolvedValue([ORIGINAL])
        api.events.getTemplate.mockResolvedValue({
            template: ORIGINAL,
            fields: [],
            registrationFields: [],
            reminderDays: [],
            restriction: {register: NOBODY, view: NOBODY},
        } satisfies TemplateDetailResponse)
        api.events.createTemplate.mockResolvedValue({...ORIGINAL, id: 32})
        api.events.updateTemplate.mockResolvedValue({...ORIGINAL, id: 32})
        api.events.setTemplateRestrictions.mockResolvedValue({register: NOBODY, view: NOBODY})
        api.events.listTemplateAttachments.mockResolvedValue([
            attachment(7, 70, 'Laufzettel', false),
            attachment(3, 30, null, true),
        ])
        api.events.attachTemplateFile.mockResolvedValue(attachment(9, 70, null, false))
        api.events.getTemplateFieldDefaults.mockResolvedValue([
            {eventId: 31, fieldId: 11, source: 'VALUE', value: 'Halle'},
            {eventId: 31, fieldId: 12, source: 'EVENT_NAME', value: null},
        ])
        api.events.setTemplateFieldDefaults.mockResolvedValue([])
        api.events.getTemplateFederationShare.mockResolvedValue({
            shared: true, scope: ShareScope.SPECIFIC, partnerIds: [5, 6],
        })
        api.events.setTemplateFederationShare.mockResolvedValue(undefined)
    })

    it('hangs the same files on the copy, with label and flag, in the original order', async () => {
        await duplicate()

        expect(api.events.listTemplateAttachments).toHaveBeenCalledWith(31)
        expect(api.events.attachTemplateFile.mock.calls).toEqual([
            [32, 70, 'Laufzettel', false],
            [32, 30, null, true],
        ])
    })

    it('gives the copy the original field starting values', async () => {
        await duplicate()

        expect(api.events.setTemplateFieldDefaults).toHaveBeenCalledWith(32, [
            {fieldId: 11, source: 'VALUE', value: 'Halle'},
            {fieldId: 12, source: 'EVENT_NAME', value: null},
        ])
    })

    it('shares the copy the way the original is shared', async () => {
        await duplicate()

        expect(api.events.setTemplateFederationShare).toHaveBeenCalledWith(32, ShareScope.SPECIFIC, [5, 6])
    })

    it('makes no share call at all for somebody who may not share appointments', async () => {
        held.value = []

        await duplicate()

        expect(api.events.getTemplateFederationShare).not.toHaveBeenCalled()
        expect(api.events.setTemplateFederationShare).not.toHaveBeenCalled()
        expect(api.events.attachTemplateFile).toHaveBeenCalledTimes(2)
    })
})
