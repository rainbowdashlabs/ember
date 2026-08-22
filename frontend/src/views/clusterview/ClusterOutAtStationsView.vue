/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {clusterInventory} from '@/api'
import type {ClusterItem} from '@/api/clusterInventory'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * Where the association's gear is when it is not in the association's own store.
 *
 * <p>A custody query rather than an inventory one: the same rows the store shows, filtered to the
 * ones that have gone somewhere, and grouped by the station holding them. An item on its way reads as
 * in transit at both ends, which is the point of splitting the journey in two.
 */
const {t} = useI18n()

const items = ref<ClusterItem[]>([])

const {loading, error} = useAsyncLoader(async () => {
  items.value = await clusterInventory.listItems()
})

/** Everything that is not resting at the association. */
const away = computed(() => items.value.filter(item => item.custody !== 'WITH_OWNER'))

const byStation = computed(() => {
  const groups = new Map<string, ClusterItem[]>()
  for (const item of away.value) {
    const key = item.stationName ?? t('clusterInventory.inTransit')
    const bucket = groups.get(key)
    if (bucket) bucket.push(item)
    else groups.set(key, [item])
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b))
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory-out.subtitle')" :title="t('pages.cluster-inventory-out.title')">
    <div class="space-y-6">
      <InventoryTabs/>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="away.length === 0">{{ t('clusterInventory.outEmpty') }}</EmptyState>

        <NeutralContainer v-for="[station, stationItems] in byStation" :key="station" class="space-y-3">
          <div class="flex items-center justify-between gap-3">
            <SectionHeader>{{ station }}</SectionHeader>
            <SecondaryBadge>{{ t('clusterInventory.itemCount', {count: stationItems.length}) }}</SecondaryBadge>
          </div>

          <div class="space-y-1">
            <div v-for="item in stationItems" :key="item.id"
                 class="flex flex-wrap items-center justify-between gap-2 border-b border-(--border) py-1 last:border-0">
              <span class="font-medium">{{ item.name }}</span>
              <div class="flex items-center gap-2">
                <MutedText v-if="item.holderName" size="sm">{{ item.holderName }}</MutedText>
                <SecondaryBadge>{{ t(`clusterInventory.custody.${item.custody}`) }}</SecondaryBadge>
              </div>
            </div>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
