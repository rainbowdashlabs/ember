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
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import AgeFields from './fieldmodal/AgeFields.vue'
import FieldDefaultValueSection from '@/components/input/FieldDefaultValueSection.vue'
import BirthDateFields from './fieldmodal/BirthDateFields.vue'
import BehaviorToggles from './fieldmodal/BehaviorToggles.vue'
import ModalActions from './fieldmodal/ModalActions.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {
    DATE_FIELD_TYPES, FieldTypes, parseFieldConfig,
    type ProfileField, type ProfileFieldConfig, type ProfileFieldRequest,
} from '@/api/profileFields'
import {FieldWidths} from '@/components/profilefields/fieldLayout'

/**
 * The question itself. Who is asked it is not here: a question is written once and put to as many
 * audiences as it is meant for, and naming one while writing it is what used to make the same
 * question be written twice.
 */
const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  field: ProfileField | null
  dateFields: ProfileField[]
  /** The field that already is the station's birth date, if any. */
  birthDateField: ProfileField | null
}>()

/** The birth date carries a date like any other, so it offers the same configuration. */
function isDateType(type: string | undefined): boolean {
  return DATE_FIELD_TYPES.includes(type ?? '')
}

const birthDateAvailable = computed(() =>
    !props.birthDateField || props.birthDateField.id === props.field?.id)

/**
 * A heading and a spacer hold no answer, so everything that describes an answer is beside the point
 * for them. A spacer keeps its width all the same, which is the only thing it is for.
 */
const holdsValue = computed(() =>
    fieldType.value !== FieldTypes.SECTION && fieldType.value !== FieldTypes.SPACER)

const isSpacer = computed(() => fieldType.value === FieldTypes.SPACER)

const emit = defineEmits<{
  save: [data: ProfileFieldRequest]
}>()

const fieldName = ref('')
const fieldType = ref<string>(FieldTypes.TEXT)
const fieldDescription = ref('')
const fieldRequired = ref(false)
const fieldReadonly = ref(false)
const fieldNotifyOnChange = ref(false)
const fieldOverview = ref(false)
const fieldEnumOptions = ref<string[]>([])
const fieldAgeSource = ref('')
const fieldAgeMode = ref('now')
const fieldHasDefault = ref(false)
const fieldDefaultValue = ref('')
const fieldDefaultBool = ref(false)
const fieldDefaultToday = ref(false)
const fieldDefaultNumber = ref<number>(0)
const fieldKeepOnArchive = ref(false)
const fieldShowAge = ref(true)
const fieldWidth = ref<string>(FieldWidths.FULL)
const saving = ref(false)

/**
 * Whether the answer is worked out from another one rather than given.
 *
 * <p>Nobody writes it, so everything about writing it, expecting it, locking it, reporting a change
 * to it or starting it off with a value, is a setting with nothing to act on.
 */
const isCalculated = computed(() => fieldType.value === FieldTypes.AGE)

watch(modelValue, (open) => {
  if (!open) return
  const f = props.field
  if (f) {
    fieldName.value = f.name ?? ''
    fieldType.value = f.fieldType ?? FieldTypes.TEXT
    const cfg = parseFieldConfig(f.config)
    fieldDescription.value = typeof cfg.description === 'string' ? cfg.description : ''
    fieldRequired.value = !!f.required
    fieldReadonly.value = !!f.readonly
    fieldNotifyOnChange.value = !!cfg.notifyOnChange
    fieldOverview.value = !!cfg.overview
    fieldEnumOptions.value = [...((cfg.options as string[]) ?? [])]
    fieldAgeSource.value = (cfg.sourceField as string) ?? ''
    fieldAgeMode.value = (cfg.ageMode as string) ?? 'now'
    fieldHasDefault.value = cfg.defaultValue !== undefined
    if (f.fieldType === FieldTypes.BOOLEAN) {
      fieldDefaultBool.value = cfg.defaultValue === true
    } else if (isDateType(f.fieldType)) {
      fieldDefaultToday.value = cfg.defaultValue === '__TODAY__'
    } else if (f.fieldType === FieldTypes.NUMBER) {
      fieldDefaultNumber.value = typeof cfg.defaultValue === 'number' ? cfg.defaultValue : 0
    } else {
      fieldDefaultValue.value = typeof cfg.defaultValue === 'string' ? cfg.defaultValue : ''
    }
    fieldKeepOnArchive.value = f.keepOnArchive ?? false
    fieldShowAge.value = cfg.showAge !== false
    fieldWidth.value = f.width ?? FieldWidths.FULL
  } else {
    fieldName.value = ''
    fieldType.value = FieldTypes.TEXT
    fieldDescription.value = ''
    fieldRequired.value = false
    fieldReadonly.value = false
    fieldNotifyOnChange.value = false
    fieldOverview.value = false
    fieldEnumOptions.value = []
    fieldAgeSource.value = ''
    fieldAgeMode.value = 'now'
    fieldHasDefault.value = false
    fieldDefaultValue.value = ''
    fieldDefaultBool.value = false
    fieldDefaultToday.value = false
    fieldKeepOnArchive.value = false
    fieldShowAge.value = true
    fieldWidth.value = FieldWidths.FULL
    fieldDefaultNumber.value = 0
  }
})

/**
 * The settings as they are written down, which is only the ones that were chosen.
 *
 * <p>A birth date's age is the exception in reverse: it is recorded only where it was switched off,
 * so every birth date written before there was a switch keeps showing the age it always showed.
 */
function buildConfig(): ProfileFieldConfig {
  const cfg: ProfileFieldConfig = {}
  if (fieldDescription.value.trim()) cfg.description = fieldDescription.value.trim()
  if (fieldNotifyOnChange.value) cfg.notifyOnChange = true
  if (fieldOverview.value) cfg.overview = true
  if (fieldType.value === FieldTypes.ENUM && fieldEnumOptions.value.length > 0) {
    cfg.options = [...fieldEnumOptions.value]
  }
  if (fieldType.value === FieldTypes.AGE) {
    if (fieldAgeSource.value) cfg.sourceField = fieldAgeSource.value
    cfg.ageMode = fieldAgeMode.value
  }
  if (fieldType.value === FieldTypes.BIRTH_DATE && !fieldShowAge.value) cfg.showAge = false
  if (fieldHasDefault.value) {
    if (fieldType.value === FieldTypes.BOOLEAN) {
      cfg.defaultValue = fieldDefaultBool.value
    } else if (isDateType(fieldType.value)) {
      cfg.defaultValue = fieldDefaultToday.value ? '__TODAY__' : ''
    } else if (fieldType.value === FieldTypes.NUMBER) {
      cfg.defaultValue = fieldDefaultNumber.value
    } else {
      cfg.defaultValue = fieldDefaultValue.value.trim()
    }
  }
  return cfg
}

function submit() {
  saving.value = true
  emit('save', {
    name: fieldName.value,
    fieldType: fieldType.value,
    config: buildConfig(),
    required: fieldRequired.value,
    readonly: fieldReadonly.value,
    width: fieldWidth.value === FieldWidths.FULL ? null : fieldWidth.value,
    keepOnArchive: fieldKeepOnArchive.value,
  })
  saving.value = false
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ field ? t('membersConfig.editField') : t('membersConfig.addField') }}</SubHeader>
      <BasicFields v-model:name="fieldName" v-model:field-type="fieldType"
                   v-model:description="fieldDescription"
                   :named="!isSpacer"
                   :birth-date-available="birthDateAvailable"/>
      <template v-if="holdsValue">
        <QuestionOptionsEditor
            v-if="fieldType === 'ENUM'"
            v-model="fieldEnumOptions"
            :label="t('membersConfig.fieldEnumOptions')"
        />
        <AgeFields v-if="fieldType === 'AGE'" v-model:source="fieldAgeSource" v-model:mode="fieldAgeMode"
                   :date-fields="dateFields"/>
        <FieldDefaultValueSection
          v-if="!isCalculated"
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
        <BirthDateFields v-if="fieldType === 'BIRTH_DATE'" v-model:show-age="fieldShowAge"/>
        <BehaviorToggles
          v-model:required="fieldRequired"
          v-model:readonly="fieldReadonly"
          v-model:notify-on-change="fieldNotifyOnChange"
          v-model:overview="fieldOverview"
          v-model:keep-on-archive="fieldKeepOnArchive"
          :calculated="isCalculated"
        />
        <WidthField v-model="fieldWidth"/>
      </template>
      <WidthField v-if="isSpacer" v-model="fieldWidth"/>
      <ModalActions
          :saving="saving"
          :disabled="!fieldName && !isSpacer"
          @cancel="modelValue = false"
          @submit="submit"/>
    </div>
  </Modal>
</template>
