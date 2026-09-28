/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MenuSettingRow from './MenuSettingRow.vue'
import type {QuestionDraft} from '../types'

/** How far a rating goes and what it is drawn with. */
defineProps<{
  question: QuestionDraft
}>()

const {t} = useI18n()
</script>

<template>
  <MenuSettingRow :label="t('forms.rating.scale')">
    <NumberInput v-model="(question.config.scale as number)" class="w-20"/>
  </MenuSettingRow>
  <MenuSettingRow :label="t('forms.rating.icon')">
    <SelectInput v-model="(question.config.icon as string)" class="w-36">
      <option value="STAR">{{ t('forms.rating.iconStar') }}</option>
      <option value="NUMBER">{{ t('forms.rating.iconNumber') }}</option>
      <option value="HEART">{{ t('forms.rating.iconHeart') }}</option>
      <option value="THUMB_UP">{{ t('forms.rating.iconThumbUp') }}</option>
    </SelectInput>
  </MenuSettingRow>
</template>
