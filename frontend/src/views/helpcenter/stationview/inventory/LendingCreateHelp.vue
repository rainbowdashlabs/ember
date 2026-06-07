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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingCreate.title')" :subtitle="t('helpCenter.inventoryLendingCreate.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingCreate.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingCreate.howTo')">
      <p>{{ t('helpCenter.inventoryLendingCreate.step1') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCreate.step2') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCreate.step3') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCreate.step4') }}</p>
    </HelpSection>

    <!-- Dummy: Selected inventory with station badge -->
    <HelpSection :title="t('helpCenter.inventoryLendingCreate.dummyTitle')">
      <NeutralContainer class="space-y-4">
        <!-- Inventory header with station -->
        <NeutralContainer>
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium text-lg">Rettungsringe</span>
            <StationBadge station-name="DLRG Musterstadt" />
          </div>
          <span class="text-sm text-(--text-muted)">5 {{ t('helpCenter.inventoryLendingCreate.dummyAvailable') }}</span>
        </NeutralContainer>

        <!-- Date range -->
        <div class="grid sm:grid-cols-2 gap-4">
          <div class="flex flex-col gap-1">
            <FieldLabel>{{ t('helpCenter.inventoryLendingCreate.fieldFrom') }}</FieldLabel>
            <DateInput model-value="2026-06-01" disabled />
          </div>
          <div class="flex flex-col gap-1">
            <FieldLabel>{{ t('helpCenter.inventoryLendingCreate.fieldTo') }}</FieldLabel>
            <DateInput model-value="2026-06-15" disabled />
          </div>
        </div>

        <!-- Quantity -->
        <div class="flex flex-col gap-1">
          <FieldLabel>{{ t('helpCenter.inventoryLendingCreate.fieldQuantity') }}</FieldLabel>
          <NumberInput :model-value="3" disabled />
          <span class="text-xs text-(--text-muted)">{{ t('helpCenter.inventoryLendingCreate.maxQuantityHint') }}</span>
        </div>

        <!-- Note -->
        <div class="flex flex-col gap-1">
          <FieldLabel>{{ t('helpCenter.inventoryLendingCreate.fieldNote') }}</FieldLabel>
          <TextAreaInput model-value="Wird für das Stadtfest benötigt." disabled :rows="3" />
        </div>

        <!-- Action buttons -->
        <div class="flex justify-end gap-2">
          <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']">
            {{ t('helpCenter.inventoryLendingCreate.dummySend') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
