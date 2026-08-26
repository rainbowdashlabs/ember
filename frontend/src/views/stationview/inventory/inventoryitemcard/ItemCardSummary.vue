/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {ExchangeRequestEntry} from '@/api/exchanges'
import type {MyInventoryItem} from '@/api/inventory'
import {formatDate} from '@/util/format'

/** What one piece of gear says about itself on a member's list: what it is, and where it stands. */
const props = defineProps<{
  item: MyInventoryItem
  exchange?: ExchangeRequestEntry | null
  showInventoryName?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <div class="font-medium text-sm">
      {{ props.item.name }}
      <SizeBadge v-if="props.item.sizeName" :lost="!!props.item.lostAt">{{ props.item.sizeName }}</SizeBadge>
    </div>
    <div v-if="props.showInventoryName" class="text-xs text-(--text-muted)">{{ props.item.inventoryName }}</div>
    <div v-if="props.item.internalId" class="text-xs text-(--text-muted)">{{ props.item.internalId }}</div>
    <ErrorBadge v-if="props.item.lostAt" class="mt-1">
      {{ t('profile.lostSince') }} {{ formatDate(props.item.lostAt) }}
    </ErrorBadge>
    <div v-if="props.item.lostAt && props.item.lostNote" class="mt-1 text-xs text-(--text-muted)"
         data-testid="item-lost-note">
      {{ props.item.lostNote }}
      <span v-if="props.item.lostNoteBy?.name">({{ props.item.lostNoteBy.name }})</span>
    </div>
    <InfoBadge v-if="props.item.movementStep" class="mt-1">{{ props.item.movementStep }}</InfoBadge>
    <InfoBadge v-else-if="props.exchange" class="mt-1">
      {{ t('exchanges.status.' + props.exchange.status) }}
    </InfoBadge>
  </div>
</template>
