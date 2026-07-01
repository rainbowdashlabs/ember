/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ColumnEntry, GdprDeletionContext, TrackingStatusName} from '@/api/dataTracking'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'
import DeletionStrategyEditor from './DeletionStrategyEditor.vue'

defineProps<{
  deletion: GdprDeletionContext
  statuses: TrackingStatusName[]
  strategies: readonly string[]
  columns: ColumnEntry[]
}>()

const emit = defineEmits<{
  addStrategy: []
  removeStrategy: [index: number]
}>()

const {t} = useI18n()
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
            v-for="(s, idx) in deletion.strategies ?? []"
            :key="idx"
            :strategy="s"
            :columns="columns"
            :strategies="strategies"
            @remove="emit('removeStrategy', idx)"
        />
      </div>
    </div>
  </section>
</template>
