/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { QuizCatalog } from '@/api/quiz'

defineProps<{
  catalog: QuizCatalog
  isMobile: boolean
}>()

const emit = defineEmits<{
  navigate: [catalog: QuizCatalog]
  exportCatalog: [catalog: QuizCatalog]
  confirmDelete: [catalog: QuizCatalog]
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer
    class="cursor-pointer hover:border-primary transition-colors"
    @click="emit('navigate', catalog)"
  >
    <div v-if="isMobile" class="space-y-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-medium">{{ catalog.name }}</span>
        <SuccessBadge v-if="catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
      </div>
      <MutedText v-if="catalog.description" tag="p">{{ catalog.description }}</MutedText>
      <div class="flex items-center justify-between border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
        <MutedText>{{ t('quiz.catalogs.questionCount') }}</MutedText>
        <div class="flex items-center gap-2" @click.stop>
          <MutedIconButton :icon="['fas', 'file-export']" :label="t('quiz.catalogs.export')" @click="emit('exportCatalog', catalog)" />
          <DeleteButton @click="emit('confirmDelete', catalog)" />
        </div>
      </div>
    </div>

    <div v-else class="flex items-center justify-between gap-4">
      <div class="flex-1 min-w-0 space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ catalog.name }}</span>
          <SuccessBadge v-if="catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
        </div>
        <MutedText v-if="catalog.description" tag="p" class="truncate">{{ catalog.description }}</MutedText>
      </div>
      <div class="flex items-center gap-2 shrink-0" @click.stop>
        <MutedIconButton :icon="['fas', 'file-export']" :label="t('quiz.catalogs.export')" @click="emit('exportCatalog', catalog)" />
        <DeleteButton @click="emit('confirmDelete', catalog)" />
      </div>
    </div>
  </NeutralContainer>
</template>
