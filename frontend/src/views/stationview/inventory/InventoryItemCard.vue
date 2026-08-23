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
import ItemCardSummary from './inventoryitemcard/ItemCardSummary.vue'
import type { ExchangeRequestEntry } from '@/api/exchanges'
import { ItemCustody, type MyInventoryItem } from '@/api/inventory'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  item: MyInventoryItem
  exchange?: ExchangeRequestEntry | null
  showExchangeButton?: boolean
  showUnassignButton?: boolean
  showReassignButton?: boolean
  showInventoryName?: boolean
  /** Whether whoever holds this may say they cannot find it. */
  showLostButton?: boolean
}>(), {
  exchange: null,
  showExchangeButton: false,
  showUnassignButton: false,
  showReassignButton: false,
  showInventoryName: false,
  showLostButton: false,
})

const emit = defineEmits<{
  requestExchange: [item: MyInventoryItem]
  unassign: [item: MyInventoryItem]
  reassign: [item: MyInventoryItem]
  reportLost: [item: MyInventoryItem]
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
      <ItemCardSummary
          :item="props.item"
          :exchange="props.exchange"
          :show-inventory-name="props.showInventoryName"
      />
      <div class="flex items-center gap-1">
        <MutedIconButton
            v-if="props.showExchangeButton && !props.exchange && !props.item.movementStep"
            :icon="['fas', 'rotate']"
            :label="t('profile.requestExchange')"
            @click="emit('requestExchange', props.item)"
        />
        <MutedIconButton
            v-if="props.showLostButton && !props.item.lostAt && !props.item.movementStep"
            :icon="['fas', 'triangle-exclamation']"
            :label="t('profile.reportLost')"
            data-testid="item-report-lost"
            hover="error"
            @click="emit('reportLost', props.item)"
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
