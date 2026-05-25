/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory, EventFieldEntry, EventTemplate, MemberGroup, Role, StationMember, UserTag} from '@/api/types'
import {EventTypes, needsDayOfWeek} from '@/api/types'
import type {EventFieldDefault} from '@/api/events'
import {attendance, events, memberGroups, stationMembers, userTags} from '@/api'
import EventFormPanel from './eventshared/EventFormPanel.vue'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const eventId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => eventId.value !== null)

const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const eventTemplates = ref<EventTemplate[]>([])
const roles = ref<Role[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const allTemplateFields = ref<AttendanceTemplateField[]>([])
const allMembers = ref<StationMember[]>([])
const groupMembersMap = ref(new Map<number, StationMember[]>())
const eventRoleIds = ref<number[]>([])
const eventGroupIds = ref<number[]>([])
const eventTagIds = ref<number[]>([])
const eventFieldDefaults = ref<EventFieldDefault[]>([])
const eventCustomFields = ref<EventFieldEntry[]>([])

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const templateApplied = ref(false)

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
      const newFields = detail.fields.map(f => ({
        name: f.name,
        fieldType: f.fieldType ?? 'string',
        config: f.config ?? '{}',
        value: '',
        overview: f.overview ?? false,
        attendanceFieldId: f.attendanceFieldId ?? null,
        isPublic: f.isPublic ?? false,
      }))
      eventCustomFields.value = [...eventCustomFields.value, ...newFields]
    }
    if (detail.restrictionRoleIds.length > 0) {
      selectedRoleIds.value = [...new Set([...selectedRoleIds.value, ...detail.restrictionRoleIds])]
    }
    templateApplied.value = true
    setTimeout(() => { templateApplied.value = false }, 3000)
  } catch { error.value = t('common.error') }
}

// Form state
const eventName = ref('')
const eventDescription = ref('')
const eventType = ref<string>(EventTypes.ONE_TIME)
const eventDayOfWeek = ref('1')
const eventStartTime = ref('')
const eventEndTime = ref('')
const eventTemplateId = ref('')
const eventCategoryId = ref('')
const eventRequiresRegistration = ref(false)
const eventHasDeadline = ref(false)
const eventRegistrationDeadline = ref('')
const eventRequiresConfirmation = ref(false)
const eventRegistrationLimit = ref<number | undefined>(undefined)

const selectedRoleIds = ref<number[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const restrictionMode = ref<'AND' | 'OR'>('AND')
const fieldDefaults = ref<Map<number, { source: string; value: string }>>(new Map())

function toLocalDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [cats, tpl, allRoles, allGroups, allTags, members, evtTpls] = await Promise.all([
      events.listCategories(),
      attendance.listTemplates(),
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
      userTags.listTags(),
      stationMembers.listMembers(),
      events.listTemplates(),
    ])
    categories.value = cats
    templates.value = tpl
    eventTemplates.value = evtTpls
    roles.value = allRoles
    groups.value = allGroups
    tags.value = allTags
    allMembers.value = members

    // Build group members map
    const gMap = new Map<number, StationMember[]>()
    for (const g of allGroups) {
      const gMembers = await memberGroups.getGroupMembers(g.id)
      gMap.set(g.id, gMembers)
    }
    groupMembersMap.value = gMap

    const fieldResults = await Promise.all(tpl.map(t => attendance.listTemplateFields(t.id)))
    allTemplateFields.value = fieldResults.flat()

    if (isEdit.value) {
      const [ev, restrictions, defaults, fields] = await Promise.all([
        events.getEvent(eventId.value!),
        events.getRestrictions(eventId.value!),
        events.getFieldDefaults(eventId.value!),
        events.getEventFields(eventId.value!),
      ])

      eventCustomFields.value = fields.map(f => ({
        name: f.name ?? '',
        fieldType: f.fieldType ?? 'string',
        config: f.config ?? '{}',
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

      eventRoleIds.value = restrictions.roleIds ?? []
      eventGroupIds.value = restrictions.groupIds ?? []
      eventTagIds.value = restrictions.tagIds ?? []
      selectedRoleIds.value = [...eventRoleIds.value]
      selectedGroupIds.value = [...eventGroupIds.value]
      selectedTagIds.value = [...eventTagIds.value]
      restrictionMode.value = (restrictions.mode as 'AND' | 'OR') ?? 'AND'

      const fdMap = new Map<number, { source: string; value: string }>()
      for (const fd of defaults) {
        fdMap.set(fd.fieldId, {source: fd.source, value: fd.value ?? ''})
      }
      fieldDefaults.value = fdMap
      eventFieldDefaults.value = defaults
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const EVENT_SOURCES = [
  {value: 'EVENT_NAME', label: 'Terminname'},
  {value: 'EVENT_DESCRIPTION', label: 'Beschreibung'},
  {value: 'EVENT_START_TIME', label: 'Startzeit'},
  {value: 'EVENT_END_TIME', label: 'Endzeit'},
]

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


async function submit() {
  saving.value = true
  error.value = ''
  try {
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
      restrictedRoleIds: selectedRoleIds.value,
      restrictedGroupIds: selectedGroupIds.value,
      restrictedTagIds: selectedTagIds.value,
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

    const customFields = eventCustomFields.value.filter(f => f.name.trim())
    await events.setEventFields(savedEventId, {fields: customFields})

    router.push({name: 'events'})
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({name: 'events'})
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="templateApplied" variant="success">{{ t('eventTemplates.applied') }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <SectionHeader>{{ isEdit ? t('events.editEvent') : t('events.addEvent') }}</SectionHeader>
          <div v-if="eventTemplates.length > 0" class="flex items-center gap-2">
            <SelectInput model-value="" class="w-48 text-sm" @update:model-value="applyEventTemplate">
              <option value="" disabled>{{ t('eventTemplates.loadTemplate') }}</option>
              <option v-for="et in eventTemplates" :key="et.id" :value="String(et.id)">{{ et.name }}</option>
            </SelectInput>
          </div>
        </div>

        <NeutralContainer>
          <EventFormPanel
              v-model:name="eventName"
              v-model:description="eventDescription"
              v-model:category-id="eventCategoryId"
              v-model:template-id="eventTemplateId"
              v-model:event-type="eventType"
              v-model:day-of-week="eventDayOfWeek"
              v-model:start-time="eventStartTime"
              v-model:end-time="eventEndTime"
              v-model:requires-registration="eventRequiresRegistration"
              v-model:requires-confirmation="eventRequiresConfirmation"
              v-model:has-deadline="eventHasDeadline"
              v-model:registration-deadline="eventRegistrationDeadline"
              v-model:registration-limit="eventRegistrationLimit"
              v-model:selected-role-ids="selectedRoleIds"
              v-model:selected-group-ids="selectedGroupIds"
              v-model:selected-tag-ids="selectedTagIds"
              v-model:fields="eventCustomFields"
              :categories="categories"
              :templates="templates"
              :attendance-fields="allTemplateFields"
              :roles="roles"
              :groups="groups"
              :tags="tags"
              :all-members="allMembers"
              :group-members="groupMembersMap"
              show-schedule
              show-value
          />
        </NeutralContainer>

        <NeutralContainer v-if="currentTemplateFields.length > 0" class="space-y-4">
          <SubHeader>{{ t('events.fieldDefaults') }}</SubHeader>
          <p class="text-xs text-(--text-muted)">{{ t('events.fieldDefaultsHint') }}</p>
          <div class="space-y-2">
            <div v-for="field in currentTemplateFields" :key="field.id"
                 class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
              <div class="text-sm font-medium">{{ field.name }} <span
                  class="text-xs text-(--text-muted)">({{ field.fieldType }})</span></div>
              <div class="grid gap-2 sm:grid-cols-2">
                <SelectInput
                    :model-value="getFieldDefault(field.id).source"
                    @update:model-value="setFieldDefaultSource(field.id, $event ?? '')"
                >
                  <option value="">{{ t('events.noDefault') }}</option>
                  <option value="VALUE">{{ t('events.staticValue') }}</option>
                  <option v-for="src in EVENT_SOURCES" :key="src.value" :value="src.value">{{ src.label }}</option>
                </SelectInput>
                <TextInput
                    v-if="getFieldDefault(field.id).source === 'VALUE'"
                    :model-value="getFieldDefault(field.id).value"
                    :placeholder="t('events.defaultValuePlaceholder')"
                    @update:model-value="setFieldDefaultValue(field.id, $event ?? '')"
                />
              </div>
            </div>
          </div>
        </NeutralContainer>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="goBack">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || !eventName || !eventStartTime || !eventEndTime" @click="submit">
            {{ saving ? t('common.loading') : t('common.save') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
