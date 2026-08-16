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
import MutedText from '@/components/typography/MutedText.vue'
import type {SharedCatalogEntry} from '@/api/quiz'

const props = defineProps<{
  shared: SharedCatalogEntry
  isMobile: boolean
}>()

const emit = defineEmits<{
  navigate: [shared: SharedCatalogEntry]
  copy: [catalogId: number]
}>()

const { t } = useI18n()

function open() {
  if (!props.shared.stationUid) return
  emit('navigate', props.shared)
}
</script>

<template>
  <NeutralContainer
    class="hover:border-primary transition-colors"
    :class="shared.stationUid ? 'cursor-pointer' : ''"
    @click="open"
  >
    <div v-if="isMobile" class="space-y-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-medium">{{ shared.name }}</span>
        <StationBadge :station-name="shared.stationName" />
      </div>
      <MutedText v-if="shared.description" tag="p">{{ shared.description }}</MutedText>
      <div class="flex items-center justify-end border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
        <IconButton
          :icon="['fas', 'copy']"
          :label="t('federation.copyToStation')"
          @click.stop="emit('copy', shared.id)"
        />
      </div>
    </div>

    <div v-else class="flex items-center justify-between gap-4">
      <div class="flex-1 min-w-0 space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ shared.name }}</span>
          <StationBadge :station-name="shared.stationName" />
        </div>
        <MutedText v-if="shared.description" tag="p" class="truncate">{{ shared.description }}</MutedText>
      </div>
      <div class="flex items-center gap-2 shrink-0" @click.stop>
        <MutedIconButton
          :icon="['fas', 'copy']"
          :label="t('federation.copyToStation')"
          @click="emit('copy', shared.id)"
        />
      </div>
    </div>
  </NeutralContainer>
</template>
