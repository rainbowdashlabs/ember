/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {InventoryItemHistory} from '@/api/inventory'
import {formatDate} from '@/util/format'

const props = defineProps<{
  entries: InventoryItemHistory[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('itemDetail.history') }}</SubHeader>
    <div v-if="props.entries.length === 0" class="text-sm text-(--text-muted)">{{ t('itemDetail.noHistory') }}</div>
    <table v-else class="w-full text-sm">
      <thead>
        <tr class="text-xs text-(--text-muted) border-b border-(--border)">
          <th class="p-2 text-left">{{ t('itemDetail.member') }}</th>
          <th class="p-2 text-left">{{ t('itemDetail.givenOut') }}</th>
          <th class="p-2 text-left">{{ t('itemDetail.returned') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="h in props.entries" :key="h.id" class="border-b border-(--border) last:border-0">
          <td class="p-2">{{ h.memberName || '-' }}</td>
          <td class="p-2 text-(--text-muted)">{{ h.givenOut ? formatDate(h.givenOut) : '-' }}</td>
          <td class="p-2 text-(--text-muted)">{{ h.returned ? formatDate(h.returned) : t('itemDetail.current') }}</td>
        </tr>
      </tbody>
    </table>
  </NeutralContainer>
</template>
