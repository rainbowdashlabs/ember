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
import EnumOptionsField from './fieldmodal/EnumOptionsField.vue'
import FieldDefaultValueSection from '@/components/input/FieldDefaultValueSection.vue'
import BehaviorToggles from './fieldmodal/BehaviorToggles.vue'
import PositionField from './fieldmodal/PositionField.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {FieldWidths} from '@/components/profilefields/fieldLayout'
import ModalActions from './fieldmodal/ModalActions.vue'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {MemberGroup} from '@/api/types'

const props = defineProps<{
  field: AttendanceTemplateField | null
  availableGroups: MemberGroup[]
  saving: boolean
  fieldCount: number
}>()

const emit = defineEmits<{
  save: [data: { name: string; fieldType: string; config: Record<string, unknown>; position: number }]
  close: []
}>()

const {t} = useI18n()

const open = defineModel<boolean>({default: false})

const fieldName = ref('')
const fieldType = ref('STRING')
const fieldConfigGroupId = ref('')
const fieldConfigRequired = ref(false)
const fieldConfigAutoAttend = ref(false)
const fieldEnumOptions = ref('')
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

function parseConfig(config?: Record<string, unknown>): Record<string, unknown> {
  return config ?? {}
}

function buildConfig(): Record<string, unknown> {
  const cfg: Record<string, unknown> = {}
  if (fieldConfigRequired.value) cfg.required = true
  if (fieldWidth.value && fieldWidth.value !== FieldWidths.FULL) cfg.width = fieldWidth.value
  if (fieldTypeNeedsGroup(fieldType.value) && fieldConfigGroupId.value) {
    cfg.groupId = Number(fieldConfigGroupId.value)
  }
  if (fieldTypeCanAutoAttend(fieldType.value) && fieldConfigAutoAttend.value) {
    cfg.autoAttend = true
  }
  if (fieldType.value === 'ENUM' && fieldEnumOptions.value.trim()) {
    cfg.options = fieldEnumOptions.value.split('\n').map(o => o.trim()).filter(o => o.length > 0)
  }
  if (fieldHasDefault.value && fieldTypeCanHaveDefault(fieldType.value)) {
    if (fieldType.value === 'BOOLEAN') {
      cfg.defaultValue = fieldDefaultBool.value
    } else if (fieldType.value === 'DATE') {
      cfg.defaultValue = fieldDefaultToday.value ? '__TODAY__' : ''
    } else if (fieldType.value === 'NUMBER') {
      cfg.defaultValue = fieldDefaultNumber.value
    } else {
      cfg.defaultValue = fieldDefaultValue.value.trim()
    }
  }
  return cfg
}

watch([open, () => props.field], () => {
  if (!open.value) return
  if (props.field) {
    fieldName.value = props.field.name ?? ''
    fieldType.value = props.field.fieldType ?? 'STRING'
    const cfg = parseConfig(props.field.config)
    fieldConfigGroupId.value = cfg.groupId ? String(cfg.groupId) : ''
    fieldWidth.value = cfg.width ? String(cfg.width) : FieldWidths.FULL
    fieldConfigRequired.value = !!cfg.required
    fieldConfigAutoAttend.value = !!cfg.autoAttend
    fieldEnumOptions.value = ((cfg.options as string[]) ?? []).join('\n')
    fieldHasDefault.value = cfg.defaultValue !== undefined
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
    fieldEnumOptions.value = ''
    fieldHasDefault.value = false
    fieldDefaultValue.value = ''
    fieldDefaultBool.value = false
    fieldDefaultToday.value = false
    fieldDefaultNumber.value = 0
    fieldPosition.value = props.fieldCount
  }
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
      <GroupSelector v-if="fieldTypeNeedsGroup(fieldType)" v-model="fieldConfigGroupId"
                     :available-groups="availableGroups"/>
      <EnumOptionsField v-if="fieldType === 'ENUM'" v-model="fieldEnumOptions"/>
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
