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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminApiStatusDetail.title')" :subtitle="t('helpCenter.adminApiStatusDetail.subtitle')">
    <HelpSection :title="t('helpCenter.adminApiStatusDetail.overviewTitle')">
      <p>{{ t('helpCenter.adminApiStatusDetail.overviewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatusDetail.statusCodesTitle')">
      <p>{{ t('helpCenter.adminApiStatusDetail.statusCodesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatusDetail.recentTitle')">
      <p>{{ t('helpCenter.adminApiStatusDetail.recentText') }}</p>
    </HelpSection>

    <!-- Dummy: Endpoint detail page -->
    <HelpSection :title="t('helpCenter.adminApiStatusDetail.exampleTitle')">
      <!-- Back button + header -->
      <div class="flex items-center gap-3 mb-4">
        <SecondaryButton :icon="['fas', 'arrow-left']">
          {{ t('common.back') }}
        </SecondaryButton>
        <span class="text-lg font-bold">
          <span class="font-mono text-green-600">GET</span>
          <span class="font-mono ml-2">/api/v1/members</span>
        </span>
      </div>

      <!-- Summary cards -->
      <div class="grid grid-cols-2 gap-3 mb-4">
        <NeutralContainer class="text-center">
          <p class="text-2xl font-bold">1.240</p>
          <p class="text-xs text-(--text-muted)">{{ t('apiStatus.totalRequests') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <p class="text-2xl font-bold">12ms</p>
          <p class="text-xs text-(--text-muted)">{{ t('apiStatus.avgResponseTime') }}</p>
        </NeutralContainer>
      </div>

      <!-- Chart placeholders -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <NeutralContainer>
          <SectionHeader>{{ t('apiStatus.statusCodes') }}</SectionHeader>
          <div class="h-32 flex items-center justify-center text-sm text-(--text-muted)">
            <font-awesome-icon :icon="['fas', 'chart-bar']" class="text-2xl mr-2" />
          </div>
        </NeutralContainer>
        <NeutralContainer>
          <SectionHeader>{{ t('apiStatus.responseTimeLine') }}</SectionHeader>
          <div class="h-32 flex items-center justify-center text-sm text-(--text-muted)">
            <font-awesome-icon :icon="['fas', 'chart-line']" class="text-2xl mr-2" />
          </div>
        </NeutralContainer>
      </div>

      <!-- Recent requests table -->
      <NeutralContainer>
        <SectionHeader>{{ t('apiStatus.recentRequests') }}</SectionHeader>
        <div class="overflow-x-auto mt-3">
          <table class="w-full text-sm">
            <THead>
              <Th>{{ t('apiStatus.timestamp') }}</Th>
              <Th align="right">{{ t('apiStatus.statusCode') }}</Th>
              <Th align="right">{{ t('apiStatus.duration') }}</Th>
            </THead>
            <tbody>
              <TRow>
                <Td class="font-mono text-xs">07.06.26, 14:32:10</Td>
                <Td align="right" class="font-semibold text-success tabular-nums">200</Td>
                <Td align="right" class="tabular-nums">8ms</Td>
              </TRow>
              <TRow>
                <Td class="font-mono text-xs">07.06.26, 14:31:55</Td>
                <Td align="right" class="font-semibold text-success tabular-nums">200</Td>
                <Td align="right" class="tabular-nums">14ms</Td>
              </TRow>
              <TRow>
                <Td class="font-mono text-xs">07.06.26, 14:30:22</Td>
                <Td align="right" class="font-semibold text-error tabular-nums">500</Td>
                <Td align="right" class="tabular-nums">203ms</Td>
              </TRow>
            </tbody>
          </table>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApiStatusDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
