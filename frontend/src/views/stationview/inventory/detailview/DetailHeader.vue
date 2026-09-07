/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import InventoryBadges from '@/components/inventory/InventoryBadges.vue'
import type {InventoryTypeName} from '@/api/inventory'

defineProps<{
  name: string
  inventoryType: InventoryTypeName | null
  hasSizes: boolean
  homogeneous: boolean
  /** How many kinds are defined here, or {@code null} while the inventory is still being read. */
  artCount: number | null
  /** Whether the reader may change the inventory itself, which is what the edit button needs. */
  canEdit: boolean
}>()

defineEmits<{
  back: []
  edit: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <div class="min-w-0 space-y-1">
      <SubHeader>{{ name }}</SubHeader>
      <InventoryBadges
          :inventory-type="inventoryType"
          :has-sizes="hasSizes"
          :homogeneous="homogeneous"
          :art-count="artCount"
      />
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
        {{ t('inventory.manage.back') }}
      </SecondaryButton>
      <PrimaryButton v-if="canEdit" :icon="['fas', 'pen']" data-testid="inventory-detail-edit" @click="$emit('edit')">
        {{ t('inventory.detail.edit') }}
      </PrimaryButton>
    </div>
  </div>
</template>
