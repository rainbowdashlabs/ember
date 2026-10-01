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
import GroupSelector from './fieldmodal/GroupSelector.vue'
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import FieldDefaultValueSection from '@/components/input/FieldDefaultValueSection.vue'
import BehaviorToggles from './fieldmodal/BehaviorToggles.vue'
import PositionField from './fieldmodal/PositionField.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {FieldWidths} from '@/components/profilefields/fieldLayout'
import ModalActions from './fieldmodal/ModalActions.vue'
import type {
  AttendanceFieldConfig,
  AttendanceFieldType,
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

const ATTENDANCE_FIELD_TYPES = {
  STRING: 'STRING',
  NUMBER: 'NUMBER',
  DATE: 'DATE',
  TIME: 'TIME',
  BOOLEAN: 'BOOLEAN',
  ENUM: 'ENUM',
  URL: 'URL',
  TEXTAREA: 'TEXTAREA',
  MEMBER: 'MEMBER',
  MEMBER_LIST: 'MEMBER_LIST',
  MEMBER_OF_GROUP: 'MEMBER_OF_GROUP',
  MEMBER_LIST_OF_GROUP: 'MEMBER_LIST_OF_GROUP',
} as const satisfies Record<AttendanceFieldType, AttendanceFieldType>

function isAttendanceFieldType(value: string): value is AttendanceFieldType {
  return Object.hasOwn(ATTENDANCE_FIELD_TYPES, value)
}

const {t} = useI18n()

const open = defineModel<boolean>({default: false})

const fieldName = ref('')
const fieldType = ref('STRING')
const fieldConfigGroupId = ref('')
const fieldConfigRequired = ref(false)
const fieldConfigAutoAttend = ref(false)
const fieldEnumOptions = ref<string[]>([])
const fieldHasDefault = ref(false)
const fieldDefaultValue = ref('')
const fieldDefaultBool = ref(false)
const fieldDefaultToday = ref(false)
const fieldDefaultNumber = ref(0)
const fieldPosition = ref(0)
const fieldWidth = ref<string>(FieldWidths.FULL)

const isEditing = computed(() => props.field !== null)

function fieldTypeNeedsGroup(type: string): boolean {
  return ['MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'].includes(type)
}

function fieldTypeCanAutoAttend(type: string): boolean {
  return ['MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'].includes(type)
}

function fieldTypeCanHaveDefault(type: string): boolean {
  return ['STRING', 'NUMBER', 'TIME', 'DATE', 'BOOLEAN', 'ENUM'].includes(type)
}

function buildDefaultValue(): unknown {
  if (!fieldHasDefault.value || !fieldTypeCanHaveDefault(fieldType.value)) return null
  if (fieldType.value === 'BOOLEAN') return fieldDefaultBool.value
  if (fieldType.value === 'DATE') return fieldDefaultToday.value ? '__TODAY__' : ''
  if (fieldType.value === 'NUMBER') return fieldDefaultNumber.value
  return fieldDefaultValue.value.trim()
}

function buildConfig(): AttendanceFieldConfig {
  return {
    required: fieldConfigRequired.value,
    width: fieldWidth.value && fieldWidth.value !== FieldWidths.FULL ? fieldWidth.value : null,
    groupId: fieldTypeNeedsGroup(fieldType.value) && fieldConfigGroupId.value
        ? Number(fieldConfigGroupId.value)
        : null,
    autoAttend: fieldTypeCanAutoAttend(fieldType.value) && fieldConfigAutoAttend.value,
    options: fieldType.value === 'ENUM' && fieldEnumOptions.value.length > 0 ? [...fieldEnumOptions.value] : null,
    defaultValue: buildDefaultValue(),
  }
}

watch([open, () => props.field], () => {
  if (!open.value) return
  if (props.field) {
    fieldName.value = props.field.name
    fieldType.value = props.field.fieldType
    const cfg = props.field.config
    fieldConfigGroupId.value = cfg.groupId != null ? String(cfg.groupId) : ''
    fieldWidth.value = cfg.width ?? FieldWidths.FULL
    fieldConfigRequired.value = cfg.required
    fieldConfigAutoAttend.value = cfg.autoAttend
    fieldEnumOptions.value = [...(cfg.options ?? [])]
    fieldHasDefault.value = cfg.defaultValue != null
    if (props.field.fieldType === 'BOOLEAN') {
      fieldDefaultBool.value = cfg.defaultValue === true
    } else if (props.field.fieldType === 'DATE') {
      fieldDefaultToday.value = cfg.defaultValue === '__TODAY__'
    } else if (props.field.fieldType === 'NUMBER') {
      fieldDefaultNumber.value = typeof cfg.defaultValue === 'number' ? cfg.defaultValue : 0
    } else {
      fieldDefaultValue.value = typeof cfg.defaultValue === 'string' ? cfg.defaultValue : ''
    }
    fieldPosition.value = props.field.position
  } else {
    fieldName.value = ''
    fieldType.value = 'STRING'
    fieldConfigGroupId.value = ''
    fieldConfigRequired.value = false
    fieldConfigAutoAttend.value = false
    fieldEnumOptions.value = []
    fieldHasDefault.value = false
    fieldDefaultValue.value = ''
    fieldDefaultBool.value = false
    fieldDefaultToday.value = false
    fieldDefaultNumber.value = 0
    fieldPosition.value = props.fieldCount
  }
})

function handleSave() {
  if (!isAttendanceFieldType(fieldType.value)) return
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
      <GroupSelector v-if="fieldTypeNeedsGroup(fieldType)" v-model="fieldConfigGroupId"
                     :available-groups="availableGroups"/>
      <QuestionOptionsEditor
          v-if="fieldType === 'ENUM'"
          v-model="fieldEnumOptions"
          :label="t('attendanceConfig.fieldEnumOptions')"
      />
      <FieldDefaultValueSection
        v-if="fieldTypeCanHaveDefault(fieldType)"
        v-model:has-default="fieldHasDefault"
        v-model:default-value="fieldDefaultValue"
        v-model:default-bool="fieldDefaultBool"
        v-model:default-today="fieldDefaultToday"
        v-model:default-number="fieldDefaultNumber"
        :toggle-label="t('attendanceConfig.fieldHasDefault')"
        :placeholder="t('attendanceConfig.fieldDefaultValuePlaceholder')"
        :date-hint="t('attendanceConfig.fieldDefaultDateHint')"
        :value-hint="t('attendanceConfig.fieldDefaultValueHint')"
        :field-type="fieldType"
        :enum-options="fieldEnumOptions"
      />
      <BehaviorToggles
        v-model:required="fieldConfigRequired"
        v-model:auto-attend="fieldConfigAutoAttend"
        :show-auto-attend="fieldTypeCanAutoAttend(fieldType)"
      />
      <WidthField v-model="fieldWidth"/>

      <PositionField v-model="fieldPosition"/>
      <ModalActions :saving="saving" :disabled="!fieldName" @cancel="open = false" @submit="handleSave"/>
    </div>
  </Modal>
</template>
