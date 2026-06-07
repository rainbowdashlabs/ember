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
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { StationEvent, EventCategory, AttendanceTemplate, AttendanceTemplateField, MemberGroup, UserTag } from '@/api/types'
import { EventTypes } from '@/api/types'
import type { EventFieldDefault } from '@/api/events'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
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
  'update:modelValue': [value: boolean]
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
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
// field defaults: fieldId -> { source, value }
const fieldDefaults = ref<Map<number, { source: string; value: string }>>(new Map())
const saving = ref(false)

function toLocalDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(() => props.modelValue, (open) => {
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
    selectedUserTypes.value = [...props.eventUserTypes]
    selectedGroupIds.value = [...props.eventGroupIds]
    selectedTagIds.value = [...props.eventTagIds]
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
    selectedUserTypes.value = []
    selectedGroupIds.value = []
    selectedTagIds.value = []
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
    restrictedUserTypes: selectedUserTypes.value,
    restrictedGroupIds: selectedGroupIds.value,
    restrictedTagIds: selectedTagIds.value,
    fieldDefaults: [...fieldDefaults.value.entries()]
      .filter(([, v]) => v.source)
      .map(([fieldId, v]) => ({ fieldId, source: v.source, value: v.value || undefined })),
  })
  saving.value = false
}
</script>

<template>
  <Modal :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <SectionHeader>{{ event ? t('events.editEvent') : t('events.addEvent') }}</SectionHeader>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.name') }}</FieldLabel>
        <TextInput v-model="eventName" :placeholder="t('events.namePlaceholder')" />
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.description') }}</FieldLabel>
        <TextInput v-model="eventDescription" :placeholder="t('events.descriptionPlaceholder')" />
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.type') }}</FieldLabel>
        <SelectInput v-model="eventType">
          <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
          <option :value="EventTypes.ONE_TIME">{{ t('events.typeOneTime') }}</option>
        </SelectInput>
      </div>

      <div v-if="eventType === EventTypes.RECURRING" class="space-y-1">
        <FieldLabel>{{ t('events.dayOfWeek') }}</FieldLabel>
        <SelectInput v-model="eventDayOfWeek">
          <option value="1">Montag</option>
          <option value="2">Dienstag</option>
          <option value="3">Mittwoch</option>
          <option value="4">Donnerstag</option>
          <option value="5">Freitag</option>
          <option value="6">Samstag</option>
          <option value="7">Sonntag</option>
        </SelectInput>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('events.startTime') }}</FieldLabel>
          <DateTimeInput v-model="eventStartTime" />
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('events.endTime') }}</FieldLabel>
          <DateTimeInput v-model="eventEndTime" />
        </div>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.template') }}</FieldLabel>
        <SelectInput v-model="eventTemplateId">
          <option value="">{{ t('events.noTemplate') }}</option>
          <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
        </SelectInput>
        <p class="text-xs text-(--text-muted)">{{ t('events.templateHint') }}</p>
      </div>

      <!-- Field defaults for template -->
      <div v-if="currentTemplateFields.length > 0" class="space-y-3">
        <FieldLabel>{{ t('events.fieldDefaults') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('events.fieldDefaultsHint') }}</p>
        <div class="space-y-2">
          <div v-for="field in currentTemplateFields" :key="field.id" class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
            <div class="text-sm font-medium">{{ field.name }} <span class="text-xs text-(--text-muted)">({{ field.fieldType }})</span></div>
            <div class="grid gap-2 sm:grid-cols-2">
              <SelectInput
                :model-value="getFieldDefault(field.id).source"
                @update:model-value="setFieldDefaultSource(field.id, ($event as string) ?? '')"
              >
                <option value="">{{ t('events.noDefault') }}</option>
                <option value="VALUE">{{ t('events.staticValue') }}</option>
                <option v-for="src in EVENT_SOURCES" :key="src.value" :value="src.value">{{ src.label }}</option>
              </SelectInput>
              <TextInput
                v-if="getFieldDefault(field.id).source === 'VALUE'"
                :model-value="getFieldDefault(field.id).value"
                :placeholder="t('events.defaultValuePlaceholder')"
                @update:model-value="setFieldDefaultValue(field.id, ($event as string) ?? '')"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.category') }}</FieldLabel>
        <SelectInput v-model="eventCategoryId">
          <option value="">{{ t('events.noCategory') }}</option>
          <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </div>

      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('events.requiresRegistration') }}</label>
        <ToggleInput v-model="eventRequiresRegistration" />
      </div>

      <template v-if="eventRequiresRegistration">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</label>
          <ToggleInput v-model="eventRequiresConfirmation" />
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('events.requiresConfirmationHint') }}</p>

        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('events.hasDeadline') }}</label>
          <ToggleInput v-model="eventHasDeadline" />
        </div>

        <div v-if="eventHasDeadline" class="space-y-1">
          <FieldLabel>{{ t('events.registrationDeadline') }}</FieldLabel>
          <DateTimeInput v-model="eventRegistrationDeadline" />
        </div>
      </template>

      <!-- Restrictions -->
      <div class="space-y-2">
        <FieldLabel>{{ t('events.restrictToRoles') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('events.restrictToRolesHint') }}</p>
        <RestrictionPicker
          :groups="groups"
          :tags="tags"
          :selected-user-types="selectedUserTypes"
          :selected-group-ids="selectedGroupIds"
          :selected-tag-ids="selectedTagIds"
          @update:selected-user-types="selectedUserTypes = $event"
          @update:selected-group-ids="selectedGroupIds = $event"
          @update:selected-tag-ids="selectedTagIds = $event"
        />
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !eventName || !eventStartTime || !eventEndTime" @click="submit">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
