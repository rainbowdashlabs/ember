/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelfCheckEntryCard from './SelfCheckEntryCard.vue'
import type {RequiredInventoryItem} from '@/api/inventory'
import type {ExchangeCauseName, SelfCheckDraft, SelfCheckEntry} from '@/composables/useSelfCheck'
import type {SelfCheckAnswerName} from '@/api/selfChecks'

/** One kind of gear, with everything the member holds of it and every place that is still empty. */
defineProps<{
  req: RequiredInventoryItem
  entries: SelfCheckEntry[]
  draftOf: (key: string) => SelfCheckDraft
  sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
  raisedFor: Map<number, string[]>
  refusedFor: Map<string, string>
  readOnly: boolean
}>()

const emit = defineEmits<{
  setAnswer: [key: string, answer: SelfCheckAnswerName]
  setNote: [key: string, note: string]
  setTypedInternalId: [key: string, typed: string]
  setSizeId: [key: string, sizeId: string]
  reportLost: [entry: SelfCheckEntry]
  requestExchange: [entry: SelfCheckEntry, cause: ExchangeCauseName]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ req.inventoryName }}</SubHeader>
      <MutedText size="sm" class="shrink-0">
        {{ req.assignedQuantity }} / {{ req.requiredQuantity }}
        <span v-if="req.inExchangeQuantity > 0">
          {{ t('inventory.check.inExchange', {count: req.inExchangeQuantity}) }}
        </span>
      </MutedText>
    </div>

    <SelfCheckEntryCard
        v-for="entry in entries"
        :key="entry.key"
        :entry="entry"
        :draft="draftOf(entry.key)"
        :size-label="entry.type === 'piece' ? sizeLabel(req, entry.item.sizeId) : ''"
        :raised="entry.type === 'piece' ? (raisedFor.get(entry.item.id) ?? []) : []"
        :refused-reason="refusedFor.get(entry.key) ?? ''"
        :read-only="readOnly"
        @set-answer="(k, a) => emit('setAnswer', k, a)"
        @set-note="(k, n) => emit('setNote', k, n)"
        @set-typed-internal-id="(k, v) => emit('setTypedInternalId', k, v)"
        @set-size-id="(k, v) => emit('setSizeId', k, v)"
        @report-lost="e => emit('reportLost', e)"
        @request-exchange="(e, cause) => emit('requestExchange', e, cause)"
    />
  </NeutralContainer>
</template>
