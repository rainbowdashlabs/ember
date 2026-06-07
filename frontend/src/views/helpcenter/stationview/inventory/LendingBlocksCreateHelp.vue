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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.lendingBlocksCreate.title')" :subtitle="t('helpCenter.lendingBlocksCreate.subtitle')">
    <HelpSection :title="t('helpCenter.lendingBlocksCreate.whatIs')">
      <p>{{ t('helpCenter.lendingBlocksCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingBlocksCreate.howTo')">
      <p>{{ t('helpCenter.lendingBlocksCreate.step1') }}</p>
      <p>{{ t('helpCenter.lendingBlocksCreate.step2') }}</p>
      <p>{{ t('helpCenter.lendingBlocksCreate.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Date range and reason -->
    <HelpSection :title="t('helpCenter.lendingBlocksCreate.dateTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.dateText') }}</p>

      <NeutralContainer class="space-y-4">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <FieldLabel class="mb-1">{{ t('lending.blockFrom') }}</FieldLabel>
            <DateInput model-value="2026-06-20" disabled />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('lending.blockTo') }}</FieldLabel>
            <DateInput model-value="2026-06-22" disabled />
          </div>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('lending.blockReason') }}</FieldLabel>
          <TextInput model-value="Stadtfest" disabled />
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Scope picker -->
    <HelpSection :title="t('helpCenter.lendingBlocksCreate.scopeTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.scopeText') }}</p>

      <MutedText tag="div" size="sm" class="mb-3">{{ t('helpCenter.lendingBlocksCreate.scopeHint') }}</MutedText>

      <!-- Dummy: inventory entry with "all items" toggled -->
      <NeutralContainer class="space-y-3 mb-3">
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Helme</SubHeader>
          <DeleteButton />
        </div>
        <FieldLabel inline class="cursor-pointer">
          <ToggleInput :model-value="true" disabled />
          {{ t('lending.blockItemAll') }}
        </FieldLabel>
      </NeutralContainer>

      <!-- Dummy: inventory entry with individual items selected -->
      <NeutralContainer class="space-y-3 mb-3">
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Jacken</SubHeader>
          <DeleteButton />
        </div>
        <FieldLabel inline class="cursor-pointer">
          <ToggleInput :model-value="false" disabled />
          {{ t('lending.blockItemAll') }}
        </FieldLabel>
        <div class="mt-3 flex flex-wrap gap-2">
          <FieldLabel inline class="cursor-pointer">
            <CheckboxInput :model-value="true" disabled />
            Jacke (INV-0010)
          </FieldLabel>
          <FieldLabel inline class="cursor-pointer">
            <CheckboxInput :model-value="true" disabled />
            Jacke (INV-0011)
          </FieldLabel>
          <FieldLabel inline class="cursor-pointer">
            <CheckboxInput :model-value="false" disabled />
            Jacke (INV-0012)
          </FieldLabel>
        </div>
      </NeutralContainer>

      <!-- Dummy: Add inventory dropdown -->
      <div class="flex gap-2 items-end">
        <div class="flex-1">
          <SelectInput :model-value="''">
            <option value="">{{ t('lending.selectInventory') }}</option>
            <option>Stiefel</option>
          </SelectInput>
        </div>
        <SecondaryButton :icon="['fas', 'plus']" disabled>
          {{ t('lending.addInventory') }}
        </SecondaryButton>
      </div>
    </HelpSection>

    <!-- Dummy: Action buttons -->
    <HelpSection :title="t('helpCenter.lendingBlocksCreate.actionsTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.actionsText') }}</p>

      <div class="flex justify-end gap-2">
        <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.lendingBlocksCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
