/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MovementPieceBadge from '@/components/inventory/MovementPieceBadge.vue'

const {t} = useI18n()

const items = [
  {size: 'M', id: 'INV-0010'},
  {size: 'L', id: 'INV-0011'},
  {size: 'L', id: 'INV-0002', movementId: 12},
]
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
    <NeutralContainer v-for="item in items" :key="item.id" class="flex items-center justify-between gap-2">
      <div>
        <div class="text-sm font-medium">{{ t('helpCenter.sample.equipment.helmet') }} <SizeBadge>{{ item.size }}</SizeBadge></div>
        <div class="text-xs text-(--text-muted)">{{ item.id }}</div>
      </div>
      <MovementPieceBadge v-if="item.movementId" :movement-id="item.movementId"/>
      <PrimaryButton v-else>{{ t('inventory.detail.assign') }}</PrimaryButton>
    </NeutralContainer>
  </div>
</template>
