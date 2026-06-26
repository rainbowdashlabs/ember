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
import type {AttendanceTemplateField, EventFieldEntry, MemberGroup, StationMember} from '@/api/types'
import {EventFieldTypes} from '@/api/types'

const modelValue = defineModel<EventFieldEntry>({required: true})

const props = defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  allMembers?: StationMember[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, StationMember[]>
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const fieldTypeOptions = [
  {value: EventFieldTypes.STRING, label: t('eventFields.typeString')},
  {value: EventFieldTypes.NUMBER, label: t('eventFields.typeNumber')},
  {value: EventFieldTypes.TIME, label: t('eventFields.typeTime')},
  {value: EventFieldTypes.DATE, label: t('eventFields.typeDate')},
  {value: EventFieldTypes.BOOLEAN, label: t('eventFields.typeBoolean')},
  {value: EventFieldTypes.ENUM, label: t('eventFields.typeEnum')},
  {value: EventFieldTypes.URL, label: t('eventFields.typeUrl')},
  {value: EventFieldTypes.TEXTAREA, label: t('eventFields.typeTextarea')},
  {value: EventFieldTypes.LOCATION, label: t('eventFields.typeLocation')},
  {value: EventFieldTypes.MEMBER, label: t('eventFields.typeMember')},
  {value: EventFieldTypes.MEMBER_LIST, label: t('eventFields.typeMemberList')},
  {value: EventFieldTypes.MEMBER_OF_GROUP, label: t('eventFields.typeMemberOfGroup')},
  {value: EventFieldTypes.MEMBER_LIST_OF_GROUP, label: t('eventFields.typeMemberListOfGroup')},
]

const name = ref(modelValue.value.name ?? '')
const fieldType = ref(modelValue.value.fieldType ?? 'STRING')
const fieldValue = ref(modelValue.value.value ?? '')
const overview = ref(modelValue.value.overview ?? false)
const isPublic = ref(modelValue.value.isPublic ?? false)
const attendanceFieldId = ref<number | null>(modelValue.value.attendanceFieldId ?? null)
const enumOptions = ref('')
const groupId = ref<string>('')

function parseConfig(): Record<string, unknown> {
  return modelValue.value.config ?? {}
}

// Initialize from config
const cfg = parseConfig()
if (cfg.options && Array.isArray(cfg.options)) {
  enumOptions.value = (cfg.options as string[]).join('\n')
}
if (cfg.groupId) {
  groupId.value = String(cfg.groupId)
}

function isGroupField(): boolean {
  return fieldType.value === 'MEMBER_OF_GROUP' || fieldType.value === 'MEMBER_LIST_OF_GROUP'
}

function buildConfig(): Record<string, unknown> {
  const c: Record<string, unknown> = {}
  if (fieldType.value === 'ENUM' && enumOptions.value.trim()) {
    c.options = enumOptions.value.split('\n').map(o => o.trim()).filter(o => o)
  }
  if (isGroupField() && groupId.value) {
    c.groupId = Number(groupId.value)
  }
  return c
}

const configString = computed(() => buildConfig())

const matchingAttendanceFields = computed(() =>
    (props.attendanceFields ?? []).filter(af => af.fieldType === fieldType.value)
)

const entry = computed<EventFieldEntry>(() => ({
  name: name.value,
  fieldType: fieldType.value,
  config: configString.value,
  value: fieldValue.value,
  overview: overview.value,
  isPublic: isPublic.value,
  attendanceFieldId: attendanceFieldId.value,
}))

watch(entry, val => { modelValue.value = val }, {deep: true})
</script>

<template>
  <div class="rounded border border-(--border) p-3 space-y-2">
    <div class="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('eventFields.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('eventFields.namePlaceholder')"/>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('eventFields.type') }}</FieldLabel>
        <SelectInput v-model="fieldType" class="w-full sm:w-auto">
          <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
        </SelectInput>
      </div>
    </div>

    <div v-if="fieldType === 'ENUM'" class="space-y-1">
      <FieldLabel>{{ t('eventFields.enumOptions') }}</FieldLabel>
      <TextAreaInput v-model="enumOptions" :placeholder="t('eventFields.enumOptionsPlaceholder')" :rows="3"/>
    </div>

    <div v-if="isGroupField() && groups && groups.length > 0" class="space-y-1">
      <FieldLabel>{{ t('eventFields.group') }}</FieldLabel>
      <SelectInput v-model="groupId" class="w-full sm:w-auto">
        <option value="">{{ t('eventFields.selectGroup') }}</option>
        <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
      </SelectInput>
    </div>

    <div class="flex items-center gap-4">
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput v-model="overview"/>
        {{ t('eventFields.overview') }}
      </label>
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput v-model="isPublic"/>
        {{ t('eventFields.public') }}
      </label>

      <div v-if="attendanceFields && matchingAttendanceFields.length > 0" class="w-48 space-y-1">
        <FieldLabel>{{ t('eventFields.attendanceLink') }}</FieldLabel>
        <SelectInput :model-value="String(attendanceFieldId ?? '')"
                     @update:model-value="attendanceFieldId = $event ? Number($event) : null">
          <option value="">—</option>
          <option v-for="af in matchingAttendanceFields" :key="af.id" :value="String(af.id)">{{ af.name }}</option>
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
