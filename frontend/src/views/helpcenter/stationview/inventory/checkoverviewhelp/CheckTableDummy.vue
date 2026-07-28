/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import CheckRowDummy from './CheckRowDummy.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('inventory.check.title') }}</SectionHeader>
    <div class="flex items-center justify-between gap-4">
      <div class="flex gap-2">
        <SelectionToggleButton :selected="true">{{ t('inventory.check.tabTeam') }}</SelectionToggleButton>
        <SelectionToggleButton :selected="false">{{ t('inventory.check.tabMember') }}</SelectionToggleButton>
      </div>
      <MutedIconButton :icon="['fas', 'sort']" :label="t('helpCenter.inventoryChecks.sortLabel')" class="text-xs"/>
    </div>
    <DataTable>
      <template #head>
        <Th>{{ t('inventory.check.member') }}</Th>
        <Th>{{ t('inventory.check.lastChecked') }}</Th>
        <Th>{{ t('inventory.check.checkedBy') }}</Th>
        <Th>{{ t('inventory.check.status') }}</Th>
        <th class="px-3 py-2"></th>
      </template>
      <CheckRowDummy member="Max Mustermann" last-checked="10.05.2026, 14:30" checked-by="Admin User">
        <template #action><PrimaryButton>{{ t('inventory.check.start') }}</PrimaryButton></template>
      </CheckRowDummy>
      <CheckRowDummy member="Erika Musterfrau" :last-checked="t('inventory.check.neverChecked')" checked-by="-" :show-eye="false">
        <template #status><SecondaryBadge>{{ t('inventory.check.neverChecked') }}</SecondaryBadge></template>
        <template #action><PrimaryButton>{{ t('inventory.check.start') }}</PrimaryButton></template>
      </CheckRowDummy>
      <CheckRowDummy member="Jan Schmidt" last-checked="12.05.2026, 09:00" checked-by="Admin User">
        <template #status><InfoBadge>{{ t('inventory.check.lockedByMe') }}</InfoBadge></template>
        <template #action><SecondaryButton>{{ t('inventory.check.continue') }}</SecondaryButton></template>
      </CheckRowDummy>
      <CheckRowDummy member="Lisa Müller" last-checked="05.05.2026, 16:00" checked-by="Betreuer A">
        <template #status><ErrorBadge>{{ t('inventory.check.locked') }}: Betreuer B</ErrorBadge></template>
        <template #action><PrimaryButton disabled>{{ t('inventory.check.start') }}</PrimaryButton></template>
      </CheckRowDummy>
    </DataTable>
  </NeutralContainer>
</template>
