/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { InventoryItem, InventoryItemHistory } from '@/api/inventory'
import type { StationMember } from '@/api/types'
import { inventory } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { formatDate } from '@/util/format'

const props = defineProps<{
  item: InventoryItem | null
  memberMap: Map<number, StationMember>
}>()

const show = defineModel<boolean>({ default: false })

const { t } = useI18n()

const { config: entries, loading, reload: loadHistory } = useConfigPanel<InventoryItemHistory[]>({
  initial: [],
  fetch: async () => props.item ? await inventory.getItemHistory(props.item.id) : [],
  immediate: false,
  formatError: () => '',
})

watch(show, (visible) => {
  if (visible && props.item) {
    entries.value = []
    loadHistory()
  }
})
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ t('inventory.edit.historyTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ props.item?.name }}</p>
      <AsyncSection
          :empty="entries.length === 0"
          :empty-compact="true"
          :empty-message="t('inventory.edit.noHistory')"
          :loading="loading"
          spinner-size="md"
      >
        <div class="space-y-2 max-h-80 overflow-y-auto">
          <div v-for="entry in entries" :key="entry.id"
               class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
            <span class="text-sm font-medium"><MemberName :identity="entry.memberIdentity ?? null"/></span>
            <div class="text-xs text-(--text-muted) text-right">
              <div>{{ t('inventory.edit.givenOut') }}: {{ formatDate(entry.givenOut) }}</div>
              <div v-if="entry.returned">
                {{ t('inventory.edit.returned') }}: {{ formatDate(entry.returned) }}
                <span v-if="entry.corrected">({{ t('inventory.edit.corrected') }})</span>
              </div>
              <div v-else class="text-primary font-medium">{{ t('inventory.edit.currentlyAssigned') }}</div>
            </div>
          </div>
        </div>
      </AsyncSection>
      <div class="flex justify-end">
        <SecondaryButton @click="show = false">{{ t('common.close') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
