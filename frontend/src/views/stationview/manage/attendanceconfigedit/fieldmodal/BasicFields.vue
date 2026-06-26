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

const FIELD_TYPE_KEYS = ['STRING', 'TIME', 'DATE', 'BOOLEAN', 'ENUM', 'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'] as const

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})

const {t, te} = useI18n()

const fieldTypeOptions = computed(() =>
    FIELD_TYPE_KEYS.map(value => ({value, label: t(`attendanceConfig.fieldTypeLabels.${value}`)})),
)

const description = computed(() => {
  const key = `attendanceConfig.fieldTypeDescriptions.${fieldType.value}`
  return te(key) ? t(key) : ''
})
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceConfig.fieldName') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('attendanceConfig.fieldNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceConfig.fieldType') }}</FieldLabel>
      <SelectInput v-model="fieldType">
        <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ ft.label }}</option>
      </SelectInput>
      <p class="text-xs text-(--text-muted)">{{ description }}</p>
    </div>
  </div>
</template>
