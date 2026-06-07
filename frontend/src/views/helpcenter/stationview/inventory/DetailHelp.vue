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
import StatValue from '@/components/typography/StatValue.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
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

    <!-- Dummy: Stats boxes -->
    <HelpSection :title="t('helpCenter.inventoryDetail.statsTitle')">
      <p>{{ t('helpCenter.inventoryDetail.statsText') }}</p>

      <div class="grid grid-cols-2 sm:grid-cols-5 gap-3">
        <NeutralContainer class="text-center">
          <div class="text-2xl font-bold">24</div>
          <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.total') }}</div>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue color="success">8</StatValue>
          <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.free') }}</div>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue>13</StatValue>
          <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.assigned') }}</div>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue color="error">1</StatValue>
          <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lost') }}</div>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <div class="text-2xl font-bold text-secondary-accent">2</div>
          <div class="text-xs text-(--text-muted)">{{ t('helpCenter.inventoryDetail.lentOut') }}</div>
        </NeutralContainer>
      </div>

      <!-- Size distribution -->
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
    </HelpSection>

    <!-- Dummy: All items table -->
    <HelpSection :title="t('helpCenter.inventoryDetail.itemsTitle')">
      <p>{{ t('helpCenter.inventoryDetail.itemsText') }}</p>

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
                  <EditButton />
                  <DeleteButton />
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
                  <EditButton />
                  <DeleteButton />
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
                  <EditButton />
                  <DeleteButton />
                </div>
              </Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </HelpSection>

    <!-- Procurement section -->
    <HelpSection :title="t('helpCenter.inventoryDetail.procurementTitle')">
      <p>{{ t('helpCenter.inventoryDetail.procurementText') }}</p>

      <PrimaryButton :icon="['fas', 'folder-plus']">
        {{ t('helpCenter.inventoryDetail.dummyCreateProcurement') }}
      </PrimaryButton>
    </HelpSection>

    <!-- Lost items -->
    <HelpSection :title="t('helpCenter.inventoryDetail.lostTitle')">
      <p>{{ t('helpCenter.inventoryDetail.lostText') }}</p>

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
                <div class="font-medium">Helm <SizeBadge>S</SizeBadge></div>
                <div class="text-xs text-(--text-muted)">INV-0003</div>
              </Td>
              <Td>Erika Musterfrau</Td>
              <Td><ErrorBadge>01.05.2026</ErrorBadge></Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </HelpSection>

    <!-- Free items -->
    <HelpSection :title="t('helpCenter.inventoryDetail.freeTitle')">
      <p>{{ t('helpCenter.inventoryDetail.freeText') }}</p>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        <NeutralContainer class="flex items-center justify-between gap-2">
          <div>
            <div class="text-sm font-medium">Helm <SizeBadge>M</SizeBadge></div>
            <div class="text-xs text-(--text-muted)">INV-0010</div>
          </div>
          <PrimaryButton>{{ t('inventory.detail.assign') }}</PrimaryButton>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between gap-2">
          <div>
            <div class="text-sm font-medium">Helm <SizeBadge>L</SizeBadge></div>
            <div class="text-xs text-(--text-muted)">INV-0011</div>
          </div>
          <PrimaryButton>{{ t('inventory.detail.assign') }}</PrimaryButton>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
