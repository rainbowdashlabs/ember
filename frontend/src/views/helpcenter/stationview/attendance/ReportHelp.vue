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
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceReport.title')" :subtitle="t('helpCenter.attendanceReport.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceReport.whatIs')">
      <p>{{ t('helpCenter.attendanceReport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceReport.filtersTitle')">
      <p>{{ t('helpCenter.attendanceReport.filterRoleGroup') }}</p>
      <p>{{ t('helpCenter.attendanceReport.filterPeriod') }}</p>
      <p>{{ t('helpCenter.attendanceReport.filterRounding') }}</p>
    </HelpSection>

    <!-- Dummy: Filter controls -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('attendanceReport.filters') }}</SubHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('attendanceReport.filterBy') }}</label>
          <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
            {{ t('attendanceReport.byRole') }}
          </div>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('attendanceReport.role') }}</label>
          <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
            MEMBER
          </div>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('attendanceReport.rounding') }}</label>
          <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
            {{ t('attendanceReport.roundingExact') }}
          </div>
        </div>
      </div>
      <div class="flex items-center gap-3 flex-wrap">
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'eye']" class="mr-1"/>
          {{ t('attendanceReport.preview') }}
        </PrimaryButton>
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'copy']" class="mr-1"/>
          {{ t('attendanceReport.savePreset') }}
        </SecondaryButton>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceReport.previewTitle')">
      <p>{{ t('helpCenter.attendanceReport.previewText') }}</p>
      <p>{{ t('helpCenter.attendanceReport.previewSave') }}</p>
    </HelpSection>

    <!-- Dummy: Report preview table -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('attendanceReport.summary') }} – {{ t('membersList.tabMember') }}</SubHeader>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <th class="text-left py-2 px-3">{{ t('attendanceReport.name') }}</th>
            <th class="text-center py-2 px-3">{{ t('attendanceReport.sessions') }}</th>
            <th class="text-center py-2 px-3">{{ t('attendanceReport.present') }}</th>
            <th class="text-right py-2 px-3">{{ t('attendanceReport.hours') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="py-2 px-3">Max Mustermann</td>
            <td class="text-center py-2 px-3">8</td>
            <td class="text-center py-2 px-3">7</td>
            <td class="text-right py-2 px-3 font-mono">14.0</td>
          </tr>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="py-2 px-3">Erika Muster</td>
            <td class="text-center py-2 px-3">8</td>
            <td class="text-center py-2 px-3">6</td>
            <td class="text-right py-2 px-3 font-mono">12.5</td>
          </tr>
          </tbody>
        </table>
      </div>
    </NeutralContainer>

    <div class="flex justify-end">
      <PrimaryButton>
        <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
        {{ t('attendanceReport.exportPdf') }}
      </PrimaryButton>
    </div>

    <HelpTip>{{ t('helpCenter.attendanceReport.tip') }}</HelpTip>
  </HelpArticle>
</template>
