/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BasicFields from './fieldmodal/BasicFields.vue'
import AutoAttendToggle from './fieldmodal/AutoAttendToggle.vue'
import PositionField from './fieldmodal/PositionField.vue'
import ModalActions from './fieldmodal/ModalActions.vue'
import QuestionSettingsEditor from '@/components/input/questionsettings/QuestionSettingsEditor.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {FieldWidths} from '@/components/profilefields/fieldLayout'
import {FieldTypes, memberConstraintOf, namesMembers, type FieldTypeName} from '@/api/fieldTypes'
import {
  defaultAsText,
  typedDefault,
  type QuestionSetting,
  type QuestionSettingsModel,
} from '@/components/input/questionsettings/questionSettings'
import type {
  AttendanceFieldConfig,
  AttendanceTemplateField,
  MemberGroup,
  TemplateFieldRequest,
} from '@/api/generated/schema'

const props = defineProps<{
  field: AttendanceTemplateField | null
  availableGroups: MemberGroup[]
  saving: boolean
  fieldCount: number
}>()

const emit = defineEmits<{
  save: [data: TemplateFieldRequest]
  close: []
}>()

const {t} = useI18n()

const open = defineModel<boolean>({default: false})

const fieldName = ref('')
const fieldType = ref<FieldTypeName>(FieldTypes.TEXT)
const settings = ref<QuestionSettingsModel>({})
const autoAttend = ref(false)
const fieldPosition = ref(0)
const fieldWidth = ref<string>(FieldWidths.FULL)

const isEditing = computed(() => props.field !== null)

/**
 * What a sheet's field offers to set. A member field starts empty, because whom a sheet names is
 * decided by who comes, and a date starts from today or not at all.
 */
const offers = computed<QuestionSetting[]>(() => namesMembers(fieldType.value)
    ? ['options', 'members', 'required']
    : ['options', 'default', 'todayDefault', 'members', 'required'])

function buildConfig(): AttendanceFieldConfig {
  const type = fieldType.value
  const chosen = settings.value
  const options = chosen.options ?? []
  return {
    required: chosen.required ?? false,
    width: fieldWidth.value && fieldWidth.value !== FieldWidths.FULL ? fieldWidth.value : null,
    groupId: memberConstraintOf(type) === 'group' ? chosen.groupId ?? null : null,
    autoAttend: namesMembers(type) && autoAttend.value,
    options: type === FieldTypes.CHOICE && options.length > 0 ? [...options] : null,
    defaultValue: offers.value.includes('default') ? typedDefault(type, chosen.defaultValue) ?? null : null,
  }
}

watch([open, () => props.field], () => {
  if (!open.value) return
  const field = props.field
  fieldName.value = field?.name ?? ''
  fieldType.value = field?.fieldType ?? FieldTypes.TEXT
  settings.value = field
      ? {
          required: field.config.required,
          options: [...(field.config.options ?? [])],
          groupId: field.config.groupId,
          defaultValue: defaultAsText(field.config.defaultValue),
        }
      : {}
  autoAttend.value = field?.config.autoAttend ?? false
  fieldWidth.value = field?.config.width ?? FieldWidths.FULL
  fieldPosition.value = field?.position ?? props.fieldCount
})

function handleSave() {
  emit('save', {
    name: fieldName.value,
    fieldType: fieldType.value,
    config: buildConfig(),
    position: fieldPosition.value,
  })
}
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ isEditing ? t('attendanceConfig.editField') : t('attendanceConfig.addField') }}</SubHeader>
      <BasicFields v-model:name="fieldName" v-model:field-type="fieldType"/>
      <QuestionSettingsEditor
          v-model="settings"
          :default-hint="t('attendanceConfig.fieldDefaultValueHint')"
          :field-type="fieldType"
          :groups="availableGroups"
          :offers="offers"
      />
      <AutoAttendToggle v-if="namesMembers(fieldType)" v-model:auto-attend="autoAttend"/>
      <WidthField v-model="fieldWidth"/>
      <PositionField v-model="fieldPosition"/>
      <ModalActions :saving="saving" :disabled="!fieldName" @cancel="open = false" @submit="handleSave"/>
    </div>
  </Modal>
</template>
