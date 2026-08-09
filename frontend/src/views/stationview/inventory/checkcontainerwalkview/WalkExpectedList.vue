/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import WalkExpectedRow from './WalkExpectedRow.vue'
import type {ExpectedRow} from './types'

defineProps<{
  rows: ExpectedRow[]
  emptyMessage: string
}>()

const emit = defineEmits<{
  confirm: [row: ExpectedRow]
  missing: [row: ExpectedRow]
  lost: [row: ExpectedRow]
  reset: [row: ExpectedRow]
}>()

const {t} = useI18n()
</script>

<template>
  <SectionHeader>{{ t('inventory.checkContainer.expected') }}</SectionHeader>
  <NeutralContainer class="mb-4">
    <EmptyState v-if="rows.length === 0" :message="emptyMessage" />
    <ul v-else class="divide-y divide-(--bg-accent)">
      <WalkExpectedRow
          v-for="row in rows"
          :key="row.item.id"
          :row="row"
          @confirm="emit('confirm', row)"
          @missing="emit('missing', row)"
          @lost="emit('lost', row)"
          @reset="emit('reset', row)"
      />
    </ul>
  </NeutralContainer>
</template>
