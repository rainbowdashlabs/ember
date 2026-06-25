/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { InventoryItem, InventorySize } from '@/api/types'

const props = defineProps<{
  items: InventoryItem[]
  sizes: InventorySize[] | undefined
}>()

defineEmits<{
  assign: [itemId: number]
}>()

const { t } = useI18n()

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId || !props.sizes) return ''
  return props.sizes.find(s => s.id === sizeId)?.label ?? ''
}
</script>

<template>
  <template v-if="items.length > 0">
    <SubHeader>{{ t('inventory.detail.freeItems') }}</SubHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
      <NeutralContainer v-for="item in items" :key="item.id" class="flex items-center justify-between gap-2">
        <div>
          <div class="text-sm font-medium">
            {{ item.name }}
            <SizeBadge v-if="sizeName(item.sizeId)">{{ sizeName(item.sizeId) }}</SizeBadge>
          </div>
          <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
        </div>
        <PrimaryButton @click="$emit('assign', item.id)">
          {{ t('inventory.detail.assign') }}
        </PrimaryButton>
      </NeutralContainer>
    </div>
  </template>
</template>
