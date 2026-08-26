/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {FieldWidths} from '@/components/profilefields/fieldLayout'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import EventFieldTypeConfig from './EventFieldTypeConfig.vue'
import {fieldConstraint, isMemberFieldType} from './eventFieldConfig'
import type {AttendanceTemplateField} from '@/api/attendance'
import {EventFieldTypes, type EventFieldEntry} from '@/api/events'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

const modelValue = defineModel<EventFieldEntry>({required: true})

const props = defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  allMembers?: StationMember[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, StationMember[]>
  tags?: UserTag[]
  tagMembers?: Map<number, StationMember[]>
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
  {value: EventFieldTypes.MEMBER_OF_TYPE, label: t('eventFields.typeMemberOfType')},
  {value: EventFieldTypes.MEMBER_LIST_OF_TYPE, label: t('eventFields.typeMemberListOfType')},
  {value: EventFieldTypes.MEMBER_OF_TAG, label: t('eventFields.typeMemberOfTag')},
  {value: EventFieldTypes.MEMBER_LIST_OF_TAG, label: t('eventFields.typeMemberListOfTag')},
]

const name = ref('')
const fieldType = ref('STRING')
const fieldValue = ref('')
const overview = ref(false)
const isPublic = ref(false)
const attendanceFieldId = ref<number | null>(null)
const enumOptions = ref('')
const groupId = ref<string>('')
const width = ref<string>(FieldWidths.FULL)
const userType = ref<string>('')
const tagId = ref<string>('')
const selfRegistration = ref(false)

/** Takes the form apart into the fields that edit it. */
function seed(entry: EventFieldEntry) {
  name.value = entry.name ?? ''
  fieldType.value = entry.fieldType ?? 'STRING'
  fieldValue.value = entry.value ?? ''
  overview.value = entry.overview ?? false
  isPublic.value = entry.isPublic ?? false
  attendanceFieldId.value = entry.attendanceFieldId ?? null

  const cfg = entry.config ?? {}
  enumOptions.value = Array.isArray(cfg.options) ? (cfg.options as string[]).join('\n') : ''
  width.value = cfg.width ? String(cfg.width) : FieldWidths.FULL
  groupId.value = cfg.groupId ? String(cfg.groupId) : ''
  userType.value = cfg.userType ? String(cfg.userType) : ''
  tagId.value = cfg.tagId ? String(cfg.tagId) : ''
  selfRegistration.value = Boolean(cfg.selfRegistration)
}

seed(modelValue.value)

function buildConfig(): Record<string, unknown> {
  const c: Record<string, unknown> = {}
  if (width.value && width.value !== FieldWidths.FULL) c.width = width.value
  const constraint = fieldConstraint(fieldType.value)
  if (fieldType.value === 'ENUM' && enumOptions.value.trim()) {
    c.options = enumOptions.value.split('\n').map(o => o.trim()).filter(o => o)
  }
  if (constraint === 'group' && groupId.value) {
    c.groupId = Number(groupId.value)
  }
  if (constraint === 'userType' && userType.value) {
    c.userType = userType.value
  }
  if (constraint === 'tag' && tagId.value) {
    c.tagId = Number(tagId.value)
  }
  if (isMemberFieldType(fieldType.value) && selfRegistration.value) {
    c.selfRegistration = true
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

let handedOver: EventFieldEntry | null = null

watch(entry, val => {
  handedOver = val
  modelValue.value = val
}, {deep: true})

/**
 * Takes over a field that was put here from outside.
 *
 * <p>The rows are the questions in the order they are asked, and moving one hands this editor its
 * neighbour's question while the neighbour takes this one. Without reading the new one in, the form
 * would keep showing what it had and write it straight back, which is how moving a question appeared
 * to do nothing at all.
 */
watch(modelValue, incoming => {
  if (incoming === handedOver) return
  seed(incoming)
})
</script>

<template>
  <div class="rounded border border-(--border) p-3 space-y-2">
    <div class="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('eventFields.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('eventFields.namePlaceholder')" data-testid="event-field-name"/>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('eventFields.type') }}</FieldLabel>
        <SelectInput v-model="fieldType" class="w-full sm:w-auto" data-testid="event-field-type">
          <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
        </SelectInput>
      </div>
    </div>

    <EventFieldTypeConfig
        v-model:enum-options="enumOptions"
        v-model:group-id="groupId"
        v-model:user-type="userType"
        v-model:tag-id="tagId"
        v-model:self-registration="selfRegistration"
        :field-type="fieldType"
        :groups="groups"
        :tags="tags"
    />

    <div class="flex flex-wrap items-end gap-x-4 gap-y-2">
      <label class="flex items-center gap-2 pb-2 text-sm">
        <ToggleInput v-model="overview"/>
        {{ t('eventFields.overview') }}
      </label>
      <label class="flex items-center gap-2 pb-2 text-sm">
        <ToggleInput v-model="isPublic"/>
        {{ t('eventFields.public') }}
      </label>

      <div v-if="attendanceFields && matchingAttendanceFields.length > 0" class="w-48 space-y-1">
        <FieldLabel>{{ t('eventFields.attendanceLink') }}</FieldLabel>
        <SelectInput :model-value="String(attendanceFieldId ?? '')"
                     @update:model-value="attendanceFieldId = $event ? Number($event) : null">
          <option value="">-</option>
          <option v-for="af in matchingAttendanceFields" :key="af.id" :value="String(af.id)">{{ af.name }}</option>
        </SelectInput>
      </div>

      <div class="w-40">
        <WidthField v-model="width"/>
      </div>

      <div class="ms-auto flex items-center gap-1 pb-1">
        <DeleteButton :label="t('common.delete')" @click="emit('remove')"/>
      </div>
    </div>

    <div v-if="showValue && name.trim()" class="space-y-1">
      <FieldLabel>{{ t('eventFields.value') }}</FieldLabel>
      <EventFieldValueInput
          :field-type="fieldType"
          :config="configString"
          :model-value="fieldValue"
          :all-members="allMembers"
          :group-members="groupMembers"
          :tag-members="tagMembers"
          @update:model-value="fieldValue = $event"
      />
    </div>
  </div>
</template>
