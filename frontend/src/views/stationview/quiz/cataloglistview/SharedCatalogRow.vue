/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {QuizCatalog, SharedCatalogEntry} from '@/api/quiz'

defineProps<{
  shared: SharedCatalogEntry
  isMobile: boolean
}>()

const emit = defineEmits<{
  navigate: [catalog: QuizCatalog]
  copy: [catalogId: number]
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer
    class="cursor-pointer hover:border-primary transition-colors"
    @click="emit('navigate', shared.catalog)"
  >
    <div v-if="isMobile" class="space-y-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-medium">{{ shared.catalog.name }}</span>
        <StationBadge :station-name="shared.stationName" />
        <SuccessBadge v-if="shared.catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
      </div>
      <MutedText v-if="shared.catalog.description" tag="p">{{ shared.catalog.description }}</MutedText>
      <div class="flex items-center justify-end border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
        <IconButton
          :icon="['fas', 'copy']"
          :label="t('federation.copyToStation')"
          @click.stop="emit('copy', shared.catalog.id)"
        />
      </div>
    </div>

    <div v-else class="flex items-center justify-between gap-4">
      <div class="flex-1 min-w-0 space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ shared.catalog.name }}</span>
          <StationBadge :station-name="shared.stationName" />
          <SuccessBadge v-if="shared.catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
        </div>
        <MutedText v-if="shared.catalog.description" tag="p" class="truncate">{{ shared.catalog.description }}</MutedText>
      </div>
      <div class="flex items-center gap-2 shrink-0" @click.stop>
        <MutedIconButton
          :icon="['fas', 'copy']"
          :label="t('federation.copyToStation')"
          @click="emit('copy', shared.catalog.id)"
        />
      </div>
    </div>
  </NeutralContainer>
</template>
