/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { QuizCatalog } from '@/api/quiz'

defineProps<{
  catalogs: QuizCatalog[]
  selectedCatalogIds: Set<number>
}>()

defineEmits<{
  toggle: [id: number]
  start: []
}>()

const { t } = useI18n()
</script>

<template>
  <MutedText tag="p" size="base">{{ t('quiz.training.selectCatalogs') }}</MutedText>

  <MutedText v-if="catalogs.length === 0" tag="div" size="sm">
    {{ t('quiz.training.noCatalogs') }}
  </MutedText>

  <div data-onboarding="quiz.catalogs" class="grid grid-cols-1 sm:grid-cols-2 gap-3">
    <div
      v-for="catalog in catalogs"
      :key="catalog.id"
      class="rounded-lg border-2 p-4 cursor-pointer transition-all"
      :class="selectedCatalogIds.has(catalog.id) ? 'border-success bg-success/10' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary'"
      @click="$emit('toggle', catalog.id)"
    >
      <div class="flex items-center gap-2">
        <font-awesome-icon
          :icon="['fas', selectedCatalogIds.has(catalog.id) ? 'square-check' : 'square']"
          class="text-xl shrink-0"
          :class="selectedCatalogIds.has(catalog.id) ? 'text-success' : 'text-(--text-muted)'"
        />
        <div>
          <span class="font-medium">{{ catalog.name }}</span>
          <MutedText v-if="catalog.description" tag="p" class="mt-0.5">
            {{ catalog.description }}
          </MutedText>
        </div>
      </div>
    </div>
  </div>

  <div class="flex justify-end">
    <PrimaryButton data-onboarding="quiz.start" :disabled="selectedCatalogIds.size === 0" @click="$emit('start')">
      {{ t('quiz.training.start') }}
    </PrimaryButton>
  </div>
</template>
