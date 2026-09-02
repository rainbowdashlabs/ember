/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import DataTable from '@/components/table/DataTable.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryMembers.title')" :subtitle="t('helpCenter.inventoryMembers.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryMembers.whatShown')">
      <p>{{ t('helpCenter.inventoryMembers.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMembers.customizeTitle')">
      <p>{{ t('helpCenter.inventoryMembers.customizeText') }}</p>
    </HelpSection>

    <!-- Dummy: Filters -->
    <HelpSection :title="t('helpCenter.inventoryMembers.filterTitle')">
      <p>{{ t('helpCenter.inventoryMembers.filterText') }}</p>

      <NeutralContainer class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <label class="text-sm font-medium">{{ t('inventoryMembers.role') }}</label>
          <SelectInput class="w-40 text-sm">
            <option value="">{{ t('inventoryMembers.allRoles') }}</option>
            <option>Mitglied</option>
            <option>Team</option>
          </SelectInput>
        </div>
        <div class="flex items-center gap-2">
          <label class="text-sm font-medium">{{ t('inventoryMembers.showEmpty') }}</label>
          <ToggleInput :model-value="false" />
        </div>
      </NeutralContainer>

      <p>{{ t('helpCenter.inventoryMembers.restrictionText') }}</p>
    </HelpSection>

    <!-- Dummy: Member inventory table -->
    <div class="space-y-3">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('inventoryMembers.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'file-export']">
          {{ t('inventoryMembers.export') }}
        </PrimaryButton>
      </div>

      <DataTable>
        <template #head>
          <Th>{{ t('membersList.colName') }}</Th>
          <Th>Helme</Th>
          <Th>Jacken</Th>
          <Th>Stiefel</Th>
        </template>
        <TRow class="hover:bg-(--bg-accent)/30 cursor-pointer">
          <Td class="font-medium text-primary">Max Mustermann</Td>
          <Td><span class="text-xs">Helm [M]</span></Td>
          <Td><span class="text-xs">Jacke [L]</span></Td>
          <Td><span class="text-xs">Stiefel [42]</span></Td>
        </TRow>
        <TRow class="hover:bg-(--bg-accent)/30 cursor-pointer">
          <Td class="font-medium text-primary">Erika Musterfrau</Td>
          <Td><span class="text-xs text-error">Helm [S] ({{ t('inventoryMembers.lost') }})</span></Td>
          <Td><span class="text-xs">Jacke [M]</span></Td>
          <Td><MutedText size="base">–</MutedText></Td>
        </TRow>
        <TRow class="hover:bg-(--bg-accent)/30 cursor-pointer">
          <Td class="font-medium text-primary">Jan Schmidt</Td>
          <Td><span class="text-xs">Helm [L]</span></Td>
          <Td><MutedText size="base">–</MutedText></Td>
          <Td><span class="text-xs">Stiefel [44]</span></Td>
        </TRow>
      </DataTable>

      <p class="text-xs text-(--text-muted)">3 {{ t('inventoryMembers.memberCount') }}</p>
    </div>

    <HelpSection :title="t('helpCenter.inventoryMembers.exportTitle')">
      <p>{{ t('helpCenter.inventoryMembers.exportText') }}</p>
      <p>{{ t('helpCenter.inventoryMembers.exportModeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryMembers.tip') }}</HelpTip>
  </HelpArticle>
</template>
