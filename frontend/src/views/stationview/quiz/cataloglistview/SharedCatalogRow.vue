/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
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
  copy: [catalogId: number]
}>()

const { t } = useI18n()

/**
 * The partner's catalogue, where the partner is known. An entry that names no station is one
 * nothing can be opened from, so it stays the plain card it always was.
 */
const catalogPage = computed(() => props.shared.stationUid
    ? {name: 'federated-quiz-catalog', params: {stationUid: props.shared.stationUid, catalogId: props.shared.id}}
    : null)
</script>

<template>
  <RowLink :to="catalogPage">
    <NeutralContainer
      class="hover:border-primary transition-colors"
      :class="shared.stationUid ? 'cursor-pointer' : ''"
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
            @click="emit('copy', shared.id)"
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
        <div class="flex items-center gap-2 shrink-0">
          <MutedIconButton
            :icon="['fas', 'copy']"
            :label="t('federation.copyToStation')"
            @click="emit('copy', shared.id)"
          />
        </div>
      </div>
    </NeutralContainer>
  </RowLink>
</template>
