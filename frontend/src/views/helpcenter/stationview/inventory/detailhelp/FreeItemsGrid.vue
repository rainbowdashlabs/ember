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

const {t} = useI18n()

const items = [
  {size: 'M', id: 'INV-0010'},
  {size: 'L', id: 'INV-0011'},
]
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
    <NeutralContainer v-for="item in items" :key="item.id" class="flex items-center justify-between gap-2">
      <div>
        <div class="text-sm font-medium">Helm <SizeBadge>{{ item.size }}</SizeBadge></div>
        <div class="text-xs text-(--text-muted)">{{ item.id }}</div>
      </div>
      <PrimaryButton>{{ t('inventory.detail.assign') }}</PrimaryButton>
    </NeutralContainer>
  </div>
</template>
