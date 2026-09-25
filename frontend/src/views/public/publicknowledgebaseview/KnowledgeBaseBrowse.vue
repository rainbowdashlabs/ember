/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedText from '@/components/typography/MutedText.vue'
import KbItemGrid from '@/views/stationview/knowledge/knowledgebaseview/KbItemGrid.vue'
import KbItemList from '@/views/stationview/knowledge/knowledgebaseview/KbItemList.vue'
import type {KbFolder} from '@/api/knowledgeBase'
import type {KbItem} from '@/views/stationview/knowledge/knowledgebaseview/useKbItems'

defineProps<{
  loading: boolean
  currentFolder: KbFolder | null
  items: KbItem[]
  /** Tiles or one entry to a line, the same choice the station's own members have. */
  viewMode: 'grid' | 'list'
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <Spinner v-if="loading"/>
    <template v-else>
      <MutedText v-if="currentFolder?.description" tag="p" size="sm">
        {{ currentFolder.description }}
      </MutedText>

      <KbItemGrid v-if="items.length > 0 && viewMode === 'grid'" :items="items"/>
      <KbItemList v-else-if="items.length > 0" :items="items"/>

      <p v-else class="text-[var(--text-muted)] text-center py-8">
        {{ t('publicKb.empty') }}
      </p>
    </template>
  </div>
</template>
