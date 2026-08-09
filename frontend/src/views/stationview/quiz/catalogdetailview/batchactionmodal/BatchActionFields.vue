/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {QuizCategory} from '@/api/quiz'

const {t} = useI18n()

const autoPoints = defineModel<boolean>('autoPoints', {required: true})
const points = defineModel<number>('points', {required: true})
const pointsPerCorrect = defineModel<number>('pointsPerCorrect', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})

defineProps<{
  action: string
  categories: QuizCategory[]
}>()
</script>

<template>
  <template v-if="action === 'autoPoints'">
    <div class="flex items-center gap-2">
      <ToggleInput v-model="autoPoints"/>
      <span class="text-sm">{{ t('quiz.batch.autoPointsLabel') }}</span>
    </div>
  </template>
  <template v-else-if="action === 'setPoints'">
    <div class="flex items-center gap-2">
      <FieldLabel>{{ t('quiz.questions.points') }}</FieldLabel>
      <NumberInput v-model="points" class="w-20"/>
    </div>
  </template>
  <template v-else-if="action === 'pointsPerCorrect'">
    <div class="flex items-center gap-2">
      <FieldLabel>{{ t('quiz.questions.config.pointsPerCorrect') }}</FieldLabel>
      <DecimalInput v-model="pointsPerCorrect" step="0.5" class="w-20"/>
    </div>
  </template>
  <template v-else-if="action === 'setCategory'">
    <SelectInput v-model="categoryId">
      <option value="">{{ t('quiz.questions.noCategory') }}</option>
      <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
    </SelectInput>
  </template>
</template>
