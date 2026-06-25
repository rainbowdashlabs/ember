/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import { InventoryTypes, type InventoryTypeName } from '@/api/types'

defineProps<{
  name: string
  inventoryType: InventoryTypeName | null
  hasSizes: boolean
}>()

defineEmits<{
  back: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <div>
      <SectionHeader>{{ name }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">
        {{ t('inventory.manage.type.' + (inventoryType ?? InventoryTypes.INTERNAL)) }}
        <span v-if="hasSizes"> &middot; {{ t('inventory.manage.withSizes') }}</span>
      </p>
    </div>
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
      {{ t('inventory.manage.back') }}
    </SecondaryButton>
  </div>
</template>
