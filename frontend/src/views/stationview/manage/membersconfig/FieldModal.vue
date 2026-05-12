/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {ProfileField} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  modelValue: boolean
  field: ProfileField | null
  scope: string
  groupId?: string
  dateFields: ProfileField[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [data: { name: string; fieldType: string; config: string; position: number; scope: string }]
}>()

const fieldTypeOptions = [
  {value: 'text', label: 'Text'},
  {value: 'number', label: 'Zahl'},
  {value: 'date', label: 'Datum'},
  {value: 'boolean', label: 'Ja/Nein'},
  {value: 'enum', label: 'Auswahl'},
  {value: 'age', label: 'Alter (berechnet)'},
]

const fieldName = ref('')
const fieldType = ref('text')
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
const saving = ref(false)

function parseConfig(configStr: string | undefined): Record<string, unknown> {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

watch(() => props.modelValue, (open) => {
  if (!open) return
  const f = props.field
  if (f) {
    fieldName.value = f.name ?? ''
    fieldType.value = f.fieldType ?? 'text'
    const cfg = parseConfig(f.config)
    fieldRequired.value = !!cfg.required
    fieldReadonly.value = !!cfg.readonly
    fieldNotifyOnChange.value = !!cfg.notifyOnChange
    fieldOverview.value = !!cfg.overview
    fieldEnumOptions.value = ((cfg.options as string[]) ?? []).join('\n')
    fieldAgeSource.value = (cfg.sourceField as string) ?? ''
    fieldAgeMode.value = (cfg.ageMode as string) ?? 'now'
    fieldHasDefault.value = cfg.defaultValue !== undefined
    if (f.fieldType === 'boolean') {
      fieldDefaultBool.value = cfg.defaultValue === true
    } else if (f.fieldType === 'date') {
      fieldDefaultToday.value = cfg.defaultValue === '__TODAY__'
    } else if (f.fieldType === 'number') {
      fieldDefaultNumber.value = typeof cfg.defaultValue === 'number' ? cfg.defaultValue : 0
    } else {
      fieldDefaultValue.value = typeof cfg.defaultValue === 'string' ? cfg.defaultValue : ''
    }
    fieldPosition.value = f.position
  } else {
    fieldName.value = ''
    fieldType.value = 'text'
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
  if (fieldType.value === 'enum' && fieldEnumOptions.value.trim()) {
    cfg.options = fieldEnumOptions.value.split('\n').map(o => o.trim()).filter(o => o.length > 0)
  }
  if (fieldType.value === 'age') {
    if (fieldAgeSource.value) cfg.sourceField = fieldAgeSource.value
    cfg.ageMode = fieldAgeMode.value
  }
  if (fieldHasDefault.value) {
    if (fieldType.value === 'boolean') {
      cfg.defaultValue = fieldDefaultBool.value
    } else if (fieldType.value === 'date') {
      cfg.defaultValue = fieldDefaultToday.value ? '__TODAY__' : ''
    } else if (fieldType.value === 'number') {
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
  })
  saving.value = false
}
</script>

<template>
  <Modal :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <SectionHeader>{{ field ? t('membersConfig.editField') : t('membersConfig.addField') }}</SectionHeader>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('membersConfig.fieldName') }}</label>
        <TextInput v-model="fieldName" :placeholder="t('membersConfig.fieldNamePlaceholder')"/>
      </div>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('membersConfig.fieldType') }}</label>
        <SelectInput v-model="fieldType">
          <option v-for="ft in fieldTypeOptions.filter(o => o.value !== 'age' || scope === 'MEMBER')" :key="ft.value"
                  :value="ft.value">{{ ft.label }}
          </option>
        </SelectInput>
      </div>

      <div v-if="fieldType === 'enum'" class="space-y-1">
        <label class="block text-sm font-medium">{{ t('membersConfig.fieldEnumOptions') }}</label>
        <TextAreaInput v-model="fieldEnumOptions" :placeholder="t('membersConfig.fieldEnumOptionsPlaceholder')"/>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldEnumOptionsHint') }}</p>
      </div>

      <div v-if="fieldType === 'age'" class="space-y-3">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('membersConfig.fieldAgeSource') }}</label>
          <SelectInput v-model="fieldAgeSource">
            <option disabled value="">{{ t('membersConfig.fieldAgeSourcePlaceholder') }}</option>
            <option v-for="f in dateFields" :key="f.id" :value="f.name">{{ f.name }}</option>
          </SelectInput>
          <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldAgeSourceHint') }}</p>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('membersConfig.fieldAgeMode') }}</label>
          <SelectInput v-model="fieldAgeMode">
            <option value="now">{{ t('membersConfig.fieldAgeModeNow') }}</option>
            <option value="end_of_year">{{ t('membersConfig.fieldAgeModeEndOfYear') }}</option>
          </SelectInput>
        </div>
      </div>

      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('membersConfig.fieldDefault') }}</label>
          <ToggleInput v-model="fieldHasDefault"/>
        </div>
        <template v-if="fieldHasDefault">
          <template v-if="fieldType === 'boolean'">
            <ToggleInput v-model="fieldDefaultBool"/>
          </template>
          <template v-else-if="fieldType === 'date'">
            <ToggleInput v-model="fieldDefaultToday"/>
            <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldDefaultDateHint') }}</p>
          </template>
          <template v-else-if="fieldType === 'number'">
            <NumberInput v-model="fieldDefaultNumber"/>
          </template>
          <template v-else-if="fieldType === 'enum'">
            <SelectInput v-model="fieldDefaultValue">
              <option value="">{{ t('membersConfig.fieldDefaultPlaceholder') }}</option>
              <option v-for="opt in fieldEnumOptions.split('\n').map(o => o.trim()).filter(o => o)" :key="opt"
                      :value="opt">{{ opt }}
              </option>
            </SelectInput>
          </template>
          <template v-else>
            <TextInput v-model="fieldDefaultValue" :placeholder="t('membersConfig.fieldDefaultPlaceholder')"/>
          </template>
        </template>
      </div>

      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('membersConfig.fieldRequired') }}</label>
        <ToggleInput v-model="fieldRequired"/>
      </div>

      <div class="space-y-1">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('membersConfig.fieldReadonly') }}</label>
          <ToggleInput v-model="fieldReadonly"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldReadonlyHint') }}</p>
      </div>

      <div class="space-y-1">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('membersConfig.fieldNotifyOnChange') }}</label>
          <ToggleInput v-model="fieldNotifyOnChange"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldNotifyOnChangeHint') }}</p>
      </div>

      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('membersConfig.fieldOverview') }}</label>
        <ToggleInput v-model="fieldOverview"/>
      </div>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('membersConfig.fieldPosition') }}</label>
        <NumberInput v-model="fieldPosition"/>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !fieldName" @click="submit">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
