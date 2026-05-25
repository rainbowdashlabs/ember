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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminApplications.title')" :subtitle="t('helpCenter.adminApplications.subtitle')">
    <HelpSection :title="t('helpCenter.adminApplications.whatIs')">
      <p>{{ t('helpCenter.adminApplications.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApplications.processTitle')">
      <p>{{ t('helpCenter.adminApplications.accept') }}</p>
      <p>{{ t('helpCenter.adminApplications.deny') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApplications.tabsTitle')">
      <p>{{ t('helpCenter.adminApplications.tabsText') }}</p>
    </HelpSection>

    <!-- Dummy: Tabs -->
    <SectionHeader>{{ t('adminApplications.title') }}</SectionHeader>
    <div class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
      <SelectionToggleButton :selected="true">
        {{ t('adminApplications.pending') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="false">
        {{ t('adminApplications.all') }}
      </SelectionToggleButton>
    </div>

    <!-- Dummy: Applications table -->
    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
        <THead>
          <Th>{{ t('adminApplications.name') }}</Th>
          <Th>{{ t('adminApplications.email') }}</Th>
          <Th>{{ t('adminApplications.station') }}</Th>
          <Th>{{ t('adminApplications.date') }}</Th>
          <Th>{{ t('adminApplications.status') }}</Th>
          <th class="px-3 py-2"></th>
        </THead>
        </thead>
        <tbody>
        <TRow>
          <Td>
            <div class="font-medium">Lisa Beispiel</div>
            <div class="text-xs text-(--text-muted) mt-0.5">Wir möchten unsere Wache digital verwalten.</div>
          </Td>
          <Td muted>lisa@beispiel.de</Td>
          <Td>DLRG Neustadt</Td>
          <Td muted>12.05.2026</Td>
          <Td>
            <SecondaryBadge>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
          </Td>
          <Td align="right">
            <div class="flex items-center justify-end gap-1">
              <PrimaryButton>{{ t('adminApplications.accept') }}</PrimaryButton>
              <ErrorButton>{{ t('adminApplications.deny') }}</ErrorButton>
            </div>
          </Td>
        </TRow>
        <TRow>
          <Td>
            <div class="font-medium">Tom Muster</div>
          </Td>
          <Td muted>tom@muster.de</Td>
          <Td>DLRG Altstadt</Td>
          <Td muted>10.05.2026</Td>
          <Td>
            <SuccessBadge>{{ t('adminApplications.accepted') }}</SuccessBadge>
          </Td>
          <Td></Td>
        </TRow>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.adminApplications.tip') }}</HelpTip>
  </HelpArticle>
</template>
