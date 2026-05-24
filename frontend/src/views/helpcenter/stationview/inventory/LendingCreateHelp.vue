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
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

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

    <!-- Dummy: Create request form -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('helpCenter.inventoryLendingCreate.dummyTitle') }}</SectionHeader>
      <div class="grid sm:grid-cols-2 gap-4">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('helpCenter.inventoryLendingCreate.fieldItem') }}</label>
          <TextInput model-value="Rettungsring" disabled />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('helpCenter.inventoryLendingCreate.fieldQuantity') }}</label>
          <NumberInput :model-value="3" disabled />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('helpCenter.inventoryLendingCreate.fieldFrom') }}</label>
          <DateInput model-value="2026-06-01" disabled />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('helpCenter.inventoryLendingCreate.fieldTo') }}</label>
          <DateInput model-value="2026-06-15" disabled />
        </div>
        <div class="flex flex-col gap-1 sm:col-span-2">
          <label class="text-sm font-medium">{{ t('helpCenter.inventoryLendingCreate.fieldNote') }}</label>
          <TextInput model-value="Wird für das Stadtfest benötigt." disabled />
        </div>
      </div>
      <PrimaryButton>
        <font-awesome-icon :icon="['fas', 'paper-plane']" class="mr-2" />
        {{ t('helpCenter.inventoryLendingCreate.dummySend') }}
      </PrimaryButton>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryLendingCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
