/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import {FieldWidths} from './fieldLayout'

/** How much of a row the field takes, which is what lets short fields stand beside each other. */
const width = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center gap-1">
      <FieldLabel>{{ t('membersConfig.fieldWidth') }}</FieldLabel>
      <HintIcon :text="t('membersConfig.fieldWidthHint')"/>
    </div>
    <SelectInput v-model="width" class="w-full" data-testid="field-width">
      <option :value="FieldWidths.FULL">{{ t('membersConfig.widthFull') }}</option>
      <option :value="FieldWidths.HALF">{{ t('membersConfig.widthHalf') }}</option>
      <option :value="FieldWidths.THIRD">{{ t('membersConfig.widthThird') }}</option>
    </SelectInput>
  </div>
</template>
