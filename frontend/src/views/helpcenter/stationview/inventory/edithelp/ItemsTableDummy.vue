/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import ItemsTableRows from '@/views/helpcenter/stationview/inventory/edithelp/ItemsTableRows.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SubHeader>{{ t('inventory.edit.itemsTitle') }}</SubHeader>
      <div class="flex gap-2">
        <SecondaryButton :icon="['fas', 'user-plus']">
          {{ t('helpCenter.inventoryEdit.dummyQuickAssign') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('inventory.edit.addItem') }}
        </PrimaryButton>
      </div>
    </div>
    <div class="overflow-x-auto">
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
        <ItemsTableRows />
      </table>
    </div>
  </NeutralContainer>
</template>
