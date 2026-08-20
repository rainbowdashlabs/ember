/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { ExchangeRequestEntry } from '@/api/exchanges'
import { ItemCustody, type MyInventoryItem } from '@/api/inventory'
import { formatDate } from '@/util/format'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  item: MyInventoryItem
  exchange?: ExchangeRequestEntry | null
  showExchangeButton?: boolean
  showUnassignButton?: boolean
  showReassignButton?: boolean
  showInventoryName?: boolean
}>(), {
  exchange: null,
  showExchangeButton: false,
  showUnassignButton: false,
  showReassignButton: false,
  showInventoryName: false,
})

const emit = defineEmits<{
  requestExchange: [item: MyInventoryItem]
  unassign: [item: MyInventoryItem]
  reassign: [item: MyInventoryItem]
}>()

/**
 * Whether the item is on the list without being in the member's hands: taken back for an exchange,
 * in the post, or already at the station waiting to be handed over. It stays listed so the member
 * can watch what is happening to it, and it is dimmed so they can see it is not theirs to wear.
 */
const awayFromMember = computed(() =>
    !!props.item.movementStep && props.item.custody !== ItemCustody.WITH_MEMBER)
</script>

<template>
  <NeutralContainer :class="[props.item.lostAt ? 'opacity-60 border-error' : '', awayFromMember ? 'opacity-70' : '']">
    <div class="flex items-start justify-between gap-2">
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
        <template v-if="props.item.movementStep">
          <InfoBadge class="mt-1">{{ props.item.movementStep }}</InfoBadge>
          <div class="text-xs text-(--text-muted)">
            {{ props.item.movementIncoming ? t('profile.movementIncoming') : t('profile.movementOutgoing') }}
          </div>
        </template>
        <InfoBadge v-else-if="props.exchange" class="mt-1">
          {{ t('exchanges.status.' + props.exchange.status) }}
        </InfoBadge>
      </div>
      <div class="flex items-center gap-1">
        <MutedIconButton
            v-if="props.showExchangeButton && !props.exchange && !props.item.movementStep"
            :icon="['fas', 'rotate']"
            :label="t('profile.requestExchange')"
            @click="emit('requestExchange', props.item)"
        />
        <MutedIconButton
            v-if="props.showReassignButton"
            :icon="['fas', 'arrow-right-arrow-left']"
            :label="t('memberDetail.reassignItem')"
            @click="emit('reassign', props.item)"
        />
        <MutedIconButton
            v-if="props.showUnassignButton"
            :icon="['fas', 'xmark']"
            :label="t('memberDetail.unassignItem')"
            hover="error"
            @click="emit('unassign', props.item)"
        />
      </div>
    </div>
  </NeutralContainer>
</template>
