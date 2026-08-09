/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {FieldTypes} from '@/api/profileFields'

defineProps<{
  scope: string
}>()

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})

const {t} = useI18n()

const fieldTypeOptions = [
  {value: FieldTypes.TEXT, label: 'Text'},
  {value: FieldTypes.NUMBER, label: 'Zahl'},
  {value: FieldTypes.DATE, label: 'Datum'},
  {value: FieldTypes.BOOLEAN, label: 'Ja/Nein'},
  {value: FieldTypes.ENUM, label: 'Auswahl'},
  {value: FieldTypes.AGE, label: 'Alter (berechnet)'},
]
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
        <option v-for="ft in fieldTypeOptions.filter(o => o.value !== 'AGE' || scope === 'MEMBER')" :key="ft.value"
                :value="ft.value">{{ ft.label }}
        </option>
      </SelectInput>
    </div>
  </div>
</template>
