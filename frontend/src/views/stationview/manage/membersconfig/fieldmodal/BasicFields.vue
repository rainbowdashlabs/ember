/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {FieldTypes} from '@/api/profileFields'

const props = defineProps<{
  scope: string
  /** False once another field of the station already is the birth date. */
  birthDateAvailable: boolean
}>()

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})

const {t} = useI18n()

const fieldTypeOptions = [
  {value: FieldTypes.TEXT, label: 'Text'},
  {value: FieldTypes.NUMBER, label: 'Zahl'},
  {value: FieldTypes.DATE, label: 'Datum'},
  {value: FieldTypes.BIRTH_DATE, label: 'Geburtsdatum'},
  {value: FieldTypes.BOOLEAN, label: 'Ja/Nein'},
  {value: FieldTypes.ENUM, label: 'Auswahl'},
  {value: FieldTypes.AGE, label: 'Alter (berechnet)'},
]

const availableOptions = computed(() => fieldTypeOptions.filter((option) => {
  if (option.value === FieldTypes.AGE) return props.scope === 'MEMBER'
  if (option.value === FieldTypes.BIRTH_DATE) return props.birthDateAvailable
  return true
}))
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldName') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('membersConfig.fieldNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldType') }}</FieldLabel>
      <SelectInput v-model="fieldType">
        <option v-for="ft in availableOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
      </SelectInput>
    </div>
  </div>
</template>
