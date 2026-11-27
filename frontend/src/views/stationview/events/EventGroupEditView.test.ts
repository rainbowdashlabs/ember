/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import EventGroupEditView from './EventGroupEditView.vue'
import {EventType, RestrictionMode, type RestrictionAudience} from '@/api/generated/schema'

const YOUTH: RestrictionAudience = {groupIds: [4], memberIds: [], mode: RestrictionMode.OR, tagIds: [], userTypes: []}

const api = vi.hoisted(() => ({
  eventGroups: {
    createEventGroup: vi.fn(),
    updateEventGroup: vi.fn(),
    getEventGroup: vi.fn(),
    getFederationShare: vi.fn(async () => null),
    setFederationShare: vi.fn(),
    removeFederationShare: vi.fn(),
    setSettings: vi.fn(),
    getSettings: vi.fn(async () => ({templateId: null, categoryId: null, reminderDays: [], fieldDefaults: []})),
    attachFile: vi.fn(),
  },
  events: {
    getEvent: vi.fn(), getRestrictions: vi.fn(), listCategories: vi.fn(async () => []),
    listTemplates: vi.fn(async (): Promise<unknown[]> => []), getTemplate: vi.fn(),
    listTemplateAttachments: vi.fn(async (): Promise<unknown[]> => []), getTemplateFieldDefaults: vi.fn(async () => []),
  },
  attendance: {listTemplates: vi.fn(async () => []), listTemplateFields: vi.fn(async () => [])},
  memberGroups: {listGroups: vi.fn(async () => []), getGroupMembers: vi.fn(async () => [])},
  stationMembers: {listMembers: vi.fn(async () => [])},
  appointmentDocuments: {
    offeredTemplates: vi.fn(async () => ({total: 0, items: []})),
    listRequirements: vi.fn(async () => []),
    setRequirements: vi.fn(async () => []),
  },
  userTags: {listChoosableTags: vi.fn(async () => [])},
  federation: {listPartners: vi.fn(async () => [])},
}))
vi.mock('@/api', () => api)
const permitted = vi.hoisted(() => ({federate: false}))
vi.mock('@/composables/useSession', () => ({
  useSession: () => ({loaded: ref(true), stationTimezone: ref('Europe/Berlin'), hasPermission: () => permitted.federate}),
}))
const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>}))
const replace = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({useRoute: () => route, useRouter: () => ({replace})}))
const toast = vi.hoisted(() => vi.fn())
vi.mock('@/util/toast', () => ({showToast: toast}))
const attachPendingTo = vi.hoisted(() => vi.fn(async (_eventGroupId: number, _carried?: ReadonlySet<number>) => [] as string[]))

/** The files section as the editor reaches it: only the pending files it hangs on a created group. */
const FilesSectionStub = {
  template: '<div data-testid="event-group-files-section-stub"/>',
  setup(_props: unknown, {expose}: {expose: (exposed: Record<string, unknown>) => void}) {
    expose({attachPendingTo})
  },
}

function mountEditor() {
  return mount(EventGroupEditView, {
    global: {stubs: {ViewContent: {template: '<div><slot/></div>'}, MarkdownEditor: true, EventGroupSignUpFields: true, EventGroupColorField: true, RestrictionsField: true, EventGroupSetsEditor: true,
      EventGroupQuestionsCard: true, EventGroupFieldsCard: true, EventGroupShareCard: true, EventGroupPartsEditor: true, EventGroupDateSettingsSection: true, EventGroupFilesSection: FilesSectionStub}},
  })
}

/**
 * A new event group started from an appointment takes its name and audiences and sends the
 * appointment along with the creation, so the server takes it in or creates nothing. Anything but a
 * one-time appointment is ignored.
 */
describe('EventGroupEditView started from an appointment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {appointment: '12'}
    api.events.getEvent.mockResolvedValue({id: 12, name: 'Grundlehrgang 1', eventType: EventType.ONE_TIME})
    api.events.getRestrictions.mockResolvedValue({register: YOUTH, view: YOUTH})
    api.eventGroups.createEventGroup.mockResolvedValue({eventGroup: {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null}}, sets: [], parts: [], questions: [], fields: [],
      registration: YOUTH, view: YOUTH})
    api.eventGroups.setSettings.mockResolvedValue({templateId: null, categoryId: null, reminderDays: [], fieldDefaults: []})
  })

  it('saves the date settings for the group it created', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.setSettings).toHaveBeenCalledWith(7, {templateId: null, categoryId: null, reminderDays: [], fieldDefaults: []})
    expect(api.appointmentDocuments.setRequirements).toHaveBeenCalledWith({kind: 'events/groups', id: 7}, [])
  })

  it('names the appointment, takes over its audiences and creates the group with it', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.text()).toContain('Grundlehrgang 1')
    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledWith(expect.objectContaining({
      appointmentId: 12,
      registration: expect.objectContaining({groupIds: [4], mode: RestrictionMode.OR}),
      view: expect.objectContaining({groupIds: [4]}),
    }))
  })

  it('creates the group without the appointment once it is dropped', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('[role="status"] button').trigger('click')
    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledWith(expect.objectContaining({appointmentId: null}))
  })

  it('changes the group it already created when saving again after a later part of the save was refused', async () => {
    api.eventGroups.createEventGroup.mockResolvedValue({eventGroup: {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null}}, sets: [{id: 41, name: 'Termine', minimum: 1}],
      parts: [{eventId: 12, setId: 41}], questions: [], fields: [], registration: YOUTH, view: YOUTH})
    api.eventGroups.setSettings.mockRejectedValueOnce(new Error('refused'))
    api.eventGroups.updateEventGroup.mockResolvedValue({eventGroup: {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null}}, sets: [], parts: [], questions: [], fields: [],
      registration: YOUTH, view: YOUTH})
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledOnce()
    expect(api.eventGroups.updateEventGroup).toHaveBeenCalledWith(7, expect.anything(), [{id: 41, name: 'Termine', minimum: 1}],
      expect.anything(), expect.anything(), expect.anything())
  })

  it('ignores an appointment that is not a one-time one', async () => {
    api.events.getEvent.mockResolvedValue({id: 12, name: 'Dienstabend', eventType: EventType.RECURRING})
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })
})

/** A minimum the server lowered after a part left its set reaches the form, which shows it. */
describe('EventGroupEditView after a part changed', () => {
  const group = {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null,
    requiresConfirmation: false, color: null}}

  function detailWith(minimum: number, parts: number, maximum: number | null = null) {
    return {eventGroup: group, sets: [{id: 1, name: 'Übung', minimum, maximum}], questions: [], fields: [],
      registration: YOUTH, view: YOUTH,
      parts: Array.from({length: parts}, (_, index) => ({eventId: index + 1, setId: 1}))}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    route.params = {eventGroupId: '7'}
    route.query = {}
  })

  it('takes the lowered minimum into the form and says so', async () => {
    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(3, 3))
    const wrapper = mountEditor()
    await flushPromises()

    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(2, 2))
    await (wrapper.findComponent({name: 'EventGroupPartsEditor'}).props('onChanged') as () => Promise<void>)()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupSetsEditor'}).props('modelValue')).toEqual([{id: 1, name: 'Übung', minimum: 2, maximum: null}])
    expect(toast).toHaveBeenCalledOnce()
  })

  it('leaves a set alone the server did not lower', async () => {
    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(3, 2))
    const wrapper = mountEditor()
    await flushPromises()

    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(3, 1))
    await (wrapper.findComponent({name: 'EventGroupPartsEditor'}).props('onChanged') as () => Promise<void>)()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupSetsEditor'}).props('modelValue')).toEqual([{id: 1, name: 'Übung', minimum: 3, maximum: null}])
    expect(toast).not.toHaveBeenCalled()
  })

  it('brings an unsaved minimum above the parts now held down to them', async () => {
    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(2, 3))
    const wrapper = mountEditor()
    await flushPromises()
    await wrapper.findComponent({name: 'EventGroupSetsEditor'}).vm.$emit('update:modelValue', [{id: 1, name: 'Übung', minimum: 3, maximum: null}])

    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(2, 2))
    await (wrapper.findComponent({name: 'EventGroupPartsEditor'}).props('onChanged') as () => Promise<void>)()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupSetsEditor'}).props('modelValue')).toEqual([{id: 1, name: 'Übung', minimum: 2, maximum: null}])
    expect(toast).toHaveBeenCalledOnce()
  })

  /** A minimum the server raised because a part became required reaches the form, which shows it. */
  it('takes a minimum raised by a required part into the form and says so', async () => {
    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(1, 3))
    const wrapper = mountEditor()
    await flushPromises()

    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(2, 3))
    await (wrapper.findComponent({name: 'EventGroupPartsEditor'}).props('onChanged') as () => Promise<void>)()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupSetsEditor'}).props('modelValue')).toEqual([{id: 1, name: 'Übung', minimum: 2, maximum: null}])
    expect(toast).toHaveBeenCalledOnce()
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('2 statt 1'))
  })

  /** A maximum the server raised because a part became required reaches the form, which shows it. */
  it('takes a raised maximum into the form and says so', async () => {
    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(1, 3, 1))
    const wrapper = mountEditor()
    await flushPromises()

    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(2, 3, 2))
    await (wrapper.findComponent({name: 'EventGroupPartsEditor'}).props('onChanged') as () => Promise<void>)()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupSetsEditor'}).props('modelValue'))
        .toEqual([{id: 1, name: 'Übung', minimum: 2, maximum: 2}])
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('höchstens 2 statt 1'))
  })

  it('leaves a maximum the form already holds high enough alone', async () => {
    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(1, 3, 1))
    const wrapper = mountEditor()
    await flushPromises()
    await wrapper.findComponent({name: 'EventGroupSetsEditor'}).vm.$emit('update:modelValue', [{id: 1, name: 'Übung', minimum: 1, maximum: 3}])

    api.eventGroups.getEventGroup.mockResolvedValueOnce(detailWith(1, 3, 2))
    await (wrapper.findComponent({name: 'EventGroupPartsEditor'}).props('onChanged') as () => Promise<void>)()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupSetsEditor'}).props('modelValue'))
        .toEqual([{id: 1, name: 'Übung', minimum: 1, maximum: 3}])
    expect(toast).not.toHaveBeenCalled()
  })
})

/**
 * A new event group made from an appointment template takes what an event group can carry from it,
 * names the fields it cannot take and attaches the template's files once it exists.
 */
describe('EventGroupEditView from an appointment template', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    route.params = {}
    api.events.listTemplates.mockResolvedValue([{id: 5, name: 'Jugendflamme'}])
    api.events.getTemplate.mockResolvedValue({
      template: {id: 5, description: 'Abzeichen', categoryId: 2, attendanceTemplateId: 4},
      fields: [
        {name: 'Ort', fieldType: 'TEXT', config: {perDate: false}, defaultValue: 'Gerätehaus', overview: false,
          attendanceFieldId: null, isPublic: true},
        {name: 'Fahrer', fieldType: 'MEMBER', config: {perDate: true}, defaultValue: null, overview: false,
          attendanceFieldId: null, isPublic: false},
      ],
      registrationFields: [{name: 'Größe', fieldType: 'TEXT', config: {}, overview: true}],
      reminderDays: [3],
      restriction: {register: YOUTH, view: YOUTH},
    })
    api.events.listTemplateAttachments.mockResolvedValue([{id: 9, fileId: 40, label: 'Anfahrt', internal: false, fileName: 'a.pdf'}])
    api.eventGroups.createEventGroup.mockResolvedValue({eventGroup: {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null}}, sets: [], parts: [], questions: [], fields: [],
      registration: YOUTH, view: YOUTH})
    api.eventGroups.setSettings.mockResolvedValue({templateId: 4, categoryId: 2, reminderDays: [3], fieldDefaults: []})
  })

  it('fills the group, names what it left out and attaches the files after saving', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('[data-testid="event-group-apply-template"]').setValue('5')
    await flushPromises()
    expect(wrapper.find('[data-testid="event-group-template-left-out"]').text()).toContain('Fahrer')

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledWith(expect.objectContaining({
      details: expect.objectContaining({description: 'Abzeichen'}),
      fields: [expect.objectContaining({name: 'Ort', value: 'Gerätehaus'})],
      questions: [expect.objectContaining({name: 'Größe'})],
      registration: expect.objectContaining({groupIds: [4]}),
    }))
    expect(api.eventGroups.setSettings).toHaveBeenCalledWith(7, expect.objectContaining({templateId: 4, categoryId: 2, reminderDays: [3]}))
    expect(api.eventGroups.attachFile).toHaveBeenCalledWith(7, 40, 'Anfahrt', false)
  })

  /** Files added before the group existed go onto it once it is created, after the template's. */
  it('hangs the files added to the new group on it after the template\'s, leaving out the template\'s own', async () => {
    const wrapper = mountEditor()
    await flushPromises()
    await wrapper.find('[data-testid="event-group-apply-template"]').setValue('5')
    await flushPromises()

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(attachPendingTo).toHaveBeenCalledWith(7, new Set([40]))
    expect(api.eventGroups.attachFile.mock.invocationCallOrder[0]).toBeLessThan(attachPendingTo.mock.invocationCallOrder[0]!)
    expect(replace).toHaveBeenCalledWith({name: 'event-group-edit', params: {eventGroupId: 7}})
  })
})

/**
 * The event group is one form in the appointment form's order, saved at its end; its parts come below,
 * since they need a saved group.
 */
describe('EventGroupEditView layout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
  })

  it('asks in the appointment form\'s order and keeps the parts below the form', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    const form = wrapper.get('[data-testid="event-group-form"]')
    const html = form.html()
    const order = ['event-group-name', 'event-group-sign-up-fields-stub', 'event-group-sets-editor-stub',
      'restrict-registration', 'event-group-questions-card-stub', 'event-group-files-section-stub', 'event-group-save']
    const positions = order.map(marker => html.indexOf(marker))

    expect(positions.every(position => position >= 0)).toBe(true)
    expect([...positions].sort((a, b) => a - b)).toEqual(positions)
    expect(form.find('event-group-parts-editor-stub').exists()).toBe(false)
  })
})

/**
 * A new event group is shared in the same save that creates it, the way a new appointment is, and one
 * that is not shared sends nothing about sharing.
 */
describe('EventGroupEditView sharing a new event group', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    permitted.federate = true
    route.query = {}
    route.params = {}
    api.federation.listPartners.mockResolvedValue([])
    api.eventGroups.createEventGroup.mockResolvedValue({eventGroup: {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null}}, sets: [], parts: [], questions: [], fields: [],
      registration: YOUTH, view: YOUTH})
    api.eventGroups.setSettings.mockResolvedValue({templateId: null, categoryId: null, reminderDays: [], fieldDefaults: []})
  })

  afterEach(() => {
    permitted.federate = false
  })

  function shareOf(wrapper: ReturnType<typeof mountEditor>) {
    return wrapper.findComponent({name: 'EventGroupShareCard'}).props('federationShare') as {state: {federationShared: boolean; federationScope: string; federationPartnerIds: number[]}}
  }

  it('offers the share before the event group exists and reads the partners for it', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.findComponent({name: 'EventGroupShareCard'}).exists()).toBe(true)
    expect(api.federation.listPartners).toHaveBeenCalled()
    expect(api.eventGroups.getFederationShare).not.toHaveBeenCalled()
  })

  it('shares the event group it created in the same save', async () => {
    const wrapper = mountEditor()
    await flushPromises()
    const share = shareOf(wrapper)
    share.state.federationShared = true
    share.state.federationScope = 'SPECIFIC'
    share.state.federationPartnerIds = [3]

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.setFederationShare).toHaveBeenCalledWith(7, 'SPECIFIC', [3])
    expect(replace).toHaveBeenCalledWith({name: 'event-group-edit', params: {eventGroupId: 7}})
  })

  it('sends nothing about sharing for a new event group that is not shared', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.setFederationShare).not.toHaveBeenCalled()
    expect(api.eventGroups.removeFederationShare).not.toHaveBeenCalled()
  })

  it('stays on the new event group when its share is refused, and shares it on the next save', async () => {
    api.eventGroups.setFederationShare.mockRejectedValueOnce(new Error('refused'))
    api.eventGroups.updateEventGroup.mockResolvedValue({eventGroup: {id: 7, details: {name: 'Jugendflamme', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null}}, sets: [], parts: [], questions: [], fields: [],
      registration: YOUTH, view: YOUTH})
    const wrapper = mountEditor()
    await flushPromises()
    shareOf(wrapper).state.federationShared = true

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()
    expect(replace).not.toHaveBeenCalled()

    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledOnce()
    expect(api.eventGroups.setFederationShare).toHaveBeenCalledTimes(2)
    expect(replace).toHaveBeenCalledWith({name: 'event-group-edit', params: {eventGroupId: 7}})
  })
})
