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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryProcurement.title')" :subtitle="t('helpCenter.inventoryProcurement.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryProcurement.whatIs')">
      <p>{{ t('helpCenter.inventoryProcurement.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryProcurement.createTitle')">
      <p>{{ t('helpCenter.inventoryProcurement.createInventory') }}</p>
      <p>{{ t('helpCenter.inventoryProcurement.createMember') }}</p>
      <p>{{ t('helpCenter.inventoryProcurement.createSize') }}</p>
      <p>{{ t('helpCenter.inventoryProcurement.createNotes') }}</p>
    </HelpSection>

    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('procurement.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('procurement.create') }}
        </PrimaryButton>
      </div>

      <NeutralContainer>
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">{{ t('helpCenter.sample.equipment.helmets') }}</span>
              <span class="text-sm text-(--text-muted)">(L)</span>
              <ErrorBadge>{{ t('procurement.open') }}</ErrorBadge>
              <InfoBadge>{{ t('procurement.inStock', {n: 2}) }}</InfoBadge>
            </div>
            <div class="text-sm text-(--text-muted)">{{ t('helpCenter.sample.people.maxMustermann') }}, 10.05.2026</div>
            <div class="text-sm">{{ t('helpCenter.sample.equipment.helmetTorn') }}</div>
          </div>
          <ButtonRow class="shrink-0">
            <SecondaryButton :icon="['fas', 'box']">
              {{ t('procurement.fromStock') }}
            </SecondaryButton>
            <PrimaryButton :icon="['fas', 'check']">
              {{ t('procurement.markFulfilled') }}
            </PrimaryButton>
            <DeleteButton />
          </ButtonRow>
        </div>
      </NeutralContainer>

      <NeutralContainer>
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">{{ t('helpCenter.sample.equipment.jackets') }}</span>
              <span class="text-sm text-(--text-muted)">(M)</span>
              <SuccessBadge>{{ t('procurement.fulfilled') }}</SuccessBadge>
            </div>
            <div class="text-sm text-(--text-muted)">{{ t('helpCenter.sample.people.erikaMusterfrau') }}, 01.05.2026</div>
            <div class="text-xs text-(--text-muted)">{{ t('procurement.fulfilledAt') }}: 08.05.2026</div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <DeleteButton />
          </div>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.inventoryProcurement.fulfilledTitle')">
      <p>{{ t('helpCenter.inventoryProcurement.fulfilledText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryProcurement.fromStockTitle')">
      <p>{{ t('helpCenter.inventoryProcurement.fromStockText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryProcurement.tip') }}</HelpTip>
  </HelpArticle>
</template>
