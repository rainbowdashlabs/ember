/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BasicInfoFields from './eventmodal/BasicInfoFields.vue'
import ScheduleFields from './eventmodal/ScheduleFields.vue'
import TemplateSection from './eventmodal/TemplateSection.vue'
import CategorySelect from './eventmodal/CategorySelect.vue'
import RegistrationFields from './eventmodal/RegistrationFields.vue'
import RestrictionsFields from './eventmodal/RestrictionsFields.vue'
import type { StationEvent, EventCategory, AttendanceTemplate, AttendanceTemplateField, MemberGroup, UserTag } from '@/api/types'
import { EventTypes } from '@/api/types'
import type { EventFieldDefault } from '@/api/events'
import { type RestrictionSelection, emptyRestriction } from '@/components/input/restriction'

const { t } = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  event: StationEvent | null
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  groups: MemberGroup[]
  tags: UserTag[]
  eventUserTypes: string[]
  eventGroupIds: number[]
  eventTagIds: number[]
  templateFields: AttendanceTemplateField[]
  eventFieldDefaults: EventFieldDefault[]
}>()

const emit = defineEmits<{
  save: [data: Record<string, unknown>]
}>()

const eventName = ref('')
const eventDescription = ref('')
const eventType = ref<string>(EventTypes.RECURRING)
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
const restriction = ref<RestrictionSelection>(emptyRestriction())
const fieldDefaults = ref<Map<number, { source: string; value: string }>>(new Map())
const saving = ref(false)

function toLocalDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(modelValue, (open) => {
  if (!open) return
  const ev = props.event
  if (ev) {
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
    restriction.value = {
      userTypes: [...props.eventUserTypes],
      groupIds: [...props.eventGroupIds],
      tagIds: [...props.eventTagIds],
      memberIds: [],
      mode: 'AND',
    }
    const fdMap = new Map<number, { source: string; value: string }>()
    for (const fd of props.eventFieldDefaults) {
      fdMap.set(fd.fieldId, { source: fd.source, value: fd.value ?? '' })
    }
    fieldDefaults.value = fdMap
  } else {
    eventName.value = ''
    eventDescription.value = ''
    eventType.value = EventTypes.RECURRING
    eventDayOfWeek.value = '1'
    eventStartTime.value = ''
    eventEndTime.value = ''
    eventTemplateId.value = ''
    eventCategoryId.value = ''
    eventRequiresRegistration.value = false
    eventHasDeadline.value = false
    eventRegistrationDeadline.value = ''
    eventRequiresConfirmation.value = false
    restriction.value = emptyRestriction()
    fieldDefaults.value = new Map()
  }
})

const EVENT_SOURCES = [
  { value: 'EVENT_NAME', label: 'Terminname' },
  { value: 'EVENT_DESCRIPTION', label: 'Beschreibung' },
  { value: 'EVENT_START_TIME', label: 'Startzeit' },
  { value: 'EVENT_END_TIME', label: 'Endzeit' },
]

const currentTemplateFields = computed(() => {
  if (!eventTemplateId.value) return []
  return props.templateFields.filter(f => f.templateId === Number(eventTemplateId.value))
})

function getFieldDefault(fieldId: number): { source: string; value: string } {
  return fieldDefaults.value.get(fieldId) ?? { source: '', value: '' }
}

function setFieldDefaultSource(fieldId: number, source: string) {
  const existing = getFieldDefault(fieldId)
  const m = new Map(fieldDefaults.value)
  if (!source) {
    m.delete(fieldId)
  } else {
    m.set(fieldId, { source, value: source === 'VALUE' ? existing.value : '' })
  }
  fieldDefaults.value = m
}

function setFieldDefaultValue(fieldId: number, value: string) {
  const existing = getFieldDefault(fieldId)
  const m = new Map(fieldDefaults.value)
  m.set(fieldId, { source: existing.source, value })
  fieldDefaults.value = m
}

function submit() {
  saving.value = true
  emit('save', {
    name: eventName.value,
    description: eventDescription.value || null,
    eventType: eventType.value,
    dayOfWeek: eventType.value === EventTypes.RECURRING ? Number(eventDayOfWeek.value) : null,
    startTime: eventStartTime.value ? new Date(eventStartTime.value).toISOString() : null,
    endTime: eventEndTime.value ? new Date(eventEndTime.value).toISOString() : null,
    templateId: eventTemplateId.value ? Number(eventTemplateId.value) : null,
    categoryId: eventCategoryId.value ? Number(eventCategoryId.value) : null,
    requiresRegistration: eventRequiresRegistration.value,
    registrationDeadline: eventHasDeadline.value && eventRegistrationDeadline.value
      ? new Date(eventRegistrationDeadline.value).toISOString() : null,
    requiresConfirmation: eventRequiresConfirmation.value,
    restriction: restriction.value,
    fieldDefaults: [...fieldDefaults.value.entries()]
      .filter(([, v]) => v.source)
      .map(([fieldId, v]) => ({ fieldId, source: v.source, value: v.value || undefined })),
  })
  saving.value = false
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ event ? t('events.editEvent') : t('events.addEvent') }}</SubHeader>
      <BasicInfoFields v-model:event-name="eventName" v-model:event-description="eventDescription" v-model:event-type="eventType" v-model:event-day-of-week="eventDayOfWeek"/>
      <ScheduleFields v-model:event-start-time="eventStartTime" v-model:event-end-time="eventEndTime"/>
      <TemplateSection v-model:event-template-id="eventTemplateId" :templates="templates" :current-template-fields="currentTemplateFields" :sources="EVENT_SOURCES" :get-default="getFieldDefault" @update-source="setFieldDefaultSource" @update-value="setFieldDefaultValue"/>
      <CategorySelect v-model:event-category-id="eventCategoryId" :categories="categories"/>
      <RegistrationFields v-model:event-requires-registration="eventRequiresRegistration" v-model:event-requires-confirmation="eventRequiresConfirmation" v-model:event-has-deadline="eventHasDeadline" v-model:event-registration-deadline="eventRegistrationDeadline"/>
      <RestrictionsFields v-model="restriction" :groups="groups" :tags="tags"/>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !eventName || !eventStartTime || !eventEndTime" @click="submit">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
