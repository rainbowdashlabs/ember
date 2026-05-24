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
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

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
          <THead>
            <Th>{{ t('inventory.detail.size') }}</Th>
            <Th align="center">{{ t('inventory.detail.total') }}</Th>
            <Th align="center">{{ t('inventory.detail.free') }}</Th>
            <Th align="center">{{ t('inventory.detail.assigned') }}</Th>
            <Th align="center">{{ t('inventory.detail.lost') }}</Th>
          </THead>
        </thead>
        <tbody>
          <TRow>
            <Td class="font-medium">S</Td>
            <Td align="center">6</Td>
            <Td align="center" class="text-success">2</Td>
            <Td align="center" class="text-primary">3</Td>
            <Td align="center" class="text-error">1</Td>
          </TRow>
          <TRow>
            <Td class="font-medium">M</Td>
            <Td align="center">10</Td>
            <Td align="center" class="text-success">3</Td>
            <Td align="center" class="text-primary">7</Td>
            <Td align="center" class="text-error">0</Td>
          </TRow>
          <TRow>
            <Td class="font-medium">L</Td>
            <Td align="center">8</Td>
            <Td align="center" class="text-success">3</Td>
            <Td align="center" class="text-primary">5</Td>
            <Td align="center" class="text-error">0</Td>
          </TRow>
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
          <THead>
            <Th>{{ t('inventory.detail.item') }}</Th>
            <Th>{{ t('inventory.detail.owner') }}</Th>
            <Th>{{ t('inventory.detail.lostSince') }}</Th>
          </THead>
        </thead>
        <tbody>
          <TRow>
            <Td>
              <div class="font-medium">Helm <span class="font-normal text-(--text-muted)">[S]</span></div>
              <div class="text-xs text-(--text-muted)">INV-0003</div>
            </Td>
            <Td>Erika Musterfrau</Td>
            <Td><ErrorBadge>01.05.2026</ErrorBadge></Td>
          </TRow>
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
