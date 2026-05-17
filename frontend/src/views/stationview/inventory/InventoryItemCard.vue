/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { ExchangeRequestEntry } from '@/api/types'
import type { MyInventoryItem } from '@/api/inventory'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  item: MyInventoryItem
  exchange?: ExchangeRequestEntry | null
  showExchangeButton?: boolean
  showInventoryName?: boolean
}>(), {
  exchange: null,
  showExchangeButton: false,
  showInventoryName: false,
})

const emit = defineEmits<{
  requestExchange: [item: MyInventoryItem]
}>()
</script>

<template>
  <NeutralContainer :class="props.item.lostAt ? 'opacity-60 border-error' : ''">
    <div class="flex items-start justify-between gap-2">
      <div>
        <div class="font-medium text-sm">
          {{ props.item.name }}
          <span class="font-normal text-(--text-muted)">{{ props.item.sizeName ?? t('common.unisize') }}</span>
        </div>
        <div v-if="props.showInventoryName" class="text-xs text-(--text-muted)">{{ props.item.inventoryName }}</div>
        <div v-if="props.item.internalId" class="text-xs text-(--text-muted)">{{ props.item.internalId }}</div>
        <ErrorBadge v-if="props.item.lostAt" class="mt-1">
          {{ t('profile.lostSince') }} {{ new Date(props.item.lostAt).toLocaleDateString('de-DE') }}
        </ErrorBadge>
        <InfoBadge v-if="props.exchange" class="mt-1">
          {{ t('exchanges.status.' + props.exchange.status) }}
        </InfoBadge>
      </div>
      <IconButton
          v-if="props.showExchangeButton && !props.exchange"
          :icon="['fas', 'rotate']"
          :label="t('profile.requestExchange')"
          class="text-(--text-muted) hover:text-primary"
          @click="emit('requestExchange', props.item)"
      />
    </div>
  </NeutralContainer>
</template>
