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
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryOverview.title')" :subtitle="t('helpCenter.inventoryOverview.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryOverview.whatShown')">
      <p>{{ t('helpCenter.inventoryOverview.whatShownText') }}</p>
      <p>{{ t('helpCenter.inventoryOverview.lostItems') }}</p>
      <p>{{ t('helpCenter.inventoryOverview.openExchanges') }}</p>
      <p>{{ t('helpCenter.inventoryOverview.openProcurement') }}</p>
    </HelpSection>

    <!-- Dummy: Open exchanges table -->
    <NeutralContainer class="space-y-3">
      <SubHeader>
        <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2" />
        {{ t('inventory.overview.exchanges') }} (2)
      </SubHeader>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colItem') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colOwner') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colStatus') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5">Helme <span class="text-(--text-muted)">[M &rarr; L]</span></td>
              <td class="px-3 py-2.5">Max Mustermann</td>
              <td class="px-3 py-2.5"><InfoBadge>{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge></td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5">Jacken <span class="text-(--text-muted)">[S &rarr; M]</span></td>
              <td class="px-3 py-2.5">Erika Musterfrau</td>
              <td class="px-3 py-2.5"><SecondaryBadge>{{ t('exchanges.status.RECEIVED') }}</SecondaryBadge></td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <!-- Dummy: Open procurement -->
    <NeutralContainer class="space-y-3">
      <SubHeader>
        <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
        {{ t('inventory.overview.procurement') }} (1)
      </SubHeader>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colItem') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colOwner') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colNotes') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5">Stiefel <span class="text-(--text-muted)">[42]</span></td>
              <td class="px-3 py-2.5">Max Mustermann</td>
              <td class="px-3 py-2.5 text-(--text-muted)">Alter Stiefel gerissen</td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <!-- Dummy: Lost items -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colItem') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colOwner') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colLostSince') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5">
                <div class="font-medium">Helm <span class="font-normal text-(--text-muted)">[M]</span></div>
                <div class="text-xs text-(--text-muted)">INV-0042</div>
              </td>
              <td class="px-3 py-2.5">Erika Musterfrau</td>
              <td class="px-3 py-2.5"><ErrorBadge>01.05.2026</ErrorBadge></td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
