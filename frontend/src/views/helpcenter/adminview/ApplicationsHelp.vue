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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
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

    <HelpSection :title="t('helpCenter.adminApplications.denyModalTitle')">
      <p>{{ t('helpCenter.adminApplications.denyModalText') }}</p>
    </HelpSection>

    <!-- Dummy: Applications page -->
    <HelpSection :title="t('helpCenter.adminApplications.exampleTitle')">
      <SectionHeader>{{ t('adminApplications.title') }}</SectionHeader>

      <!-- Tabs (raw buttons like real view) -->
      <div class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent mb-4">
        <button class="px-4 py-2 text-sm font-medium border-b-2 border-primary text-primary">
          {{ t('adminApplications.pending') }}
        </button>
        <button class="px-4 py-2 text-sm font-medium text-(--text-muted)">
          {{ t('adminApplications.all') }}
        </button>
      </div>

      <!-- Applications table -->
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
              <div class="text-xs text-(--text-muted) mt-0.5">Wir moechten unsere Wache digital verwalten.</div>
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
          <TRow>
            <Td>
              <div class="font-medium">Anna Schmidt</div>
            </Td>
            <Td muted>anna@schmidt.de</Td>
            <Td>DLRG Westhafen</Td>
            <Td muted>08.05.2026</Td>
            <Td>
              <ErrorBadge>{{ t('adminApplications.denied') }}</ErrorBadge>
            </Td>
            <Td>
              <div class="text-xs text-(--text-muted)">Doppelter Antrag</div>
            </Td>
          </TRow>
          </tbody>
        </table>
      </NeutralContainer>

      <!-- Deny modal snapshot -->
      <NeutralContainer class="mt-4 space-y-4">
        <SectionHeader>{{ t('adminApplications.denyTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">Lisa Beispiel — DLRG Neustadt</p>
        <div class="space-y-1">
          <FieldLabel>{{ t('adminApplications.denyReasonLabel') }}</FieldLabel>
          <TextInput :model-value="''" :placeholder="t('adminApplications.denyReasonPlaceholder')" />
        </div>
        <div class="flex justify-end gap-3">
          <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton>{{ t('adminApplications.deny') }}</ErrorButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApplications.tip') }}</HelpTip>
  </HelpArticle>
</template>
