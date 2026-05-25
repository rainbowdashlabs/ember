/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventFieldValueInput from '@/components/input/EventFieldValueInput.vue'
import type {AttendanceTemplateField, EventFieldEntry, StationMember} from '@/api/types'
import {EventFieldTypes} from '@/api/types'

const props = defineProps<{
  modelValue: EventFieldEntry
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  allMembers?: StationMember[]
  groupMembers?: Map<number, StationMember[]>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: EventFieldEntry]
  remove: []
}>()

const {t} = useI18n()

const fieldTypeOptions = [
  {value: EventFieldTypes.STRING, label: t('eventFields.typeString')},
  {value: EventFieldTypes.TIME, label: t('eventFields.typeTime')},
  {value: EventFieldTypes.DATE, label: t('eventFields.typeDate')},
  {value: EventFieldTypes.BOOLEAN, label: t('eventFields.typeBoolean')},
  {value: EventFieldTypes.ENUM, label: t('eventFields.typeEnum')},
  {value: EventFieldTypes.MEMBER, label: t('eventFields.typeMember')},
  {value: EventFieldTypes.MEMBER_LIST, label: t('eventFields.typeMemberList')},
  {value: EventFieldTypes.MEMBER_OF_GROUP, label: t('eventFields.typeMemberOfGroup')},
  {value: EventFieldTypes.MEMBER_LIST_OF_GROUP, label: t('eventFields.typeMemberListOfGroup')},
]

const name = ref(props.modelValue.name ?? '')
const fieldType = ref(props.modelValue.fieldType ?? 'string')
const fieldValue = ref(props.modelValue.value ?? '')
const overview = ref(props.modelValue.overview ?? false)
const attendanceFieldId = ref<number | null>(props.modelValue.attendanceFieldId ?? null)
const enumOptions = ref('')

function parseConfig(): Record<string, unknown> {
  if (!props.modelValue.config) return {}
  try {
    return JSON.parse(props.modelValue.config)
  } catch {
    return {}
  }
}

// Initialize enum options from config
const cfg = parseConfig()
if (cfg.options && Array.isArray(cfg.options)) {
  enumOptions.value = (cfg.options as string[]).join('\n')
}

function buildConfig(): string {
  const c: Record<string, unknown> = {}
  if (fieldType.value === 'enum' && enumOptions.value.trim()) {
    c.options = enumOptions.value.split('\n').map(o => o.trim()).filter(o => o)
  }
  return Object.keys(c).length > 0 ? JSON.stringify(c) : '{}'
}

const configString = computed(() => buildConfig())

const entry = computed<EventFieldEntry>(() => ({
  name: name.value,
  fieldType: fieldType.value,
  config: configString.value,
  value: fieldValue.value,
  overview: overview.value,
  attendanceFieldId: attendanceFieldId.value,
}))

watch(entry, val => emit('update:modelValue', val), {deep: true})
</script>

<template>
  <div class="rounded border border-(--border) p-3 space-y-2">
    <div class="flex flex-wrap items-end gap-2">
      <div class="flex-1 min-w-40 space-y-1">
        <FieldLabel>{{ t('eventFields.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('eventFields.namePlaceholder')"/>
      </div>

      <div class="w-40 space-y-1">
        <FieldLabel>{{ t('eventFields.type') }}</FieldLabel>
        <SelectInput v-model="fieldType">
          <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
        </SelectInput>
      </div>

      <div v-if="fieldType === 'enum'" class="w-48 space-y-1">
        <FieldLabel>{{ t('eventFields.enumOptions') }}</FieldLabel>
        <TextAreaInput v-model="enumOptions" :placeholder="t('eventFields.enumOptionsPlaceholder')" :rows="3"/>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('eventFields.overview') }}</FieldLabel>
        <div class="flex items-center px-3 py-2">
          <ToggleInput v-model="overview"/>
        </div>
      </div>

      <div v-if="attendanceFields && attendanceFields.length > 0" class="w-48 space-y-1">
        <FieldLabel>{{ t('eventFields.attendanceLink') }}</FieldLabel>
        <SelectInput :model-value="String(attendanceFieldId ?? '')"
                     @update:model-value="attendanceFieldId = $event ? Number($event) : null">
          <option value="">—</option>
          <option v-for="af in attendanceFields" :key="af.id" :value="String(af.id)">{{ af.name }}</option>
        </SelectInput>
      </div>

      <DeleteButton :label="t('common.delete')" @click="emit('remove')"/>
    </div>

    <!-- Value input -->
    <div v-if="showValue && name.trim()" class="space-y-1">
      <FieldLabel>{{ t('eventFields.value') }}</FieldLabel>
      <EventFieldValueInput
          :field-type="fieldType"
          :config="configString"
          :model-value="fieldValue"
          :all-members="allMembers"
          :group-members="groupMembers"
          @update:model-value="fieldValue = $event"
      />
    </div>
  </div>
</template>
