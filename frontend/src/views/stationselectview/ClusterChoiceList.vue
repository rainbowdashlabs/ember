/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {Cluster} from '@/api/clusters'

defineProps<{
  clusters: readonly Cluster[]
}>()

defineEmits<{
  select: [clusterUid: string]
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="clusters.length > 0" class="space-y-3">
    <MutedText size="sm" tag="p">{{ t('stationSelect.clusterHint') }}</MutedText>
    <NeutralContainer
        v-for="cluster in clusters"
        :key="cluster.uid"
        class="flex items-center justify-between cursor-pointer hover:border-primary transition-colors"
        @click="$emit('select', cluster.uid)"
    >
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'sitemap']" class="h-5 w-5 text-(--text-muted)"/>
        <span class="font-medium text-lg">{{ cluster.name }}</span>
      </div>
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-(--text-muted)"/>
    </NeutralContainer>
  </div>
</template>
