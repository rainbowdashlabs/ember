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
        <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
          <th class="px-3 py-2 font-medium">{{ t('adminApplications.name') }}</th>
          <th class="px-3 py-2 font-medium">{{ t('adminApplications.email') }}</th>
          <th class="px-3 py-2 font-medium">{{ t('adminApplications.station') }}</th>
          <th class="px-3 py-2 font-medium">{{ t('adminApplications.date') }}</th>
          <th class="px-3 py-2 font-medium">{{ t('adminApplications.status') }}</th>
          <th class="px-3 py-2"></th>
        </tr>
        </thead>
        <tbody>
        <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <td class="px-3 py-2.5">
            <div class="font-medium">Lisa Beispiel</div>
            <div class="text-xs text-(--text-muted) mt-0.5">Wir möchten unsere Wache digital verwalten.</div>
          </td>
          <td class="px-3 py-2.5 text-(--text-muted)">lisa@beispiel.de</td>
          <td class="px-3 py-2.5">DLRG Neustadt</td>
          <td class="px-3 py-2.5 text-(--text-muted)">12.05.2026</td>
          <td class="px-3 py-2.5">
            <SecondaryBadge>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
          </td>
          <td class="px-3 py-2.5 text-right">
            <div class="flex items-center justify-end gap-1">
              <PrimaryButton>{{ t('adminApplications.accept') }}</PrimaryButton>
              <ErrorButton>{{ t('adminApplications.deny') }}</ErrorButton>
            </div>
          </td>
        </tr>
        <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <td class="px-3 py-2.5">
            <div class="font-medium">Tom Muster</div>
          </td>
          <td class="px-3 py-2.5 text-(--text-muted)">tom@muster.de</td>
          <td class="px-3 py-2.5">DLRG Altstadt</td>
          <td class="px-3 py-2.5 text-(--text-muted)">10.05.2026</td>
          <td class="px-3 py-2.5">
            <SuccessBadge>{{ t('adminApplications.accepted') }}</SuccessBadge>
          </td>
          <td class="px-3 py-2.5"></td>
        </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.adminApplications.tip') }}</HelpTip>
  </HelpArticle>
</template>
