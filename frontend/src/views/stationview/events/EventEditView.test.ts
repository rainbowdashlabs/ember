/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {computed, ref} from 'vue'
import EventEditView from './EventEditView.vue'
import EventEditBody from './eventeditview/EventEditBody.vue'
import {
    EventType,
    FederationStatus,
    RestrictionMode,
    ShareScope,
    StationPermission,
    type EventAttachment,
    type EventTemplate,
    type PartnerResponse,
    type RestrictionAudience,
    type TemplateDetailResponse,
} from '@/api/generated/schema'
import {createSessionInfo} from '@/test/mocks/factories'

const api = vi.hoisted(() => ({
    events: {
        getTemplate: vi.fn(),
        listTemplateAttachments: vi.fn(),
        getTemplateFieldDefaults: vi.fn(),
        getTemplateFederationShare: vi.fn(),
        getEvent: vi.fn(),
        getRestrictions: vi.fn(),
        getEventFields: vi.fn(),
        getEventReminders: vi.fn(),
        getFieldDefaults: vi.fn(),
        getFederationShare: vi.fn(),
        getPartnerPlaces: vi.fn(),
        listRegistrationFields: vi.fn(),
        listEventAttachments: vi.fn(),
        attachEventFile: vi.fn(),
    },
    eventGroups: {eventGroupOfEvent: vi.fn(async () => null)},
    federation: {listPartners: vi.fn()},
    appointmentDocuments: {offeredTemplates: vi.fn(), listRequirements: vi.fn()},
}))
vi.mock('@/api', () => api)

const held = ref<string[]>([])
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({
        loaded: ref(true),
        sessionInfo: ref(createSessionInfo()),
        hasPermission: (permission: string) => held.value.includes(permission),
    }),
}))
vi.mock('@/composables/useEventRoutes', () => ({useEventRoutes: () => ({index: 'events'})}))
const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {}}))
vi.mock('vue-router', () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn()}),
}))
/** Field 11 is on sheet 4, the template's, and field 21 on sheet 7. */
const SHEET_FIELDS = vi.hoisted(() => [{id: 11, templateId: 4}, {id: 21, templateId: 7}])
vi.mock('./eventeditview/useEventEditData', () => ({
    useEventEditData: (selectedSheet: () => string) => ({
        props: computed(() => ({
            eventTemplates: [], categories: [], templates: [], groups: [], tags: [],
            attendanceFields: SHEET_FIELDS.filter(field => String(field.templateId) === selectedSheet()),
            allMembers: [], groupMembers: new Map(), currentTemplateFields: [],
        })),
        load: async () => undefined,
    }),
}))

const NOBODY: RestrictionAudience = {groupIds: [], memberIds: [], mode: RestrictionMode.AND, tagIds: [], userTypes: []}

const TEMPLATE: EventTemplate = {
    id: 31,
    stationId: '00000000-0000-0000-0000-000000000001',
    name: 'Übungsabend',
    title: null,
    description: null,
    categoryId: null,
    eventType: null,
    requiresRegistration: null,
    requiresConfirmation: null,
    registrationLimit: null,
    registrationDeadlineOffsetHours: null,
    minRegistrations: null,
    thresholdDays: null,
    registrationCloseDays: null,
    attendanceTemplateId: 4,
    restrictionMode: null,
    viewRestrictionMode: RestrictionMode.AND,
}

function partner(id: number, status: FederationStatus = FederationStatus.ACTIVE): PartnerResponse {
    return {partner: {id, status}, partnerStationName: `Wache ${id}`} as PartnerResponse
}

function file(id: number, fileName: string, label: string | null = null, internal = false): EventAttachment {
    return {
        id, eventId: 31, fileId: 40 + id, label, internal, sortOrder: id,
        createdAt: '2026-05-12T10:00:00Z', fileName, mimeType: 'application/pdf', fileSize: 1024, contentHash: `h${id}`,
    }
}

async function applyTemplateInEditor() {
    const wrapper = mount(EventEditView, {
        global: {stubs: {ViewContent: {template: '<div><slot/></div>'}, EventEditBody: true, DocumentRequirementsEditor: true}},
    })
    await flushPromises()
    wrapper.findComponent(EventEditBody).vm.$emit('apply-template', '31')
    await flushPromises()
    return wrapper
}

/**
 * A new appointment made from a template takes over what the template carries, so the save that
 * creates it writes the field starting values and the share. Somebody who may not share
 * appointments gets the share skipped with a note rather than a save that fails over it.
 */
describe('EventEditView creating from a template', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        route.params = {}
        held.value = [StationPermission.EVENTS_FEDERATE]
        api.events.getTemplate.mockResolvedValue({
            template: TEMPLATE, fields: [], registrationFields: [], reminderDays: [],
            restriction: {register: NOBODY, view: NOBODY},
        } satisfies TemplateDetailResponse)
        api.events.listTemplateAttachments.mockResolvedValue([file(1, 'laufzettel.pdf')])
        api.events.getTemplateFieldDefaults.mockResolvedValue([
            {eventId: 31, fieldId: 11, source: 'VALUE', value: 'Halle'},
            {eventId: 31, fieldId: 21, source: 'VALUE', value: 'Hof'},
        ])
        api.events.getTemplateFederationShare.mockResolvedValue({shared: true, scope: ShareScope.SPECIFIC, partnerIds: [5, 6, 9]})
        api.federation.listPartners.mockResolvedValue([partner(5), partner(6, FederationStatus.SUSPENDED)])
        api.appointmentDocuments.offeredTemplates.mockResolvedValue({total: 0, items: []})
        api.appointmentDocuments.listRequirements.mockResolvedValue([])
    })

    it('takes the field starting values into the form', async () => {
        const wrapper = await applyTemplateInEditor()

        const body = wrapper.findComponent(EventEditBody)
        expect(api.events.getTemplateFieldDefaults).toHaveBeenCalledWith(31)
        expect(body.props('fieldDefaults').get(11)).toEqual({source: 'VALUE', value: 'Halle'})
        expect(body.props('fieldDefaults').has(21), 'a field off the template\'s sheet gets no value').toBe(false)
    })

    it('drops the starting values of the sheet left behind when the sheet changes', async () => {
        const wrapper = await applyTemplateInEditor()

        const body = wrapper.findComponent(EventEditBody)
        body.vm.$emit('update:templateId', '7')
        await flushPromises()

        expect(body.props('fieldDefaults').size).toBe(0)
    })

    it('takes the share over, dropping partners that are not active', async () => {
        const wrapper = await applyTemplateInEditor()

        const body = wrapper.findComponent(EventEditBody)
        expect(body.props('federationShared')).toBe(true)
        expect(body.props('federationScope')).toBe(ShareScope.SPECIFIC)
        expect(body.props('federationPartnerIds')).toEqual([5])
        expect(body.props('federationNote')).toContain('2')
    })

    it('skips a template\'s share without the right to share and says so', async () => {
        held.value = []

        const wrapper = await applyTemplateInEditor()

        const body = wrapper.findComponent(EventEditBody)
        expect(api.federation.listPartners).not.toHaveBeenCalled()
        expect(body.props('federationShared')).toBe(false)
        expect(body.props('federationNote')).toContain('Termine föderieren')
        expect(api.events.getTemplateFieldDefaults).toHaveBeenCalledWith(31)
    })

    it('names the template files that are attached on saving', async () => {
        const wrapper = await applyTemplateInEditor()

        expect(wrapper.get('[data-testid="template-files-note"]').text()).toContain('laufzettel.pdf')
    })

    it('says nothing about sharing to somebody without the right when the template shares nothing', async () => {
        held.value = []
        api.events.getTemplateFederationShare.mockResolvedValue({shared: false, scope: null, partnerIds: null})

        const wrapper = await applyTemplateInEditor()

        expect(wrapper.findComponent(EventEditBody).props('federationNote')).toBe('')
    })
})

/**
 * A template applied to an appointment that already exists: the server links a template's files
 * only when it creates an appointment, so the editor attaches them right away, as every change to
 * the files of a saved appointment is written.
 */
describe('EventEditView applying a template to a saved appointment', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        route.params = {id: '12'}
        held.value = [StationPermission.EVENTS_FEDERATE]
        api.events.getTemplate.mockResolvedValue({
            template: TEMPLATE, fields: [], registrationFields: [], reminderDays: [],
            restriction: {register: NOBODY, view: NOBODY},
        } satisfies TemplateDetailResponse)
        api.events.getEvent.mockResolvedValue({id: 12, name: 'Dienst', eventType: EventType.ONE_TIME})
        api.events.getRestrictions.mockResolvedValue({register: NOBODY, view: NOBODY})
        api.events.getEventFields.mockResolvedValue([])
        api.events.getEventReminders.mockResolvedValue([])
        api.events.getFieldDefaults.mockResolvedValue([])
        api.events.getFederationShare.mockResolvedValue({shared: false, scope: null, partnerIds: null})
        api.events.getPartnerPlaces.mockResolvedValue([])
        api.events.listRegistrationFields.mockResolvedValue([])
        api.events.listEventAttachments.mockResolvedValue([file(5, 'schon-da.pdf')])
        api.events.attachEventFile.mockImplementation(async () => file(9, 'neu.pdf'))
        api.events.listTemplateAttachments.mockResolvedValue([
            file(1, 'laufzettel.pdf', 'Laufzettel', false),
            file(5, 'schon-da.pdf'),
            file(2, 'plan.pdf', null, true),
        ])
        api.events.getTemplateFieldDefaults.mockResolvedValue([])
        api.events.getTemplateFederationShare.mockResolvedValue({shared: false, scope: null, partnerIds: null})
        api.federation.listPartners.mockResolvedValue([])
        api.appointmentDocuments.offeredTemplates.mockResolvedValue({total: 0, items: []})
        api.appointmentDocuments.listRequirements.mockResolvedValue([])
    })

    it('attaches the template files in order, with label and flag, leaving files already there', async () => {
        const wrapper = await applyTemplateInEditor()

        expect(api.events.attachEventFile.mock.calls).toEqual([
            [12, 41, 'Laufzettel', false],
            [12, 42, null, true],
        ])
        expect(wrapper.find('[data-testid="template-files-note"]').exists()).toBe(false)
    })
})
