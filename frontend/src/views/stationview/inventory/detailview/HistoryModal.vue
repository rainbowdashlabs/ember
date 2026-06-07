/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { InventoryItem, InventoryItemHistory, StationMember } from '@/api/types'
import { inventory } from '@/api'

const props = defineProps<{
  item: InventoryItem | null
  memberMap: Map<number, StationMember>
}>()

const show = defineModel<boolean>({ default: false })

const { t } = useI18n()

const entries = ref<InventoryItemHistory[]>([])
const loading = ref(false)

watch(show, async (visible) => {
  if (visible && props.item) {
    loading.value = true
    entries.value = []
    try {
      entries.value = await inventory.getItemHistory(props.item.id)
    } catch { /* ignore */ }
    finally { loading.value = false }
  }
})


function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.historyTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ props.item?.name }}</p>
      <Spinner v-if="loading" size="md" />
      <EmptyState v-if="!loading && entries.length === 0" compact>{{ t('inventory.edit.noHistory') }}</EmptyState>
      <div v-if="!loading && entries.length > 0" class="space-y-2 max-h-80 overflow-y-auto">
        <div v-for="entry in entries" :key="entry.id"
             class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <span class="text-sm font-medium"><MemberName :identity="entry.memberIdentity ?? null"/></span>
          <div class="text-xs text-(--text-muted) text-right">
            <div>{{ t('inventory.edit.givenOut') }}: {{ formatDate(entry.givenOut) }}</div>
            <div v-if="entry.returned">{{ t('inventory.edit.returned') }}: {{ formatDate(entry.returned) }}</div>
            <div v-else class="text-primary font-medium">{{ t('inventory.edit.currentlyAssigned') }}</div>
          </div>
        </div>
      </div>
      <div class="flex justify-end">
        <SecondaryButton @click="show = false">{{ t('common.close') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
