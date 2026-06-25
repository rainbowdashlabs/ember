/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DummyBlockFull from '@/views/helpcenter/stationview/inventory/lendingblockshelp/DummyBlockFull.vue'
import DummyBlockMixed from '@/views/helpcenter/stationview/inventory/lendingblockshelp/DummyBlockMixed.vue'
import DummyBlockInventory from '@/views/helpcenter/stationview/inventory/lendingblockshelp/DummyBlockInventory.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('helpCenter.inventoryLendingBlocks.dummyTitle') }}</SectionHeader>
      <div class="flex gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']">
          {{ t('helpCenter.inventoryLendingBlocks.dummyBack') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('helpCenter.inventoryLendingBlocks.dummyAddBlock') }}
        </PrimaryButton>
      </div>
    </div>

    <DummyBlockFull />
    <DummyBlockMixed />
    <DummyBlockInventory />
  </NeutralContainer>
</template>
