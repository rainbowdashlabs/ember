/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import type {ClusterItem} from '@/api/clusterInventory'

defineProps<{
  item: ClusterItem
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center justify-between gap-3">
    <div class="min-w-0">
      <p class="font-medium truncate">{{ item.name }}</p>
      <p class="text-sm text-(--text-muted) truncate">
        {{ item.internalId }}
        <template v-if="item.stationName"> · {{ item.stationName }}</template>
        <template v-if="item.holderName"> · {{ item.holderName }}</template>
      </p>
    </div>
    <div class="flex items-center gap-2">
      <PrimaryBadge v-if="item.sizeLabel">{{ item.sizeLabel }}</PrimaryBadge>
      <SecondaryBadge>{{ t(`clusterInventory.custody.${item.custody}`) }}</SecondaryBadge>
    </div>
  </NeutralContainer>
</template>
