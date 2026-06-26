/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import GenPreviewCard from './GenPreviewCard.vue'

export interface GenPreview {
  title: string
  config: string
  quizQuestionType: string
  categoryId: number | null
  accepted: boolean
}

const props = defineProps<{
  previews: GenPreview[]
  generating: boolean
  regeneratingIndex: number | null
  progressTotal: number
  error: string
}>()

const emit = defineEmits<{
  toggle: [index: number]
  regenerate: [index: number]
  save: []
  back: []
}>()

const {t} = useI18n()

const acceptedCount = computed(() => props.previews.filter(q => q.accepted).length)
</script>

<template>
  <div class="space-y-3">
    <div v-if="props.generating" class="space-y-2">
      <div class="flex justify-between text-xs text-(--text-muted)">
        <span>{{ t('quiz.ai.generating') }}</span>
        <span>{{ props.previews.length }} / {{ props.progressTotal }}</span>
      </div>
      <div class="w-full h-2 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
        <div class="h-full rounded-full bg-primary transition-all duration-300"
             :style="{ width: `${props.progressTotal > 0 ? (props.previews.length / props.progressTotal) * 100 : 0}%` }"/>
      </div>
    </div>

    <p v-if="!props.generating" class="text-sm text-(--text-muted)">{{ t('quiz.ai.reviewHint') }}</p>
    <div class="space-y-2">
      <GenPreviewCard
          v-for="(q, idx) in props.previews"
          :key="idx"
          :preview="q"
          :regenerating="props.regeneratingIndex === idx"
          @toggle="emit('toggle', idx)"
          @regenerate="emit('regenerate', idx)"
      />
    </div>
    <p v-if="props.error" class="text-xs text-error">{{ props.error }}</p>
    <div class="flex items-center justify-between pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <div class="flex gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="emit('back')">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-(--text-muted)">{{ acceptedCount }} / {{ props.previews.length }}</span>
        <PrimaryButton :disabled="props.generating || acceptedCount === 0" @click="emit('save')">
          <Spinner v-if="props.generating" size="sm" class="mr-1"/>
          <font-awesome-icon v-else :icon="['fas', 'check']" class="mr-1"/>
          {{ t('quiz.ai.acceptSelected') }}
        </PrimaryButton>
      </div>
    </div>
  </div>
</template>
