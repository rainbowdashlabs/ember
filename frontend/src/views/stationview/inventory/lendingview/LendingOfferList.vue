/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {AvailableInventoryEntry} from '@/api/generated/schema'

/**
 * What the partners offer, one tile per inventory with how many pieces are free and how far away
 * the partner is. Pressing the request button hands the entry back to the screen, which knows where
 * a request is written.
 */
defineProps<{
  items: AvailableInventoryEntry[]
}>()

const emit = defineEmits<{
  request: [item: AvailableInventoryEntry]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col gap-2">
    <SubHeader class="mb-1">{{ t('lending.availableItems') }}</SubHeader>
    <NeutralContainer v-for="item in items" :key="`${item.stationId}-${item.inventoryId}`">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ item.inventoryName }}</span>
            <StationBadge :station-name="item.stationName"/>
          </div>
          <span class="text-xs text-[var(--text-muted)]">
            {{ item.availableCount }} {{ t('lending.available') }}
            <template v-if="item.distanceKm != null">
              · {{ t('lendingDistance.distanceKm', {distance: item.distanceKm.toFixed(1)}) }}
            </template>
            <template v-else>
              · {{ t('lendingDistance.distanceUnknown') }}
            </template>
          </span>
        </div>
        <PrimaryButton
            :icon="['fas', 'paper-plane']"
            data-testid="lending-offer-request"
            @click="emit('request', item)"
        >
          {{ t('lending.requestItem') }}
        </PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
