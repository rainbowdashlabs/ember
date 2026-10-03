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
import AgeFields from './fieldmodal/AgeFields.vue'
import QuestionSettingsEditor from '@/components/input/questionsettings/QuestionSettingsEditor.vue'
import BirthDateFields from './fieldmodal/BirthDateFields.vue'
import ExpiryDateFields from './fieldmodal/ExpiryDateFields.vue'
import BehaviorToggles from './fieldmodal/BehaviorToggles.vue'
import ModalActions from './fieldmodal/ModalActions.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {
    ageSourceOf, parseFieldConfig,
    type EditableField, type FieldSettings, type EditableFieldRequest,
} from '@/api/profileFields'
import GenderPronounsEditor from './fieldmodal/GenderPronounsEditor.vue'
import {PRESETS, storedPronouns, type GenderPronouns} from './fieldmodal/genderPronouns'
import {holdsValue as typeHoldsValue, isChoiceType, isDateType} from '@/api/fieldTypes'
import {FieldType} from '@/api/generated/schema'
import {
    defaultAsText, TODAY, typedDefault,
    type QuestionSetting, type QuestionSettingsModel,
} from '@/components/input/questionsettings/questionSettings'
import {FieldWidths} from '@/components/profilefields/fieldLayout'
import {expiryConfigOf, expirySettingsOf, type ExpirySettings} from '@/util/expiry'

/**
 * The question itself. Who is asked it is not here: a question is written once and put to as many
 * audiences as it is meant for, and naming one while writing it is what used to make the same
 * question be written twice.
 *
 * <p>The answers of a choice and the starting value are the shared settings every feature edits the
 * same way. A date starts as today or not at all, because a fixed day goes stale the day after it is
 * set; a number is whole, because the profile offers no step.
 */
const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  field: EditableField | null
  dateFields: EditableField[]
  /** The field that already is the station's birth date, if any. */
  birthDateField: EditableField | null
  /** The field that already is the owner's gender, if any. */
  genderField: EditableField | null
}>()

/** The types this field cannot take, because another field already is the one of its kind. */
const unavailableTypes = computed<FieldType[]>(() => [
  ...takenBy(props.birthDateField, FieldType.BIRTH_DATE),
  ...takenBy(props.genderField, FieldType.GENDER),
])

function takenBy(other: EditableField | null, type: FieldType): FieldType[] {
  return other && other.id !== props.field?.id ? [type] : []
}

const emit = defineEmits<{
  save: [data: EditableFieldRequest]
}>()

const fieldName = ref('')
const fieldType = ref<FieldType>(FieldType.TEXT)
const fieldDescription = ref('')
const fieldRequired = ref(false)
const fieldReadonly = ref(false)
const fieldNotifyOnChange = ref(false)
const fieldOverview = ref(false)
const fieldSettings = ref<QuestionSettingsModel>({})
const fieldAgeSourceId = ref<number | null>(null)
const fieldAgeMode = ref('now')
const fieldKeepOnArchive = ref(false)
const fieldShowAge = ref(true)
const fieldExpiry = ref<ExpirySettings>(expirySettingsOf({}))
const fieldWidth = ref<string>(FieldWidths.FULL)
const fieldPronouns = ref<GenderPronouns>({})
const saving = ref(false)

/**
 * Whether anything describing an answer is beside the point. A heading and a spacer hold no answer;
 * a spacer keeps its width all the same, which is the only thing it is for. An age holds none of its
 * own either, but is still asked, so it keeps the settings about being asked.
 */
const holdsValue = computed(() => fieldType.value !== FieldType.SECTION && fieldType.value !== FieldType.SPACER)

const isSpacer = computed(() => fieldType.value === FieldType.SPACER)

/**
 * Whether the answer is worked out from another one rather than given.
 *
 * <p>Nobody writes it, so everything about writing it, expecting it, locking it, reporting a change
 * to it or starting it off with a value, is a setting with nothing to act on.
 */
const isCalculated = computed(() => fieldType.value === FieldType.AGE)

/**
 * What of the shared settings this question offers. A certificate that runs out on the day it is
 * entered is never what anybody means, so an expiry date starts empty.
 */
const offers = computed<QuestionSetting[]>(() =>
    typeHoldsValue(fieldType.value) && fieldType.value !== FieldType.EXPIRY_DATE
        ? ['options', 'default', 'todayDefault']
        : ['options'])

/** A date's only starting value is today; an empty one written before that was a choice reads as none. */
function startingValue(type: FieldType, stored: unknown): string | null {
  const text = defaultAsText(stored)
  if (isDateType(type) && text !== TODAY) return null
  return text
}

watch(modelValue, (open) => {
  if (!open) return
  const f = props.field
  const cfg = parseFieldConfig(f?.config)
  fieldName.value = f?.name ?? ''
  fieldType.value = f?.fieldType ?? FieldType.TEXT
  fieldDescription.value = typeof cfg.description === 'string' ? cfg.description : ''
  fieldRequired.value = !!f?.required
  fieldReadonly.value = !!f?.readonly
  fieldNotifyOnChange.value = !!cfg.notifyOnChange
  fieldOverview.value = !!cfg.overview
  fieldSettings.value = {
    options: [...(cfg.options ?? [])],
    defaultValue: startingValue(fieldType.value, cfg.defaultValue),
  }
  fieldAgeSourceId.value = ageSourceOf(cfg, props.dateFields)?.id ?? null
  fieldAgeMode.value = (cfg.ageMode as string) ?? 'now'
  fieldKeepOnArchive.value = f?.keepOnArchive ?? false
  fieldShowAge.value = cfg.showAge !== false
  fieldExpiry.value = expirySettingsOf(cfg)
  fieldWidth.value = f?.width ?? FieldWidths.FULL
  fieldPronouns.value = structuredClone(cfg.pronouns ?? {})
})

/**
 * A new gender field starts with the two predefined answers and their pronouns. A choice field turned
 * into one keeps its answers, and each is mapped in the pronoun editor.
 */
watch(fieldType, (type) => {
  if (type !== FieldType.GENDER || (fieldSettings.value.options ?? []).length > 0) return
  const male = t('membersConfig.gender.male')
  const female = t('membersConfig.gender.female')
  fieldSettings.value = {...fieldSettings.value, options: [male, female]}
  fieldPronouns.value = {[male]: structuredClone(PRESETS.MALE), [female]: structuredClone(PRESETS.FEMALE)}
})

/**
 * The settings as they are written down, which is only the ones that were chosen.
 *
 * <p>A birth date's age is the exception in reverse: it is recorded only where it was switched off,
 * so every birth date written before there was a switch keeps showing the age it always showed.
 */
function buildConfig(): FieldSettings {
  const cfg: FieldSettings = {}
  if (fieldDescription.value.trim()) cfg.description = fieldDescription.value.trim()
  if (fieldNotifyOnChange.value) cfg.notifyOnChange = true
  if (fieldOverview.value) cfg.overview = true
  const options = fieldSettings.value.options ?? []
  if (isChoiceType(fieldType.value) && options.length > 0) cfg.options = [...options]
  if (fieldType.value === FieldType.GENDER) cfg.pronouns = storedPronouns(fieldPronouns.value, options)
  if (fieldType.value === FieldType.AGE) {
    const source = props.dateFields.find(f => f.id === fieldAgeSourceId.value)
    if (source) {
      cfg.sourceFieldId = source.id
      cfg.sourceField = source.name
    }
    cfg.ageMode = fieldAgeMode.value
  }
  if (fieldType.value === FieldType.BIRTH_DATE && !fieldShowAge.value) cfg.showAge = false
  if (fieldType.value === FieldType.EXPIRY_DATE) Object.assign(cfg, expiryConfigOf(fieldExpiry.value))
  if (offers.value.includes('default')) {
    const starting = typedDefault(fieldType.value, fieldSettings.value.defaultValue)
    if (starting !== undefined) cfg.defaultValue = starting
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
                   :unavailable="unavailableTypes"/>
      <template v-if="holdsValue">
        <AgeFields v-if="isCalculated" v-model:source-id="fieldAgeSourceId" v-model:mode="fieldAgeMode"
                   :date-fields="dateFields"/>
        <QuestionSettingsEditor v-model="fieldSettings" :field-type="fieldType" :offers="offers"/>
        <GenderPronounsEditor v-if="fieldType === FieldType.GENDER" v-model="fieldPronouns"
                              :answers="fieldSettings.options ?? []"/>
        <BirthDateFields v-if="fieldType === FieldType.BIRTH_DATE" v-model:show-age="fieldShowAge"/>
        <ExpiryDateFields v-if="fieldType === FieldType.EXPIRY_DATE" v-model="fieldExpiry"/>
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
