/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {ProcurementResponse} from '@/api/generated/schema'
import ProcurementEntryRow from './ProcurementEntryRow.vue'

/**
 * The orders of a station, the open ones first.
 *
 * <p>What still has to be ordered or handed over is what the page is opened for. Fulfilled orders stay
 * as a record, most recent first, folded away below so they do not push the open ones down.
 */
const props = defineProps<{
  entries: ProcurementResponse[]
  canManageProcurement: boolean
}>()

const emit = defineEmits<{
  fulfill: [id: number]
  fromStock: [entry: ProcurementResponse]
  delete: [id: number]
}>()

const {t} = useI18n()

const openEntries = computed(() => props.entries.filter(entry => !entry.fulfilledAt))

const doneEntries = computed(() => props.entries
    .filter(entry => entry.fulfilledAt)
    .slice()
    .sort((a, b) => (b.fulfilledAt ?? '').localeCompare(a.fulfilledAt ?? '')))
</script>

<template>
  <div class="space-y-3" data-testid="procurement-open">
    <MutedText v-if="openEntries.length === 0" tag="p" size="sm">{{ t('procurement.noOpen') }}</MutedText>
    <ProcurementEntryRow
      v-for="entry in openEntries"
      :key="entry.id"
      :entry="entry"
      :can-manage-procurement="props.canManageProcurement"
      @fulfill="emit('fulfill', $event)"
      @from-stock="emit('fromStock', $event)"
      @delete="emit('delete', $event)"
    />
  </div>

  <details v-if="doneEntries.length > 0" class="mt-6" data-testid="procurement-done">
    <summary class="cursor-pointer text-sm text-(--text-muted) hover:text-(--text) transition-colors">
      {{ t('procurement.doneSummary', {count: doneEntries.length}) }}
    </summary>
    <div class="mt-3 space-y-3">
      <ProcurementEntryRow
        v-for="entry in doneEntries"
        :key="entry.id"
        :entry="entry"
        :can-manage-procurement="props.canManageProcurement"
        @fulfill="emit('fulfill', $event)"
        @from-stock="emit('fromStock', $event)"
        @delete="emit('delete', $event)"
      />
    </div>
  </details>
</template>
