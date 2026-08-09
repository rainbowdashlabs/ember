/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BasicFields from './fieldmodal/BasicFields.vue'
import EnumOptionsField from './fieldmodal/EnumOptionsField.vue'
import AgeFields from './fieldmodal/AgeFields.vue'
import FieldDefaultValueSection from '@/components/input/FieldDefaultValueSection.vue'
import BehaviorToggles from './fieldmodal/BehaviorToggles.vue'
import PositionField from './fieldmodal/PositionField.vue'
import ModalActions from './fieldmodal/ModalActions.vue'
import {FieldTypes, parseFieldConfig, type ProfileField} from '@/api/profileFields'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  field: ProfileField | null
  scope: string
  groupId?: string
  dateFields: ProfileField[]
}>()

const emit = defineEmits<{
  save: [data: { name: string; fieldType: string; config: string; position: number; scope: string; keepOnArchive: boolean }]
}>()

const fieldName = ref('')
const fieldType = ref<string>(FieldTypes.TEXT)
const fieldRequired = ref(false)
const fieldReadonly = ref(false)
const fieldNotifyOnChange = ref(false)
const fieldOverview = ref(false)
const fieldEnumOptions = ref('')
const fieldAgeSource = ref('')
const fieldAgeMode = ref('now')
const fieldHasDefault = ref(false)
const fieldDefaultValue = ref('')
const fieldDefaultBool = ref(false)
const fieldDefaultToday = ref(false)
const fieldDefaultNumber = ref<number>(0)
const fieldPosition = ref(0)
const fieldKeepOnArchive = ref(false)
const saving = ref(false)

watch(modelValue, (open) => {
  if (!open) return
  const f = props.field
  if (f) {
    fieldName.value = f.name ?? ''
    fieldType.value = f.fieldType ?? FieldTypes.TEXT
    const cfg = parseFieldConfig(f.config)
    fieldRequired.value = !!cfg.required
    fieldReadonly.value = !!cfg.readonly
    fieldNotifyOnChange.value = !!cfg.notifyOnChange
    fieldOverview.value = !!cfg.overview
    fieldEnumOptions.value = ((cfg.options as string[]) ?? []).join('\n')
    fieldAgeSource.value = (cfg.sourceField as string) ?? ''
    fieldAgeMode.value = (cfg.ageMode as string) ?? 'now'
    fieldHasDefault.value = cfg.defaultValue !== undefined
    if (f.fieldType === FieldTypes.BOOLEAN) {
      fieldDefaultBool.value = cfg.defaultValue === true
    } else if (f.fieldType === FieldTypes.DATE) {
      fieldDefaultToday.value = cfg.defaultValue === '__TODAY__'
    } else if (f.fieldType === FieldTypes.NUMBER) {
      fieldDefaultNumber.value = typeof cfg.defaultValue === 'number' ? cfg.defaultValue : 0
    } else {
      fieldDefaultValue.value = typeof cfg.defaultValue === 'string' ? cfg.defaultValue : ''
    }
    fieldPosition.value = f.position
    fieldKeepOnArchive.value = f.keepOnArchive ?? false
  } else {
    fieldName.value = ''
    fieldType.value = FieldTypes.TEXT
    fieldRequired.value = false
    fieldReadonly.value = false
    fieldNotifyOnChange.value = false
    fieldOverview.value = false
    fieldEnumOptions.value = ''
    fieldAgeSource.value = ''
    fieldAgeMode.value = 'now'
    fieldHasDefault.value = false
    fieldDefaultValue.value = ''
    fieldDefaultBool.value = false
    fieldDefaultToday.value = false
    fieldKeepOnArchive.value = false
    fieldDefaultNumber.value = 0
    fieldPosition.value = 0
  }
})

function buildConfig(): string {
  const cfg: Record<string, unknown> = {}
  if (fieldRequired.value) cfg.required = true
  if (fieldReadonly.value) cfg.readonly = true
  if (fieldNotifyOnChange.value) cfg.notifyOnChange = true
  if (fieldOverview.value) cfg.overview = true
  if (fieldType.value === FieldTypes.ENUM && fieldEnumOptions.value.trim()) {
    cfg.options = fieldEnumOptions.value.split('\n').map(o => o.trim()).filter(o => o.length > 0)
  }
  if (fieldType.value === FieldTypes.AGE) {
    if (fieldAgeSource.value) cfg.sourceField = fieldAgeSource.value
    cfg.ageMode = fieldAgeMode.value
  }
  if (fieldHasDefault.value) {
    if (fieldType.value === FieldTypes.BOOLEAN) {
      cfg.defaultValue = fieldDefaultBool.value
    } else if (fieldType.value === FieldTypes.DATE) {
      cfg.defaultValue = fieldDefaultToday.value ? '__TODAY__' : ''
    } else if (fieldType.value === FieldTypes.NUMBER) {
      cfg.defaultValue = fieldDefaultNumber.value
    } else {
      cfg.defaultValue = fieldDefaultValue.value.trim()
    }
  }
  if (props.scope === 'GROUP' && props.groupId) {
    cfg.groupId = Number(props.groupId)
  }
  return JSON.stringify(cfg)
}

function submit() {
  saving.value = true
  emit('save', {
    name: fieldName.value,
    fieldType: fieldType.value,
    config: buildConfig(),
    position: fieldPosition.value,
    scope: props.scope,
    keepOnArchive: fieldKeepOnArchive.value,
  })
  saving.value = false
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ field ? t('membersConfig.editField') : t('membersConfig.addField') }}</SubHeader>
      <BasicFields v-model:name="fieldName" v-model:field-type="fieldType" :scope="scope"/>
      <EnumOptionsField v-if="fieldType === 'ENUM'" v-model="fieldEnumOptions"/>
      <AgeFields v-if="fieldType === 'AGE'" v-model:source="fieldAgeSource" v-model:mode="fieldAgeMode"
                 :date-fields="dateFields"/>
      <FieldDefaultValueSection
        v-model:has-default="fieldHasDefault"
        v-model:default-value="fieldDefaultValue"
        v-model:default-bool="fieldDefaultBool"
        v-model:default-today="fieldDefaultToday"
        v-model:default-number="fieldDefaultNumber"
        :toggle-label="t('membersConfig.fieldDefault')"
        :placeholder="t('membersConfig.fieldDefaultPlaceholder')"
        :date-hint="t('membersConfig.fieldDefaultDateHint')"
        :field-type="fieldType"
        :enum-options="fieldEnumOptions"
      />
      <BehaviorToggles
        v-model:required="fieldRequired"
        v-model:readonly="fieldReadonly"
        v-model:notify-on-change="fieldNotifyOnChange"
        v-model:overview="fieldOverview"
        v-model:keep-on-archive="fieldKeepOnArchive"
      />
      <PositionField v-model="fieldPosition"/>
      <ModalActions :saving="saving" :disabled="!fieldName" @cancel="modelValue = false" @submit="submit"/>
    </div>
  </Modal>
</template>
