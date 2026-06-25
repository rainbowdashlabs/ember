/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {ColumnEntry, GdprDeletionContext, TrackingStatusName} from '@/api/dataTracking'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatusBadge from './StatusBadge.vue'
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
      <SectionHeader class="!text-base">{{ t('adminDataTracking.gdprDeletion') }}</SectionHeader>
      <StatusBadge :status="deletion.status"/>
    </div>
    <div class="space-y-2 mt-2">
      <SelectInput v-model="deletion.status">
        <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
      </SelectInput>
      <TextInput
          v-if="deletion.status === 'IGNORED'"
          v-model="deletion.reason"
          :placeholder="t('adminDataTracking.detail.reason')"
      />
      <TextAreaInput
          v-if="deletion.status === 'UNVERIFIED'"
          v-model="deletion.reason"
          :placeholder="t('adminDataTracking.detail.reviewNote')"
          :rows="2"
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
