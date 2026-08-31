/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {AvailableInventoryEntry} from '@/api/lending'

defineProps<{
  offers: AvailableInventoryEntry[]
  /** Why an empty answer is empty, which the browse call says and the screen repeats. */
  emptyReason: string | null
}>()

const emit = defineEmits<{
  pick: [offer: AvailableInventoryEntry]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3" data-testid="partner-offers">
    <SubHeader>{{ t('lendingCollect.offersTitle') }}</SubHeader>

    <p v-if="offers.length === 0" class="text-sm text-(--text-muted)" data-testid="offers-empty">
      {{ emptyReason === 'NOTHING_SHARED' ? t('lendingCollect.nothingShared') : t('lendingCollect.nothingFree') }}
    </p>

    <div
        v-for="offer in offers"
        :key="`${offer.stationId}-${offer.inventoryId}-${offer.artId ?? 'all'}`"
        class="flex flex-wrap items-center gap-2 px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50"
        data-testid="partner-offer"
    >
      <span class="text-sm">{{ offer.artName ?? offer.inventoryName }}</span>
      <InfoBadge>{{ t('lendingCollect.freeCount', {count: offer.availableCount}) }}</InfoBadge>
      <span class="text-xs text-(--text-muted)">{{ offer.stationName }}</span>
      <SecondaryButton data-testid="offer-add" @click="emit('pick', offer)">
        {{ t('lendingCollect.add') }}
      </SecondaryButton>
    </div>
  </div>
</template>
