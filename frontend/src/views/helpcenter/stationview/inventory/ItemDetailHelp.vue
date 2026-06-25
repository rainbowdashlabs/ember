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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import MetadataSection from './itemdetailhelp/MetadataSection.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.itemDetail.title')" :subtitle="t('helpCenter.itemDetail.subtitle')">
    <HelpSection :title="t('helpCenter.itemDetail.whatIs')">
      <p>{{ t('helpCenter.itemDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.itemDetail.howTo')">
      <p>{{ t('helpCenter.itemDetail.howToText') }}</p>
    </HelpSection>

    <MetadataSection/>

    <!-- Dummy: Action buttons -->
    <HelpSection :title="t('helpCenter.itemDetail.actionsTitle')">
      <p>{{ t('helpCenter.itemDetail.actionsText') }}</p>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('itemDetail.actions') }}</SubHeader>
        <div class="flex flex-wrap gap-2">
          <PrimaryButton :icon="['fas', 'user-plus']">
            {{ t('itemDetail.assign') }}
          </PrimaryButton>
          <SecondaryButton :icon="['fas', 'user-minus']">
            {{ t('itemDetail.unassign') }}
          </SecondaryButton>
          <ErrorButton :icon="['fas', 'triangle-exclamation']">
            {{ t('itemDetail.markLost') }}
          </ErrorButton>
          <SuccessButton :icon="['fas', 'check']">
            {{ t('itemDetail.markFound') }}
          </SuccessButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Assign modal -->
    <HelpSection :title="t('helpCenter.itemDetail.assignModalTitle')">
      <p>{{ t('helpCenter.itemDetail.assignModalText') }}</p>

      <NeutralContainer class="space-y-3">
        <SectionHeader>{{ t('itemDetail.assignTitle') }}</SectionHeader>
        <SelectInput :model-value="''">
          <option value="">{{ t('itemDetail.selectMember') }}</option>
          <option>Max Mustermann</option>
          <option>Erika Musterfrau</option>
        </SelectInput>
        <div class="flex justify-end gap-2">
          <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton disabled>{{ t('itemDetail.assign') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Assignment history table -->
    <HelpSection :title="t('helpCenter.itemDetail.historyTitle')">
      <p>{{ t('helpCenter.itemDetail.historyText') }}</p>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('itemDetail.history') }}</SubHeader>
        <NeutralContainer class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <THead>
                <Th>{{ t('itemDetail.member') }}</Th>
                <Th>{{ t('itemDetail.givenOut') }}</Th>
                <Th>{{ t('itemDetail.returned') }}</Th>
              </THead>
            </thead>
            <tbody>
              <TRow>
                <Td class="font-medium">Max Mustermann</Td>
                <Td muted>15.03.2026</Td>
                <Td muted>{{ t('itemDetail.current') }}</Td>
              </TRow>
              <TRow>
                <Td class="font-medium">Jan Schmidt</Td>
                <Td muted>10.01.2026</Td>
                <Td muted>14.03.2026</Td>
              </TRow>
              <TRow>
                <Td class="font-medium">Erika Musterfrau</Td>
                <Td muted>01.09.2025</Td>
                <Td muted>09.01.2026</Td>
              </TRow>
            </tbody>
          </table>
        </NeutralContainer>
      </NeutralContainer>
    </HelpSection>

    <!-- Notes -->
    <HelpSection :title="t('helpCenter.itemDetail.notesTitle')">
      <p>{{ t('helpCenter.itemDetail.notesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.itemDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
