/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {reportCaughtError} from '@/util/devErrorReporter'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {AttendanceTemplateField, EventFieldEntry, EventTemplate, StationMember} from '@/api/types'
import {EventTypes, StationPermission, needsDayOfWeek} from '@/api/types'
import type {EventFieldDefault} from '@/api/events'
import {attendance, events, federation, memberGroups} from '@/api'
import {type RestrictionSelection, emptyRestriction} from '@/components/input/restriction'
import type {PartnerResponse} from '@/api/federation'
import EventEditBody from './eventeditview/EventEditBody.vue'
import {useSession} from '@/composables/useSession'
import {useEventEditDeps} from '@/composables/useEventEditDeps'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const canFederate = computed(() => hasPermission(StationPermission.EVENTS_FEDERATE))
const eventId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => eventId.value !== null)

const {categories, templates, groups, tags, members: allMembers, reload: reloadDeps} = useEventEditDeps({withMembers: true, autoLoad: false})
const eventTemplates = ref<EventTemplate[]>([])
const allTemplateFields = ref<AttendanceTemplateField[]>([])
const groupMembersMap = ref(new Map<number, StationMember[]>())
const eventFieldDefaults = ref<EventFieldDefault[]>([])
const eventCustomFields = ref<EventFieldEntry[]>([])

const loading = ref(true)
const error = ref('')
const {message: templateAppliedMessage, flash: flashTemplateApplied} = useFlashMessage(3000)

async function applyEventTemplate(templateId: string | undefined) {
  if (!templateId) return
  try {
    const detail = await events.getTemplate(Number(templateId))
    const tpl = detail.template
    if (tpl.title) eventName.value = tpl.title
    if (tpl.description) eventDescription.value = tpl.description
    if (tpl.categoryId) eventCategoryId.value = String(tpl.categoryId)
    if (tpl.eventType) eventType.value = tpl.eventType
    if (tpl.requiresRegistration != null) eventRequiresRegistration.value = tpl.requiresRegistration
    if (tpl.requiresConfirmation != null) eventRequiresConfirmation.value = tpl.requiresConfirmation
    if (detail.fields.length > 0) {
      const newFields: EventFieldEntry[] = detail.fields.map(f => ({
        name: f.name,
        fieldType: f.fieldType ?? 'STRING',
        config: typeof f.config === 'string' ? (f.config ? JSON.parse(f.config) : {}) : (f.config ?? {}),
        value: '',
        overview: f.overview ?? false,
        attendanceFieldId: f.attendanceFieldId ?? null,
        isPublic: f.isPublic ?? false,
      }))
      eventCustomFields.value = [...eventCustomFields.value, ...newFields]
    }
    if (detail.reminderDays?.length) {
      eventReminders.value = [...new Set([...eventReminders.value, ...detail.reminderDays])]
    }
    flashTemplateApplied(t('eventTemplates.applied'))
  } catch (e) { reportCaughtError(e, 'applyEventTemplate'); error.value = t('common.error') }
}

const eventName = ref('')
const eventDescription = ref('')
const eventType = ref<string>(EventTypes.ONE_TIME)
const eventDayOfWeek = ref('1')
const eventStartTime = ref('')
const eventEndTime = ref('')

watch(eventStartTime, (val) => {
  if (val && !eventEndTime.value) {
    eventEndTime.value = val
  }
})
const eventTemplateId = ref('')
const eventCategoryId = ref('')
const eventRequiresRegistration = ref(false)
const eventHasDeadline = ref(false)
const eventRegistrationDeadline = ref('')
const eventRequiresConfirmation = ref(false)
const eventRegistrationLimit = ref<number | undefined>(undefined)
const eventMinRegistrations = ref<number | undefined>(undefined)
const eventHasThreshold = ref(false)
const eventThresholdDate = ref('')

const eventRegistrationCloseDays = ref<number | undefined>(undefined)

const eventReminders = ref<number[]>([])

const restriction = ref<RestrictionSelection>(emptyRestriction())
const federationShared = ref(false)
const federationScope = ref('ALL_PARTNERS')
const federationPartnerIds = ref<number[]>([])
const allPartners = ref<PartnerResponse[]>([])
const fieldDefaults = ref<Map<number, { source: string; value: string }>>(new Map())

async function loadExistingEvent(id: number) {
  const [ev, restrictions, defaults, fields] = await Promise.all([
    events.getEvent(id),
    events.getRestrictions(id),
    events.getFieldDefaults(id),
    events.getEventFields(id),
  ])

  eventCustomFields.value = fields.map(f => ({
    name: f.name ?? '',
    fieldType: f.fieldType ?? 'STRING',
    config: f.config ?? {},
    value: f.value ?? '',
    overview: f.overview ?? false,
    attendanceFieldId: f.attendanceFieldId ?? null,
  }))

  eventName.value = ev.name ?? ''
  eventDescription.value = ev.description ?? ''
  eventType.value = ev.eventType ?? EventTypes.RECURRING
  eventDayOfWeek.value = ev.dayOfWeek != null ? String(ev.dayOfWeek) : '1'
  eventStartTime.value = ev.startTime ? toLocalDateTime(ev.startTime) : ''
  eventEndTime.value = ev.endTime ? toLocalDateTime(ev.endTime) : ''
  eventTemplateId.value = ev.templateId != null ? String(ev.templateId) : ''
  eventCategoryId.value = ev.categoryId != null ? String(ev.categoryId) : ''
  eventRequiresRegistration.value = ev.requiresRegistration ?? false
  eventHasDeadline.value = !!ev.registrationDeadline
  eventRegistrationDeadline.value = ev.registrationDeadline ? toLocalDateTime(ev.registrationDeadline) : ''
  eventRequiresConfirmation.value = ev.requiresConfirmation ?? false
  eventRegistrationLimit.value = ev.registrationLimit ?? undefined
  eventMinRegistrations.value = ev.minRegistrations ?? undefined
  eventHasThreshold.value = !!ev.thresholdDate
  eventThresholdDate.value = ev.thresholdDate ? toLocalDateTime(ev.thresholdDate) : ''
  eventRegistrationCloseDays.value = ev.registrationCloseDays ?? undefined

  restriction.value = {
    userTypes: restrictions.userTypes ?? [],
    groupIds: restrictions.groupIds ?? [],
    tagIds: restrictions.tagIds ?? [],
    memberIds: [],
    mode: (restrictions.mode as 'AND' | 'OR') ?? 'AND',
  }

  try {
    eventReminders.value = await events.getEventReminders(id)
  } catch { eventReminders.value = [] }

  const fdMap = new Map<number, { source: string; value: string }>()
  for (const fd of defaults) {
    fdMap.set(fd.fieldId, {source: fd.source, value: fd.value ?? ''})
  }
  fieldDefaults.value = fdMap
  eventFieldDefaults.value = defaults

  await loadFederationShare(id)
}

async function loadFederationShare(id: number) {
  try {
    allPartners.value = await federation.listPartners()
  } catch {
    allPartners.value = []
  }
  if (!canFederate.value) return
  try {
    const fedShare = await events.getFederationShare(id)
    federationShared.value = fedShare.shared
    if (fedShare.shared) {
      federationScope.value = fedShare.scope ?? 'ALL_PARTNERS'
      federationPartnerIds.value = fedShare.partnerIds ?? []
    }
  } catch {
    federationShared.value = false
  }
}

function toLocalDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [, evtTpls] = await Promise.all([
      reloadDeps(),
      events.listTemplates(),
    ])
    eventTemplates.value = evtTpls

    const gMap = new Map<number, StationMember[]>()
    for (const g of groups.value) {
      const gMembers = await memberGroups.getGroupMembers(g.id)
      gMap.set(g.id, gMembers)
    }
    groupMembersMap.value = gMap

    const fieldResults = await Promise.all(templates.value.map(t => attendance.listTemplateFields(t.id)))
    allTemplateFields.value = fieldResults.flat()

    if (isEdit.value) {
      await loadExistingEvent(eventId.value!)
    }
  } catch (e) {
    reportCaughtError(e, 'EventEditView.loadData')
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const currentTemplateFields = computed(() => {
  if (!eventTemplateId.value) return []
  return allTemplateFields.value.filter(f => f.templateId === Number(eventTemplateId.value))
})

function getFieldDefault(fieldId: number): { source: string; value: string } {
  return fieldDefaults.value.get(fieldId) ?? {source: '', value: ''}
}

function setFieldDefaultSource(fieldId: number, source: string) {
  const existing = getFieldDefault(fieldId)
  const m = new Map(fieldDefaults.value)
  if (!source) {
    m.delete(fieldId)
  } else {
    m.set(fieldId, {source, value: source === 'VALUE' ? existing.value : ''})
  }
  fieldDefaults.value = m
}

function setFieldDefaultValue(fieldId: number, value: string) {
  const existing = getFieldDefault(fieldId)
  const m = new Map(fieldDefaults.value)
  m.set(fieldId, {source: existing.source, value})
  fieldDefaults.value = m
}


const {running: saving, error: saveError, run: submit} = useAsyncAction(async () => {
  error.value = ''
  const fieldDefaultEntries = [...fieldDefaults.value.entries()]
      .filter(([, v]) => v.source)
      .map(([fieldId, v]) => ({fieldId, source: v.source, value: v.value || undefined}))

  const data = {
    name: eventName.value,
    description: eventDescription.value || undefined,
    eventType: eventType.value,
    dayOfWeek: needsDayOfWeek(eventType.value) ? Number(eventDayOfWeek.value) : null,
    startTime: eventStartTime.value ? new Date(eventStartTime.value).toISOString() : undefined,
    endTime: eventEndTime.value ? new Date(eventEndTime.value).toISOString() : undefined,
    templateId: eventTemplateId.value ? Number(eventTemplateId.value) : undefined,
    categoryId: eventCategoryId.value ? Number(eventCategoryId.value) : undefined,
    requiresRegistration: eventRequiresRegistration.value,
    registrationDeadline: eventHasDeadline.value && eventRegistrationDeadline.value
        ? new Date(eventRegistrationDeadline.value).toISOString() : undefined,
    requiresConfirmation: eventRequiresConfirmation.value,
    registrationLimit: eventRegistrationLimit.value ?? undefined,
    minRegistrations: eventMinRegistrations.value ?? undefined,
    thresholdDate: eventHasThreshold.value && eventThresholdDate.value
        ? new Date(eventThresholdDate.value).toISOString() : undefined,
    restriction: restriction.value,
    registrationCloseDays: eventRegistrationCloseDays.value ?? undefined,
  }

  let savedEventId: number
  if (isEdit.value) {
    await events.updateEvent(eventId.value!, data)
    savedEventId = eventId.value!
  } else {
    const created = await events.createEvent(data)
    savedEventId = created.id
  }

  if (fieldDefaultEntries.length > 0 || isEdit.value) {
    await events.setFieldDefaults(savedEventId, fieldDefaultEntries)
  }

  await events.setEventReminders(savedEventId, eventReminders.value)

  const customFields = eventCustomFields.value.filter(f => f.name.trim())
  await events.setEventFields(savedEventId, {fields: customFields})

  if (canFederate.value) {
    if (federationShared.value) {
      const pIds = federationScope.value === 'SPECIFIC_PARTNERS' ? federationPartnerIds.value : undefined
      await events.setFederationShare(savedEventId, federationScope.value, pIds)
    } else {
      await events.removeFederationShare(savedEventId).catch(() => {})
    }
  }

  leaveEditor()
}, {formatError: (e) => {
  reportCaughtError(e, 'EventEditView.submit')
  return t('common.error')
}})

function leaveEditor() {
  const returnTo = typeof route.query.returnTo === 'string' ? route.query.returnTo : null
  if (returnTo && returnTo.startsWith('/')) {
    router.push(returnTo)
  } else {
    router.push({name: 'events'})
  }
}

function goBack() {
  leaveEditor()
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})

const bodyProps = computed(() => ({
  isEdit: isEdit.value,
  saving: saving.value,
  canFederate: canFederate.value,
  eventTemplates: eventTemplates.value,
  categories: categories.value,
  templates: templates.value,
  attendanceFields: allTemplateFields.value,
  groups: groups.value,
  tags: tags.value,
  allMembers: allMembers.value,
  groupMembers: groupMembersMap.value,
  currentTemplateFields: currentTemplateFields.value,
  fieldDefaults: fieldDefaults.value,
  partners: allPartners.value,
  name: eventName.value,
  description: eventDescription.value,
  categoryId: eventCategoryId.value,
  templateId: eventTemplateId.value,
  eventType: eventType.value,
  dayOfWeek: eventDayOfWeek.value,
  startTime: eventStartTime.value,
  endTime: eventEndTime.value,
  requiresRegistration: eventRequiresRegistration.value,
  requiresConfirmation: eventRequiresConfirmation.value,
  hasDeadline: eventHasDeadline.value,
  registrationDeadline: eventRegistrationDeadline.value,
  registrationLimit: eventRegistrationLimit.value,
  minRegistrations: eventMinRegistrations.value,
  hasThreshold: eventHasThreshold.value,
  thresholdDate: eventThresholdDate.value,
  registrationCloseDays: eventRegistrationCloseDays.value,
  restriction: restriction.value,
  fields: eventCustomFields.value,
  reminders: eventReminders.value,
  federationShared: federationShared.value,
  federationScope: federationScope.value,
  federationPartnerIds: federationPartnerIds.value,
}))

const bodyHandlers = {
  'apply-template': applyEventTemplate,
  'update:name': (v: string) => { eventName.value = v },
  'update:description': (v: string) => { eventDescription.value = v },
  'update:categoryId': (v: string) => { eventCategoryId.value = v },
  'update:templateId': (v: string) => { eventTemplateId.value = v },
  'update:eventType': (v: string) => { eventType.value = v },
  'update:dayOfWeek': (v: string) => { eventDayOfWeek.value = v },
  'update:startTime': (v: string) => { eventStartTime.value = v },
  'update:endTime': (v: string) => { eventEndTime.value = v },
  'update:requiresRegistration': (v: boolean) => { eventRequiresRegistration.value = v },
  'update:requiresConfirmation': (v: boolean) => { eventRequiresConfirmation.value = v },
  'update:hasDeadline': (v: boolean) => { eventHasDeadline.value = v },
  'update:registrationDeadline': (v: string) => { eventRegistrationDeadline.value = v },
  'update:registrationLimit': (v: number | undefined) => { eventRegistrationLimit.value = v },
  'update:minRegistrations': (v: number | undefined) => { eventMinRegistrations.value = v },
  'update:hasThreshold': (v: boolean) => { eventHasThreshold.value = v },
  'update:thresholdDate': (v: string) => { eventThresholdDate.value = v },
  'update:registrationCloseDays': (v: number | undefined) => { eventRegistrationCloseDays.value = v },
  'update:restriction': (v: RestrictionSelection) => { restriction.value = v },
  'update:fields': (v: EventFieldEntry[]) => { eventCustomFields.value = v },
  'update:reminders': (v: number[]) => { eventReminders.value = v },
  'update:federationShared': (v: boolean) => { federationShared.value = v },
  'update:federationScope': (v: string) => { federationScope.value = v },
  'update:federationPartnerIds': (v: number[]) => { federationPartnerIds.value = v },
  'update:fieldDefaultSource': setFieldDefaultSource,
  'update:fieldDefaultValue': setFieldDefaultValue,
  cancel: goBack,
  submit,
}
</script>

<template>
  <ViewContent
      :title="t('pages.event-new.title')"
      :subtitle="t('pages.event-new.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || saveError" variant="error">{{ error || saveError }}</Alert>
      <Alert v-if="templateAppliedMessage" variant="success">{{ templateAppliedMessage }}</Alert>

      <EventEditBody
          v-if="!loading"
          v-bind="bodyProps"
          v-on="bodyHandlers"
      />
    </div>
  </ViewContent>
</template>
