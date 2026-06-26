/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'

const props = defineProps<{
  totalPoints: number
  maxPoints: number
  canReview: boolean
  grading: boolean
  graded: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'finish'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div class="text-lg font-semibold">
        {{ t('quiz.evaluate.total') }}: {{ props.totalPoints }} / {{ props.maxPoints }} {{ t('quiz.points') }}
      </div>
      <div class="flex gap-3">
        <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
        <SuccessButton v-if="props.canReview" :disabled="props.grading || props.graded" @click="emit('finish')">
          <Spinner v-if="props.grading" size="sm" />
          <template v-else>{{ t('quiz.evaluate.finishGrading') }}</template>
        </SuccessButton>
      </div>
    </div>
  </NeutralContainer>
</template>
