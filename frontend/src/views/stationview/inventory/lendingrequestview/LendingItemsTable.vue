/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {LendingRequestDetail} from '@/api/lending'

defineProps<{
  detail: LendingRequestDetail
}>()

const {t} = useI18n()
</script>

<template>
  <SubHeader class="mb-2">{{ t('lending.items') }}</SubHeader>
  <NeutralContainer v-if="detail.items.length > 0" class="mb-4">
    <table class="w-full text-sm">
      <thead>
      <tr class="border-b border-[var(--border)]">
        <th class="text-left py-1">{{ t('lending.itemType') }}</th>
        <th class="text-left py-1">{{ t('lending.quantity') }}</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="enriched in detail.items" :key="enriched.item.id" class="border-b border-[var(--border)] last:border-0">
        <td class="py-1">{{ enriched.inventoryName }}</td>
        <td class="py-1">{{ enriched.item.quantity }}</td>
      </tr>
      </tbody>
    </table>
  </NeutralContainer>
  <MutedText tag="p" size="sm" v-else>{{ t('lending.noItems') }}</MutedText>
</template>
