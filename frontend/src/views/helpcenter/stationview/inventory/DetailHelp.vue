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
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryDetail.title')" :subtitle="t('helpCenter.inventoryDetail.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryDetail.whatShown')">
      <p>{{ t('helpCenter.inventoryDetail.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.statsTitle')">
      <p>{{ t('helpCenter.inventoryDetail.statsText') }}</p>
    </HelpSection>

    <!-- Dummy: Stats boxes -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <NeutralContainer class="text-center">
        <div class="text-2xl font-bold">24</div>
        <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.total') }}</div>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <div class="text-2xl font-bold text-success">8</div>
        <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.free') }}</div>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <div class="text-2xl font-bold text-primary">15</div>
        <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.assigned') }}</div>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <div class="text-2xl font-bold text-error">1</div>
        <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lost') }}</div>
      </NeutralContainer>
    </div>

    <!-- Dummy: Size distribution -->
    <SubHeader>{{ t('inventory.detail.bySize') }}</SubHeader>
    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
            <th class="px-3 py-2 font-medium">{{ t('inventory.detail.size') }}</th>
            <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.total') }}</th>
            <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.free') }}</th>
            <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.assigned') }}</th>
            <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.lost') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="px-3 py-2.5 font-medium">S</td>
            <td class="px-3 py-2.5 text-center">6</td>
            <td class="px-3 py-2.5 text-center text-success">2</td>
            <td class="px-3 py-2.5 text-center text-primary">3</td>
            <td class="px-3 py-2.5 text-center text-error">1</td>
          </tr>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="px-3 py-2.5 font-medium">M</td>
            <td class="px-3 py-2.5 text-center">10</td>
            <td class="px-3 py-2.5 text-center text-success">3</td>
            <td class="px-3 py-2.5 text-center text-primary">7</td>
            <td class="px-3 py-2.5 text-center text-error">0</td>
          </tr>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="px-3 py-2.5 font-medium">L</td>
            <td class="px-3 py-2.5 text-center">8</td>
            <td class="px-3 py-2.5 text-center text-success">3</td>
            <td class="px-3 py-2.5 text-center text-primary">5</td>
            <td class="px-3 py-2.5 text-center text-error">0</td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.inventoryDetail.lostTitle')">
      <p>{{ t('helpCenter.inventoryDetail.lostText') }}</p>
    </HelpSection>

    <!-- Dummy: Lost items -->
    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
            <th class="px-3 py-2 font-medium">{{ t('inventory.detail.item') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('inventory.detail.owner') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('inventory.detail.lostSince') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="px-3 py-2.5">
              <div class="font-medium">Helm <span class="font-normal text-(--text-muted)">[S]</span></div>
              <div class="text-xs text-(--text-muted)">INV-0003</div>
            </td>
            <td class="px-3 py-2.5">Erika Musterfrau</td>
            <td class="px-3 py-2.5"><ErrorBadge>01.05.2026</ErrorBadge></td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.inventoryDetail.freeTitle')">
      <p>{{ t('helpCenter.inventoryDetail.freeText') }}</p>
    </HelpSection>

    <!-- Dummy: Free items -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
      <NeutralContainer class="flex items-center justify-between gap-2">
        <div>
          <div class="text-sm font-medium">Helm <span class="font-normal text-(--text-muted)">[M]</span></div>
          <div class="text-xs text-(--text-muted)">INV-0010</div>
        </div>
        <PrimaryButton>{{ t('inventory.detail.assign') }}</PrimaryButton>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-between gap-2">
        <div>
          <div class="text-sm font-medium">Helm <span class="font-normal text-(--text-muted)">[L]</span></div>
          <div class="text-xs text-(--text-muted)">INV-0011</div>
        </div>
        <PrimaryButton>{{ t('inventory.detail.assign') }}</PrimaryButton>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.inventoryDetail.procurementTitle')">
      <p>{{ t('helpCenter.inventoryDetail.procurementText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
