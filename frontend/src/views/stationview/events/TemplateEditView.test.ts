/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import TemplateEditView from './TemplateEditView.vue'
import TemplateEditBody from './templateeditview/TemplateEditBody.vue'
import {
    EventType,
    QueueOrder,
    RestrictionMode,
    ShareScope,
    type EventTemplate,
    type RestrictionAudience,
    type TemplateDetailResponse,
} from '@/api/generated/schema'
import {createSessionInfo} from '@/test/mocks/factories'
import {noDeadline} from './eventshared/registrationDeadline'

const api = vi.hoisted(() => ({
    events: {
        getTemplate: vi.fn(),
        listCategories: vi.fn(),
        updateTemplate: vi.fn(),
        setTemplateReminders: vi.fn(),
        setTemplateRestrictions: vi.fn(),
        setTemplateFields: vi.fn(),
        setTemplateRegistrationFields: vi.fn(),
        getTemplateFieldDefaults: vi.fn(),
        setTemplateFieldDefaults: vi.fn(),
        getTemplateFederationShare: vi.fn(),
        setTemplateFederationShare: vi.fn(),
        removeTemplateFederationShare: vi.fn(),
        listTemplateAttachments: vi.fn(),
    },
    attendance: {listTemplates: vi.fn(), listTemplateFields: vi.fn()},
    memberGroups: {listGroups: vi.fn()},
    userTags: {listChoosableTags: vi.fn()},
    federation: {listPartners: vi.fn()},
    appointmentDocuments: {offeredTemplates: vi.fn(), listRequirements: vi.fn(), setRequirements: vi.fn()},
}))
vi.mock('@/api', () => api)

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({
        loaded: ref(true),
        sessionInfo: ref(createSessionInfo()),
        hasPermission: () => true,
    }),
}))
vi.mock('vue-router', () => ({
    useRoute: () => ({params: {id: '31'}}),
    useRouter: () => ({push: vi.fn()}),
}))

const dayBeforeAtMidnight = {kind: 'RELATIVE' as const, at: null, offsetMinutes: 1440, anchor: 'MIDNIGHT' as const}

function template(overrides: Partial<EventTemplate> = {}): EventTemplate {
    return {
        id: 31,
        stationId: '00000000-0000-0000-0000-000000000001',
        name: 'Übungsabend',
        title: null,
        description: null,
        categoryId: null,
        eventType: EventType.RECURRING,
        requiresRegistration: true,
        requiresConfirmation: null,
        registrationLimit: 20,
        queueOrder: null,
        registrationDeadline: dayBeforeAtMidnight,
        minRegistrations: 5,
        thresholdDays: 2,
        attendanceTemplateId: null,
        restrictionMode: null,
        viewRestrictionMode: RestrictionMode.OR,
        ...overrides,
    }
}

const NOBODY: RestrictionAudience = {groupIds: [], memberIds: [], mode: RestrictionMode.OR, tagIds: [], userTypes: []}

function detail(tpl: EventTemplate): TemplateDetailResponse {
    return {
        template: tpl,
        fields: [],
        registrationFields: [],
        reminderDays: [],
        restriction: {register: NOBODY, view: NOBODY},
    }
}

async function editor() {
    const wrapper = mount(TemplateEditView, {
        global: {
            stubs: {
                ViewContent: {template: '<div><slot/></div>'},
                TemplateEditBody: true,
                AttachmentsCard: true,
            },
        },
    })
    await flushPromises()
    return wrapper.findComponent(TemplateEditBody)
}

/**
 * The server writes every column of a template on each save, so a rule the editor does not send is
 * a rule the save clears. What is checked is that the four sign-up rules travel with every save,
 * as loaded and as changed.
 */
describe('TemplateEditView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.events.getTemplate.mockResolvedValue(detail(template()))
        api.events.listCategories.mockResolvedValue([])
        api.events.updateTemplate.mockResolvedValue(template())
        api.events.getTemplateFieldDefaults.mockResolvedValue([])
        api.events.setTemplateFieldDefaults.mockResolvedValue([])
        api.events.getTemplateFederationShare.mockResolvedValue({shared: false, scope: null, partnerIds: null})
        api.events.listTemplateAttachments.mockResolvedValue([])
        api.attendance.listTemplates.mockResolvedValue([])
        api.memberGroups.listGroups.mockResolvedValue([])
        api.userTags.listChoosableTags.mockResolvedValue([])
        api.federation.listPartners.mockResolvedValue([])
        api.appointmentDocuments.offeredTemplates.mockResolvedValue({total: 0, items: []})
        api.appointmentDocuments.listRequirements.mockResolvedValue([])
        api.appointmentDocuments.setRequirements.mockResolvedValue([])
    })

    /** Saving writes every column, so a form that could not be filled must not offer to save. */
    it('shows no form where the template could not be read', async () => {
        api.events.getTemplate.mockRejectedValue({response: {status: 500, data: {}}})

        expect((await editor()).exists()).toBe(false)
        expect(api.events.updateTemplate).not.toHaveBeenCalled()
    })

    it('sends the deadline and the rules it loaded', async () => {
        const body = await editor()

        expect(body.props('registrationDeadline')).toEqual(dayBeforeAtMidnight)
        await body.props('save')()

        expect(api.events.updateTemplate).toHaveBeenCalledWith(31, expect.objectContaining({
            registrationDeadline: dayBeforeAtMidnight,
            minRegistrations: 5,
            thresholdDays: 2,
        }))
    })

    it('sends the rules as they were changed', async () => {
        const body = await editor()

        const threeHoursBefore = {kind: 'RELATIVE', at: null, offsetMinutes: 180, anchor: 'EXACT'}
        body.vm.$emit('update:registrationDeadline', threeHoursBefore)
        body.vm.$emit('update:minRegistrations', 8)
        body.vm.$emit('update:thresholdDays', 4)
        await flushPromises()
        await body.props('save')()

        expect(api.events.updateTemplate).toHaveBeenCalledWith(31, expect.objectContaining({
            registrationDeadline: threeHoursBefore,
            minRegistrations: 8,
            thresholdDays: 4,
        }))
    })

    /** The waiting list order is a setting of the template, handed to every appointment made from it. */
    it('loads the waiting list order and sends it as changed', async () => {
        api.events.getTemplate.mockResolvedValue(detail(template({queueOrder: QueueOrder.FAIRNESS})))
        const body = await editor()

        expect(body.props('queueOrder')).toBe(QueueOrder.FAIRNESS)
        body.vm.$emit('update:queueOrder', QueueOrder.TIME)
        await flushPromises()
        await body.props('save')()

        expect(api.events.updateTemplate).toHaveBeenCalledWith(31, expect.objectContaining({queueOrder: QueueOrder.TIME}))
    })

    it('sends no deadline and no threshold where none is set', async () => {
        api.events.getTemplate.mockResolvedValue(detail(template({
            registrationDeadline: noDeadline(), minRegistrations: null,
        })))
        const body = await editor()

        await body.props('save')()

        expect(api.events.updateTemplate).toHaveBeenCalledWith(31, expect.objectContaining({
            registrationDeadline: {kind: 'NONE', at: null, offsetMinutes: null, anchor: null},
            minRegistrations: null,
            thresholdDays: null,
        }))
    })

    it('writes the field defaults and the share of the template with the save', async () => {
        api.events.getTemplateFederationShare.mockResolvedValue({shared: true, scope: ShareScope.ALL_PARTNERS, partnerIds: []})
        const body = await editor()

        await body.props('save')()

        expect(api.events.setTemplateFieldDefaults).toHaveBeenCalledWith(31, [])
        expect(api.events.setTemplateFederationShare).toHaveBeenCalledWith(31, ShareScope.ALL_PARTNERS, undefined)
    })
})
