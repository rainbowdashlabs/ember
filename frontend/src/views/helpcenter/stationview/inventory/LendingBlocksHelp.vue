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
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingBlocks.title')" :subtitle="t('helpCenter.inventoryLendingBlocks.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.createTitle')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep1') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep2') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep3') }}</p>
    </HelpSection>

    <!-- Dummy: Grouped blocks layout -->
    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.dummyTitle')">
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

        <!-- Block 1: Full block (all inventories) -->
        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">20.06.2026 – 22.06.2026</span>
                <PrimaryBadge>{{ t('helpCenter.inventoryLendingBlocks.dummyScopeAll') }}</PrimaryBadge>
              </div>
              <MutedText tag="div" size="sm" class="mt-1">Stadtfest 2026</MutedText>
            </div>
            <DeleteButton />
          </div>
        </NeutralContainer>

        <!-- Block 2: Specific inventory -->
        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">10.09.2026 – 11.09.2026</span>
              </div>
              <div class="mt-2 space-y-1">
                <div class="flex items-center gap-2 text-xs">
                  <SecondaryBadge>Helme</SecondaryBadge>
                  <MutedText size="sm">{{ t('helpCenter.inventoryLendingBlocks.dummyAllItems') }}</MutedText>
                </div>
                <div class="flex items-center gap-2 text-xs">
                  <SecondaryBadge>Jacken</SecondaryBadge>
                  <MutedText size="sm">Jacke (INV-0010), Jacke (INV-0011)</MutedText>
                </div>
              </div>
              <MutedText tag="div" size="sm" class="mt-1">Jahreshauptübung</MutedText>
            </div>
            <DeleteButton />
          </div>
        </NeutralContainer>

        <!-- Block 3: Inventory-specific only -->
        <NeutralContainer>
          <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">05.08.2026 – 07.08.2026</span>
              </div>
              <div class="mt-2 space-y-1">
                <div class="flex items-center gap-2 text-xs">
                  <SecondaryBadge>Stiefel</SecondaryBadge>
                  <MutedText size="sm">{{ t('helpCenter.inventoryLendingBlocks.dummyAllItems') }}</MutedText>
                </div>
              </div>
              <MutedText tag="div" size="sm" class="mt-1">Wachenwettbewerb</MutedText>
            </div>
            <DeleteButton />
          </div>
        </NeutralContainer>
      </NeutralContainer>
    </HelpSection>

    <!-- Badge explanation -->
    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.badgeTitle')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.badgeFullText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.badgeInventoryText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingBlocks.tip') }}</HelpTip>
  </HelpArticle>
</template>
