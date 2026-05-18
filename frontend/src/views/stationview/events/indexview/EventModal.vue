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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { StationEvent, EventCategory, AttendanceTemplate, AttendanceTemplateField, Role, MemberGroup } from '@/api/types'
import { Roles, EventTypes } from '@/api/types'
import type { EventFieldDefault } from '@/api/events'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  event: StationEvent | null
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  roles: Role[]
  groups: MemberGroup[]
  eventRoleIds: number[]
  eventGroupIds: number[]
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
const eventTemplateId = ref('')
const eventCategoryId = ref('')
const eventRequiresRegistration = ref(false)
const eventHasDeadline = ref(false)
const eventRegistrationDeadline = ref('')
const eventRequiresConfirmation = ref(false)
const selectedRoleIds = ref<Set<number>>(new Set())
const selectedGroupIds = ref<Set<number>>(new Set())
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
    selectedRoleIds.value = new Set(props.eventRoleIds)
    selectedGroupIds.value = new Set(props.eventGroupIds)
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
    selectedRoleIds.value = new Set()
    selectedGroupIds.value = new Set()
    fieldDefaults.value = new Map()
  }
})

const RESTRICTION_ROLES = [Roles.MEMBER, Roles.MEMBER_MANAGER, Roles.TEAM] as readonly string[]

const roleFriendlyNames: Record<string, string> = {
  MEMBER: 'Mitglied', MEMBER_MANAGER: 'Mitgliedsmanager', TEAM: 'Team',
}

const restrictionRoles = computed(() =>
  props.roles.filter(r => RESTRICTION_ROLES.includes(r.role))
)

function toggleRole(roleId: number) {
  const s = new Set(selectedRoleIds.value)
  if (s.has(roleId)) s.delete(roleId); else s.add(roleId)
  selectedRoleIds.value = s
}

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

function toggleGroup(groupId: number) {
  const s = new Set(selectedGroupIds.value)
  if (s.has(groupId)) s.delete(groupId); else s.add(groupId)
  selectedGroupIds.value = s
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
    restrictedRoleIds: [...selectedRoleIds.value],
    restrictedGroupIds: [...selectedGroupIds.value],
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
        <label class="block text-sm font-medium">{{ t('events.name') }}</label>
        <TextInput v-model="eventName" :placeholder="t('events.namePlaceholder')" />
      </div>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('events.description') }}</label>
        <TextInput v-model="eventDescription" :placeholder="t('events.descriptionPlaceholder')" />
      </div>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('events.type') }}</label>
        <SelectInput v-model="eventType">
          <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
          <option :value="EventTypes.ONE_TIME">{{ t('events.typeOneTime') }}</option>
        </SelectInput>
      </div>

      <div v-if="eventType === EventTypes.RECURRING" class="space-y-1">
        <label class="block text-sm font-medium">{{ t('events.dayOfWeek') }}</label>
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
          <label class="block text-sm font-medium">{{ t('events.startTime') }}</label>
          <DateTimeInput v-model="eventStartTime" />
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('events.endTime') }}</label>
          <DateTimeInput v-model="eventEndTime" />
        </div>
      </div>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('events.template') }}</label>
        <SelectInput v-model="eventTemplateId">
          <option value="">{{ t('events.noTemplate') }}</option>
          <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
        </SelectInput>
        <p class="text-xs text-(--text-muted)">{{ t('events.templateHint') }}</p>
      </div>

      <!-- Field defaults for template -->
      <div v-if="currentTemplateFields.length > 0" class="space-y-3">
        <label class="block text-sm font-medium">{{ t('events.fieldDefaults') }}</label>
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
        <label class="block text-sm font-medium">{{ t('events.category') }}</label>
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
          <label class="block text-sm font-medium">{{ t('events.registrationDeadline') }}</label>
          <DateTimeInput v-model="eventRegistrationDeadline" />
        </div>
      </template>

      <!-- Restrictions -->
      <div class="space-y-2">
        <label class="block text-sm font-medium">{{ t('events.restrictToRoles') }}</label>
        <p class="text-xs text-(--text-muted)">{{ t('events.restrictToRolesHint') }}</p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="role in restrictionRoles"
            :key="role.id"
            type="button"
            class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all"
            :class="selectedRoleIds.has(role.id)
              ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
              : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
            @click="toggleRole(role.id)"
          >
            {{ roleFriendlyNames[role.role] ?? role.role }}
          </button>
        </div>
      </div>

      <div v-if="groups.length > 0" class="space-y-2">
        <label class="block text-sm font-medium">{{ t('events.restrictToGroups') }}</label>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="group in groups"
            :key="group.id"
            type="button"
            class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all"
            :class="selectedGroupIds.has(group.id)
              ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
              : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
            @click="toggleGroup(group.id)"
          >
            {{ group.name }}
          </button>
        </div>
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
