/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import type {FieldType} from '@/api/generated/schema'

const name = defineModel<string>('name', {required: true})
const fieldType = defineModel<FieldType>('fieldType', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <LabelledField :label="t('attendanceConfig.fieldName')">
      <TextInput v-model="name" :placeholder="t('attendanceConfig.fieldNamePlaceholder')"/>
    </LabelledField>
    <LabelledField :label="t('attendanceConfig.fieldType')">
      <FieldTypePicker v-model="fieldType" :types="OfferedFieldTypes.ATTENDANCE" describe/>
    </LabelledField>
  </div>
</template>
