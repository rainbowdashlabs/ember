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

    <!-- Dummy: Member inventory table -->
    <div class="space-y-3">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('inventoryMembers.title') }}</SectionHeader>
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1" />
          {{ t('inventoryMembers.export') }}
        </PrimaryButton>
      </div>

      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('membersList.colName') }}</th>
              <th class="px-3 py-2 font-medium">Helme</th>
              <th class="px-3 py-2 font-medium">Jacken</th>
              <th class="px-3 py-2 font-medium">Stiefel</th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-(--bg-accent)/30 cursor-pointer">
              <td class="px-3 py-2.5 font-medium text-primary">Max Mustermann</td>
              <td class="px-3 py-2.5"><span class="text-xs">Helm [M]</span></td>
              <td class="px-3 py-2.5"><span class="text-xs">Jacke [L]</span></td>
              <td class="px-3 py-2.5"><span class="text-xs">Stiefel [42]</span></td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-(--bg-accent)/30 cursor-pointer">
              <td class="px-3 py-2.5 font-medium text-primary">Erika Musterfrau</td>
              <td class="px-3 py-2.5"><span class="text-xs text-error">Helm [S] ({{ t('inventoryMembers.lost') }})</span></td>
              <td class="px-3 py-2.5"><span class="text-xs">Jacke [M]</span></td>
              <td class="px-3 py-2.5"><span class="text-(--text-muted)">&mdash;</span></td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-(--bg-accent)/30 cursor-pointer">
              <td class="px-3 py-2.5 font-medium text-primary">Jan Schmidt</td>
              <td class="px-3 py-2.5"><span class="text-xs">Helm [L]</span></td>
              <td class="px-3 py-2.5"><span class="text-(--text-muted)">&mdash;</span></td>
              <td class="px-3 py-2.5"><span class="text-xs">Stiefel [44]</span></td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>

      <p class="text-xs text-(--text-muted)">3 {{ t('inventoryMembers.memberCount') }}</p>
    </div>

    <HelpSection :title="t('helpCenter.inventoryMembers.exportTitle')">
      <p>{{ t('helpCenter.inventoryMembers.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryMembers.tip') }}</HelpTip>
  </HelpArticle>
</template>
