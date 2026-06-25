/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()

const rows = [
  {size: 'S', total: 6, free: 2, assigned: 3, lost: 1},
  {size: 'M', total: 10, free: 3, assigned: 7, lost: 0},
  {size: 'L', total: 8, free: 3, assigned: 5, lost: 0},
]
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <THead>
          <Th>{{ t('inventory.detail.size') }}</Th>
          <Th align="center">{{ t('inventory.detail.total') }}</Th>
          <Th align="center">{{ t('inventory.detail.free') }}</Th>
          <Th align="center">{{ t('inventory.detail.assigned') }}</Th>
          <Th align="center">{{ t('inventory.detail.lost') }}</Th>
        </THead>
      </thead>
      <tbody>
        <TRow v-for="row in rows" :key="row.size">
          <Td class="font-medium">{{ row.size }}</Td>
          <Td align="center">{{ row.total }}</Td>
          <Td align="center" class="text-success">{{ row.free }}</Td>
          <Td align="center" class="text-primary">{{ row.assigned }}</Td>
          <Td align="center" class="text-error">{{ row.lost }}</Td>
        </TRow>
      </tbody>
    </table>
  </NeutralContainer>
</template>
