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
import SizeBadge from '@/components/badge/SizeBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <THead>
          <Th>{{ t('inventory.edit.colName') }}</Th>
          <Th>{{ t('inventory.edit.colId') }}</Th>
          <Th>{{ t('inventory.edit.colSize') }}</Th>
          <Th>{{ t('inventory.edit.colAssigned') }}</Th>
          <th class="px-3 py-2"></th>
        </THead>
      </thead>
      <tbody>
        <TRow>
          <Td class="font-medium">Helm</Td>
          <Td muted>INV-0001</Td>
          <Td><SizeBadge>M</SizeBadge></Td>
          <Td><span class="text-primary font-medium">Max Mustermann</span></Td>
          <Td align="right">
            <div class="flex items-center justify-end gap-0.5">
              <EditButton/>
              <DeleteButton/>
            </div>
          </Td>
        </TRow>
        <TRow>
          <Td class="font-medium">Helm</Td>
          <Td muted>INV-0002</Td>
          <Td><SizeBadge>L</SizeBadge></Td>
          <Td><span class="text-(--text-muted)">&ndash;</span></Td>
          <Td align="right">
            <div class="flex items-center justify-end gap-0.5">
              <EditButton/>
              <DeleteButton/>
            </div>
          </Td>
        </TRow>
        <TRow class="opacity-60">
          <Td class="font-medium">
            Helm <span class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} (01.05.2026)</span>
          </Td>
          <Td muted>INV-0003</Td>
          <Td><SizeBadge>S</SizeBadge></Td>
          <Td><span class="text-primary font-medium">Erika Musterfrau</span></Td>
          <Td align="right">
            <div class="flex items-center justify-end gap-0.5">
              <EditButton/>
              <DeleteButton/>
            </div>
          </Td>
        </TRow>
      </tbody>
    </table>
  </NeutralContainer>
</template>
