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
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory, EventFieldEntry, MemberGroup, Role, UserTag} from '@/api/types'
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
const roles = ref<Role[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const allTemplateFields = ref<AttendanceTemplateField[]>([])
const eventRoleIds = ref<number[]>([])
const eventGroupIds = ref<number[]>([])
const eventTagIds = ref<number[]>([])
const eventFieldDefaults = ref<EventFieldDefault[]>([])
const eventCustomFields = ref<EventFieldEntry[]>([])

const loading = ref(true)
const saving = ref(false)
const error = ref('')

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
    const [cats, tpl, allRoles, allGroups, allTags] = await Promise.all([
      events.listCategories(),
      attendance.listTemplates(),
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
      userTags.listTags(),
    ])
    categories.value = cats
    templates.value = tpl
    roles.value = allRoles
    groups.value = allGroups
    tags.value = allTags

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

      <template v-if="!loading">
        <SectionHeader>{{ isEdit ? t('events.editEvent') : t('events.addEvent') }}</SectionHeader>

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
