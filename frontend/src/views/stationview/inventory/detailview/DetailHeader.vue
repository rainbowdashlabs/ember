/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import InventoryBadges from '@/components/inventory/InventoryBadges.vue'
import AuthImage from '@/components/display/AuthImage.vue'
import type {InventoryType} from '@/api/generated/schema'

withDefaults(defineProps<{
  name: string
  inventoryType: InventoryType | null
  hasSizes: boolean
  homogeneous: boolean
  /** How many kinds are defined here, or {@code null} while the inventory is still being read. */
  artCount: number | null
  /** Whether the reader may change the inventory itself, which is what the edit button needs. */
  canEdit: boolean
  /** The address of the inventory's photo, shown beside its name where it has one. */
  pictureSrc?: string | null
}>(), {pictureSrc: null})

defineEmits<{
  back: []
  edit: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <div class="flex min-w-0 items-center gap-3">
      <AuthImage v-if="pictureSrc" :src="pictureSrc" alt="" data-testid="inventory-picture"
                 class="h-16 w-16 shrink-0 rounded-theme border border-(--border) object-cover"/>
      <div class="min-w-0 space-y-1">
        <SubHeader>{{ name }}</SubHeader>
        <InventoryBadges
            :inventory-type="inventoryType"
            :has-sizes="hasSizes"
            :homogeneous="homogeneous"
            :art-count="artCount"
        />
      </div>
    </div>
    <ButtonRow pair>
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
        {{ t('inventory.manage.back') }}
      </SecondaryButton>
      <PrimaryButton v-if="canEdit" :icon="['fas', 'pen']" data-testid="inventory-detail-edit" @click="$emit('edit')">
        {{ t('inventory.detail.edit') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
