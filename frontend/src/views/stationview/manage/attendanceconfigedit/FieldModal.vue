/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {AttendanceTemplateField, MemberGroup} from '@/api/types'

const props = defineProps<{
  field: AttendanceTemplateField | null
  availableGroups: MemberGroup[]
  saving: boolean
  fieldCount: number
}>()

const emit = defineEmits<{
  save: [data: { name: string; fieldType: string; config: string; position: number }]
  close: []
}>()

const {t} = useI18n()

const open = defineModel<boolean>({default: false})

const fieldTypeOptions = [
  {value: 'string', label: 'Text'},
  {value: 'time', label: 'Uhrzeit'},
  {value: 'date', label: 'Datum'},
  {value: 'boolean', label: 'Ja/Nein'},
  {value: 'enum', label: 'Auswahl'},
  {value: 'member', label: 'Mitglied'},
  {value: 'member_list', label: 'Mitgliederliste'},
  {value: 'member_of_group', label: 'Mitglied aus Gruppe'},
  {value: 'member_list_of_group', label: 'Mitgliederliste aus Gruppe'},
]

const fieldTypeDescriptions: Record<string, string> = {
  string: 'Ein einfaches Textfeld zur freien Eingabe.',
  time: 'Eingabefeld für eine Uhrzeit.',
  date: 'Eingabefeld für ein Datum.',
  boolean: 'Ein Ja/Nein-Schalter.',
  enum: 'Auswahl aus vordefinierten Optionen.',
  member: 'Auswahl eines einzelnen Mitglieds aus allen Mitgliedern der Wache.',
  member_list: 'Auswahl mehrerer Mitglieder aus allen Mitgliedern der Wache.',
  member_of_group: 'Auswahl eines einzelnen Mitglieds aus einer bestimmten Gruppe.',
  member_list_of_group: 'Auswahl mehrerer Mitglieder aus einer bestimmten Gruppe.',
}

const fieldName = ref('')
const fieldType = ref('string')
const fieldConfigGroupId = ref('')
const fieldConfigRequired = ref(false)
const fieldConfigAutoAttend = ref(false)
const fieldEnumOptions = ref('')
const fieldHasDefault = ref(false)
const fieldDefaultValue = ref('')
const fieldDefaultBool = ref(false)
const fieldDefaultToday = ref(false)
const fieldPosition = ref(0)

const isEditing = computed(() => props.field !== null)

const fieldTypeDescription = computed(() => fieldTypeDescriptions[fieldType.value] ?? '')

function fieldTypeNeedsGroup(type: string): boolean {
  return ['member_of_group', 'member_list_of_group'].includes(type)
}

function fieldTypeCanAutoAttend(type: string): boolean {
  return ['member', 'member_list', 'member_of_group', 'member_list_of_group'].includes(type)
}

function fieldTypeCanHaveDefault(type: string): boolean {
  return ['string', 'time', 'date', 'boolean', 'enum'].includes(type)
}

function parseConfig(configStr: string | undefined): Record<string, unknown> {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

function buildConfig(): string {
  const cfg: Record<string, unknown> = {}
  if (fieldConfigRequired.value) cfg.required = true
  if (fieldTypeNeedsGroup(fieldType.value) && fieldConfigGroupId.value) {
    cfg.groupId = Number(fieldConfigGroupId.value)
  }
  if (fieldTypeCanAutoAttend(fieldType.value) && fieldConfigAutoAttend.value) {
    cfg.autoAttend = true
  }
  if (fieldType.value === 'enum' && fieldEnumOptions.value.trim()) {
    cfg.options = fieldEnumOptions.value.split('\n').map(o => o.trim()).filter(o => o.length > 0)
  }
  if (fieldHasDefault.value && fieldTypeCanHaveDefault(fieldType.value)) {
    if (fieldType.value === 'boolean') {
      cfg.defaultValue = fieldDefaultBool.value
    } else if (fieldType.value === 'date') {
      cfg.defaultValue = fieldDefaultToday.value ? '__TODAY__' : ''
    } else {
      cfg.defaultValue = fieldDefaultValue.value.trim()
    }
  }
  return JSON.stringify(cfg)
}

watch([open, () => props.field], () => {
  if (!open.value) return
  if (props.field) {
    fieldName.value = props.field.name ?? ''
    fieldType.value = props.field.fieldType ?? 'string'
    const cfg = parseConfig(props.field.config)
    fieldConfigGroupId.value = cfg.groupId ? String(cfg.groupId) : ''
    fieldConfigRequired.value = !!cfg.required
    fieldConfigAutoAttend.value = !!cfg.autoAttend
    fieldEnumOptions.value = ((cfg.options as string[]) ?? []).join('\n')
    fieldHasDefault.value = cfg.defaultValue !== undefined
    if (props.field.fieldType === 'boolean') {
      fieldDefaultBool.value = cfg.defaultValue === true
    } else if (props.field.fieldType === 'date') {
      fieldDefaultToday.value = cfg.defaultValue === '__TODAY__'
    } else {
      fieldDefaultValue.value = typeof cfg.defaultValue === 'string' ? cfg.defaultValue : ''
    }
    fieldPosition.value = props.field.position
  } else {
    fieldName.value = ''
    fieldType.value = 'string'
    fieldConfigGroupId.value = ''
    fieldConfigRequired.value = false
    fieldConfigAutoAttend.value = false
    fieldEnumOptions.value = ''
    fieldHasDefault.value = false
    fieldDefaultValue.value = ''
    fieldDefaultBool.value = false
    fieldDefaultToday.value = false
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
      <SectionHeader>{{ isEditing ? t('attendanceConfig.editField') : t('attendanceConfig.addField') }}</SectionHeader>

      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.fieldName') }}</FieldLabel>
        <TextInput v-model="fieldName" :placeholder="t('attendanceConfig.fieldNamePlaceholder')"/>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.fieldType') }}</FieldLabel>
        <SelectInput v-model="fieldType">
          <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
        </SelectInput>
        <p class="text-xs text-(--text-muted)">{{ fieldTypeDescription }}</p>
      </div>

      <!-- Group selector for group-based types -->
      <div v-if="fieldTypeNeedsGroup(fieldType)" class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.fieldGroup') }}</FieldLabel>
        <SelectInput v-model="fieldConfigGroupId">
          <option disabled value="">{{ t('attendanceConfig.fieldGroupPlaceholder') }}</option>
          <option v-for="group in availableGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
        </SelectInput>
      </div>

      <!-- Enum options -->
      <div v-if="fieldType === 'enum'" class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.fieldEnumOptions') }}</FieldLabel>
        <TextInput v-model="fieldEnumOptions" :placeholder="t('attendanceConfig.fieldEnumOptionsPlaceholder')"/>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldEnumOptionsHint') }}</p>
      </div>

      <!-- Default value -->
      <div v-if="fieldTypeCanHaveDefault(fieldType)" class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('attendanceConfig.fieldHasDefault') }}</label>
          <ToggleInput v-model="fieldHasDefault"/>
        </div>
        <template v-if="fieldHasDefault">
          <template v-if="fieldType === 'boolean'">
            <ToggleInput v-model="fieldDefaultBool"/>
          </template>
          <template v-else-if="fieldType === 'date'">
            <ToggleInput v-model="fieldDefaultToday"/>
            <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldDefaultDateHint') }}</p>
          </template>
          <template v-else-if="fieldType === 'enum'">
            <SelectInput v-model="fieldDefaultValue">
              <option value="">—</option>
              <option v-for="opt in fieldEnumOptions.split('\n').map(o => o.trim()).filter(o => o)" :key="opt"
                      :value="opt">{{ opt }}
              </option>
            </SelectInput>
          </template>
          <template v-else>
            <TextInput v-model="fieldDefaultValue" :placeholder="t('attendanceConfig.fieldDefaultValuePlaceholder')"/>
          </template>
          <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldDefaultValueHint') }}</p>
        </template>
      </div>

      <!-- Required toggle -->
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('attendanceConfig.fieldRequired') }}</label>
        <ToggleInput v-model="fieldConfigRequired"/>
      </div>

      <!-- Auto-attend toggle for member-type fields -->
      <div v-if="fieldTypeCanAutoAttend(fieldType)" class="space-y-1">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('attendanceConfig.fieldAutoAttend') }}</label>
          <ToggleInput v-model="fieldConfigAutoAttend"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldAutoAttendHint') }}</p>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.fieldPosition') }}</FieldLabel>
        <NumberInput v-model="fieldPosition"/>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('attendanceConfig.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !fieldName" @click="handleSave">
          {{ saving ? t('common.loading') : t('attendanceConfig.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
