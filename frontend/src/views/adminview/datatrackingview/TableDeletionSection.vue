/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ColumnEntry, DeletionStrategy, GdprDeletionContext, TrackingStatus} from '@/api/generated/schema'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'
import DeletionStrategyEditor from './DeletionStrategyEditor.vue'

defineProps<{
  statuses: TrackingStatus[]
  strategies: readonly string[]
  columns: ColumnEntry[]
}>()

const deletion = defineModel<GdprDeletionContext>('deletion', {required: true})

const emit = defineEmits<{
  addStrategy: []
  removeStrategy: [index: number]
}>()

const {t} = useI18n()

function replaceStrategy(index: number, strategy: DeletionStrategy) {
  deletion.value.strategies?.splice(index, 1, strategy)
}
</script>

<template>
  <section>
    <div class="flex items-center justify-between">
      <SubHeader class="!text-base">{{ t('adminDataTracking.gdprDeletion') }}</SubHeader>
      <StatusBadge :status="deletion.status"/>
    </div>
    <div class="space-y-2 mt-2">
      <StatusReasonFields
          v-model:status="deletion.status"
          v-model:reason="deletion.reason"
          :statuses="statuses"
      />
      <div>
        <div class="flex items-center justify-between mb-1">
          <span class="text-xs text-(--text-muted)">{{ t('adminDataTracking.detail.strategies') }}</span>
          <SecondaryButton :icon="['fas', 'plus']" @click="emit('addStrategy')">
            {{ t('adminDataTracking.detail.addStrategy') }}
          </SecondaryButton>
        </div>
        <div
            v-if="!deletion.strategies?.length"
            class="text-xs text-(--text-muted) italic py-2"
        >
          {{ t('adminDataTracking.detail.noStrategies') }}
        </div>
        <DeletionStrategyEditor
            v-for="(strategy, idx) in deletion.strategies ?? []"
            :key="idx"
            :strategy="strategy"
            :columns="columns"
            :strategies="strategies"
            @update:strategy="changed => replaceStrategy(idx, changed)"
            @remove="emit('removeStrategy', idx)"
        />
      </div>
    </div>
  </section>
</template>
