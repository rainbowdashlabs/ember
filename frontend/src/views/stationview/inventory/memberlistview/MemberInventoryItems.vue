/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { InventoryItem } from '@/api/inventory'
import { itemNamePart, itemSizeLabel, type ItemLabelParts } from './itemLabel'

/** The pieces one member holds from one inventory, the lost ones marked as such. */
defineProps<{
  items: InventoryItem[]
  parts: ItemLabelParts
}>()

const { t } = useI18n()
</script>

<template>
  <div v-if="items.length > 0" class="flex flex-wrap gap-1">
    <span v-for="item in items" :key="item.id"
          :class="item.lostAt ? 'text-error' : ''"
          class="inline-flex items-center gap-1 text-xs">
      <template v-if="itemNamePart(item, parts)">{{ itemNamePart(item, parts) }}</template>
      <SizeBadge v-if="itemSizeLabel(item, parts)" :lost="!!item.lostAt">{{ itemSizeLabel(item, parts) }}</SizeBadge>
      <span v-if="item.lostAt" class="text-[10px]">({{ t('inventoryMembers.lost') }})</span>
    </span>
  </div>
  <MutedText v-else size="base">–</MutedText>
</template>
