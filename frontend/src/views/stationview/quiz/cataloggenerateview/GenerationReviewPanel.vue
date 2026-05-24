/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

export interface GenPreview {
  title: string
  config: string
  questionType: string
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

const { t } = useI18n()

const acceptedCount = computed(() => props.previews.filter(q => q.accepted).length)

function parseGenConfig(configStr: string): Record<string, unknown> {
  try { return JSON.parse(configStr) } catch { return {} }
}
</script>

<template>
  <div class="space-y-3">
    <!-- Progress bar -->
    <div v-if="props.generating" class="space-y-2">
      <div class="flex justify-between text-xs text-(--text-muted)">
        <span>{{ t('quiz.ai.generating') }}</span>
        <span>{{ props.previews.length }} / {{ props.progressTotal }}</span>
      </div>
      <div class="w-full h-2 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
        <div class="h-full rounded-full bg-primary transition-all duration-300" :style="{ width: `${props.progressTotal > 0 ? (props.previews.length / props.progressTotal) * 100 : 0}%` }" />
      </div>
    </div>

    <p v-if="!props.generating" class="text-sm text-(--text-muted)">{{ t('quiz.ai.reviewHint') }}</p>
    <div class="space-y-2">
      <div
        v-for="(q, idx) in props.previews"
        :key="idx"
        class="rounded-lg border transition-all"
        :class="q.accepted
          ? 'border-success bg-success/5'
          : 'border-bg-light-accent dark:border-bg-dark-accent opacity-50'"
      >
        <div class="flex items-start gap-3 p-3 cursor-pointer" @click="emit('toggle', idx)">
          <font-awesome-icon
            :icon="['fas', q.accepted ? 'square-check' : 'square']"
            class="text-lg mt-0.5 shrink-0"
            :class="q.accepted ? 'text-success' : 'text-(--text-muted)'"
          />
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <p class="font-medium text-sm">{{ q.title }}</p>
              <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
            </div>
            <!-- MC: show options with checkmarks -->
            <template v-if="q.questionType === 'MULTIPLE_CHOICE'">
              <div class="mt-1 space-y-0.5">
                <div v-for="(opt, oi) in (parseGenConfig(q.config).options as { text: string; correct: boolean }[] || [])" :key="oi" class="flex items-center gap-1 text-xs">
                  <font-awesome-icon :icon="['fas', opt.correct ? 'square-check' : 'square']" :class="opt.correct ? 'text-success' : 'text-(--text-muted)'" class="text-[10px]" />
                  <span :class="opt.correct ? 'font-medium' : 'text-(--text-muted)'">{{ opt.text }}</span>
                </div>
              </div>
            </template>
            <!-- TF: show correct answer -->
            <template v-else-if="q.questionType === 'TRUE_FALSE'">
              <p class="text-xs mt-1" :class="(parseGenConfig(q.config).correctAnswer as boolean) ? 'text-success' : 'text-error'">
                {{ (parseGenConfig(q.config).correctAnswer as boolean) ? t('quiz.trueLabel') : t('quiz.falseLabel') }}
              </p>
            </template>
            <!-- Free answer: show possible answers -->
            <template v-else-if="q.questionType === 'FREE_ANSWER'">
              <p v-for="(ans, ai2) in (parseGenConfig(q.config).answers as string[] || [])" :key="ai2" class="text-xs text-(--text-muted) mt-0.5">
                {{ ai2 + 1 }}. {{ ans }}
              </p>
            </template>
            <!-- Fill blank: show answers -->
            <template v-else-if="q.questionType === 'FILL_IN_THE_BLANK'">
              <p class="text-xs text-success mt-1">{{ (parseGenConfig(q.config).answers as string[] || []).join(', ') }}</p>
              <p v-if="(parseGenConfig(q.config).distractors as string[] || []).length > 0" class="text-xs text-error mt-0.5">
                {{ (parseGenConfig(q.config).distractors as string[] || []).join(', ') }}
              </p>
            </template>
            <!-- Connect: show pairs -->
            <template v-else-if="q.questionType === 'CONNECT'">
              <div class="mt-1 space-y-0.5">
                <p v-for="(pair, pi) in (parseGenConfig(q.config).pairs as { left: string; right: string }[] || [])" :key="pi" class="text-xs text-(--text-muted)">
                  {{ pair.left }} &rarr; {{ pair.right }}
                </p>
              </div>
            </template>
            <!-- Ordering: show items numbered -->
            <template v-else-if="q.questionType === 'ORDERING'">
              <div class="mt-1 space-y-0.5">
                <p v-for="(item, ii) in (parseGenConfig(q.config).items as string[] || [])" :key="ii" class="text-xs text-(--text-muted)">
                  {{ ii + 1 }}. {{ item }}
                </p>
              </div>
            </template>
          </div>
        </div>
        <!-- Regenerate button -->
        <div class="flex justify-end px-3 pb-2" @click.stop>
          <SecondaryButton
            :disabled="props.regeneratingIndex === idx"
            @click="emit('regenerate', idx)"
          >
            <Spinner v-if="props.regeneratingIndex === idx" size="sm" />
            <font-awesome-icon v-else :icon="['fas', 'rotate']" />
            {{ t('quiz.ai.regenerate') }}
          </SecondaryButton>
        </div>
      </div>
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
          <Spinner v-if="props.generating" size="sm" class="mr-1" />
          <font-awesome-icon v-else :icon="['fas', 'check']" class="mr-1" />
          {{ t('quiz.ai.acceptSelected') }}
        </PrimaryButton>
      </div>
    </div>
  </div>
</template>
