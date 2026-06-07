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
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InlineDetail from '@/components/typography/InlineDetail.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLending.title')" :subtitle="t('helpCenter.inventoryLending.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLending.whatIs')">
      <p>{{ t('helpCenter.inventoryLending.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.tabsTitle')">
      <p>{{ t('helpCenter.inventoryLending.tabsOffersText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.tabsRequestsText') }}</p>
    </HelpSection>

    <!-- Dummy: Tab toggle -->
    <HelpSection :title="t('helpCenter.inventoryLending.dummyOffersTitle')">
      <NeutralContainer class="space-y-4">
        <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
          <SectionHeader>{{ t('helpCenter.inventoryLending.title') }}</SectionHeader>
          <PrimaryButton :icon="['fas', 'calendar-xmark']">
            {{ t('helpCenter.inventoryLending.dummyBlocks') }}
          </PrimaryButton>
        </div>

        <!-- Tabs -->
        <div class="flex gap-2">
          <SelectionToggleButton :selected="true">
            <font-awesome-icon :icon="['fas', 'boxes-stacked']" class="mr-1"/>
            {{ t('helpCenter.inventoryLending.tabOffers') }}
          </SelectionToggleButton>
          <SelectionToggleButton :selected="false">
            <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
            {{ t('helpCenter.inventoryLending.tabRequests') }}
          </SelectionToggleButton>
        </div>

        <!-- Search and date filters -->
        <TextInput :model-value="''" :placeholder="t('helpCenter.inventoryLending.dummySearch')" disabled />
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <FieldLabel class="mb-1">{{ t('helpCenter.inventoryLending.dummyDateFrom') }}</FieldLabel>
            <DateInput model-value="" disabled />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('helpCenter.inventoryLending.dummyDateTo') }}</FieldLabel>
            <DateInput model-value="" disabled />
          </div>
        </div>

        <!-- Available items -->
        <SubHeader>{{ t('helpCenter.inventoryLending.dummyAvailable') }}</SubHeader>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">Rettungsringe</span>
                <StationBadge station-name="DLRG Musterstadt" />
              </div>
              <span class="text-xs text-(--text-muted)">5 {{ t('helpCenter.inventoryLending.dummyAvailableCount') }}</span>
            </div>
            <PrimaryButton :icon="['fas', 'paper-plane']">
              {{ t('helpCenter.inventoryLending.dummyRequestItem') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">Taucheranzüge</span>
                <StationBadge station-name="DLRG Beispielburg" />
              </div>
              <span class="text-xs text-(--text-muted)">3 {{ t('helpCenter.inventoryLending.dummyAvailableCount') }}</span>
            </div>
            <PrimaryButton :icon="['fas', 'paper-plane']">
              {{ t('helpCenter.inventoryLending.dummyRequestItem') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Requests tab -->
    <HelpSection :title="t('helpCenter.inventoryLending.dummyRequestsTitle')">
      <p>{{ t('helpCenter.inventoryLending.requestsExplanation') }}</p>

      <NeutralContainer class="space-y-4">
        <!-- Incoming requests -->
        <SubHeader>{{ t('helpCenter.inventoryLending.dummyIncoming') }}</SubHeader>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-0.5">
              <div>
                <span class="font-medium">DLRG Neustadt</span>
                <InlineDetail>01.06.2026 - 15.06.2026</InlineDetail>
              </div>
              <span class="text-xs text-(--text-muted)">Rettungsring (3x)</span>
            </div>
            <SecondaryBadge>{{ t('helpCenter.inventoryLending.statusRequested') }}</SecondaryBadge>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-0.5">
              <div>
                <span class="font-medium">DLRG Beispielburg</span>
                <InlineDetail>10.05.2026 - 20.05.2026</InlineDetail>
              </div>
              <span class="text-xs text-(--text-muted)">Taucheranzug (1x)</span>
            </div>
            <SuccessBadge>{{ t('helpCenter.inventoryLending.statusLent') }}</SuccessBadge>
          </div>
        </NeutralContainer>

        <!-- Outgoing requests -->
        <SubHeader class="mt-4">{{ t('helpCenter.inventoryLending.dummyOutgoing') }}</SubHeader>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-0.5">
              <div>
                <span class="font-medium">DLRG Musterstadt</span>
                <InlineDetail>05.07.2026 - 10.07.2026</InlineDetail>
              </div>
              <span class="text-xs text-(--text-muted)">Erste-Hilfe-Koffer (2x)</span>
            </div>
            <SuccessBadge>{{ t('helpCenter.inventoryLending.statusApproved') }}</SuccessBadge>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-0.5">
              <div>
                <span class="font-medium">DLRG Neustadt</span>
                <InlineDetail>01.04.2026 - 05.04.2026</InlineDetail>
              </div>
              <span class="text-xs text-(--text-muted)">Schwimmweste (1x)</span>
            </div>
            <ErrorBadge>{{ t('helpCenter.inventoryLending.statusDeclined') }}</ErrorBadge>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-0.5">
              <div>
                <span class="font-medium">DLRG Musterstadt</span>
                <InlineDetail>20.03.2026 - 25.03.2026</InlineDetail>
              </div>
              <span class="text-xs text-(--text-muted)">Schwimmflossen (4x)</span>
            </div>
            <SecondaryBadge>{{ t('helpCenter.inventoryLending.statusReturned') }}</SecondaryBadge>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-0.5">
              <div>
                <span class="font-medium">DLRG Beispielburg</span>
                <InlineDetail>01.02.2026 - 10.02.2026</InlineDetail>
              </div>
              <span class="text-xs text-(--text-muted)">Taucherbrille (2x)</span>
            </div>
            <InfoBadge>{{ t('helpCenter.inventoryLending.statusClosed') }}</InfoBadge>
          </div>
        </NeutralContainer>
      </NeutralContainer>
    </HelpSection>

    <!-- Status explanations -->
    <HelpSection :title="t('helpCenter.inventoryLending.statusTitle')">
      <p>{{ t('helpCenter.inventoryLending.statusRequestedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusApprovedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusLentDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusReturnedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusDeclinedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusClosedDesc') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLending.tip') }}</HelpTip>
  </HelpArticle>
</template>
