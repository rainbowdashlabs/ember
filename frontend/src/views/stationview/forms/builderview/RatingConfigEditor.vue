/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type { QuestionDraft } from './types'

defineProps<{
  question: QuestionDraft
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex gap-4 items-center">
    <label class="text-sm">{{ t('forms.rating.scale') }}</label>
    <NumberInput v-model="(question.config.scale as number)" class="w-20" />
    <SelectInput v-model="(question.config.icon as string)" class="w-40">
      <option value="STAR">{{ t('forms.rating.iconStar') }}</option>
      <option value="NUMBER">{{ t('forms.rating.iconNumber') }}</option>
      <option value="HEART">{{ t('forms.rating.iconHeart') }}</option>
      <option value="THUMB_UP">{{ t('forms.rating.iconThumbUp') }}</option>
    </SelectInput>
  </div>
</template>
