/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import LabelledField from '@/components/input/LabelledField.vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {EditableField} from '@/api/profileFields'

defineProps<{
  dateFields: EditableField[]
}>()

/**
 * Which question the age is worked out from, held by its identifier rather than by its name.
 *
 * <p>A name is what somebody writes on a question and changes their mind about, and the age was
 * pointed at one: renaming the birth date left every age blank, with nothing on either screen
 * showing why. An identifier outlives what the question is called.
 */
const sourceId = defineModel<number | null>('sourceId', {required: true})
const mode = defineModel<string>('mode', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <LabelledField :label="t('membersConfig.fieldAgeSource')" :help="t('membersConfig.fieldAgeSourceHint')" help-as-hint>
      <template #default="{describedBy}">
        <SelectInput v-model="sourceId" :aria-describedby="describedBy">
          <option :value="null" disabled>{{ t('membersConfig.fieldAgeSourcePlaceholder') }}</option>
          <option v-for="f in dateFields" :key="f.id" :value="f.id">{{ f.name }}</option>
        </SelectInput>
      </template>
    </LabelledField>
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldAgeMode') }}</FieldLabel>
      <SelectInput v-model="mode">
        <option value="now">{{ t('membersConfig.fieldAgeModeNow') }}</option>
        <option value="end_of_year">{{ t('membersConfig.fieldAgeModeEndOfYear') }}</option>
      </SelectInput>
    </div>
  </div>
</template>
