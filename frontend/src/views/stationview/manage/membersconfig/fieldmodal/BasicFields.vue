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
import {FIELD_TYPE_ORDER, fieldTypeLabel} from '../fieldTypes'

const props = defineProps<{
  scope: string
  /** False once another field of the station already is the birth date. */
  birthDateAvailable: boolean
}>()

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})

const {t} = useI18n()

const availableOptions = computed(() => FIELD_TYPE_ORDER
    .filter((type) => {
      if (type === FieldTypes.AGE) return props.scope === 'MEMBER'
      if (type === FieldTypes.BIRTH_DATE) return props.birthDateAvailable
      return true
    })
    .map(type => ({value: type, label: fieldTypeLabel(t, type)})))
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
