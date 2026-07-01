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
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.checklist.detailTitle')" :subtitle="t('helpCenter.checklist.detailSubtitle')">
    <HelpSection :title="t('helpCenter.checklist.matrixTitle')">
      <p>{{ t('helpCenter.checklist.matrixText') }}</p>
      <NeutralContainer class="overflow-x-auto">
        <table class="text-sm">
          <thead>
          <tr class="text-left">
            <th class="p-2">{{ t('checklist.memberSet') }}</th>
            <th class="p-2 text-center">Briefing</th>
            <th class="p-2 text-center">Unterschrift</th>
          </tr>
          </thead>
          <tbody>
          <tr>
            <td class="p-2">Anna Sample</td>
            <td class="p-2 text-center"><ToggleInput :model-value="true" disabled/></td>
            <td class="p-2 text-center"><ToggleInput :model-value="false" disabled/></td>
          </tr>
          <tr>
            <td class="p-2 flex items-center gap-2">Bert Beispiel <InfoBadge>{{ t('checklist.notInFilter') }}</InfoBadge></td>
            <td class="p-2 text-center"><ToggleInput :model-value="true" disabled/></td>
            <td class="p-2 text-center"><ToggleInput :model-value="true" disabled/></td>
          </tr>
          <tr>
            <td class="p-2 flex items-center gap-2">Clara Demo <SecondaryBadge>{{ t('checklist.previouslyRemoved') }}</SecondaryBadge></td>
            <td class="p-2 text-center"><ToggleInput :model-value="false" disabled/></td>
            <td class="p-2 text-center"><ToggleInput :model-value="false" disabled/></td>
          </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.filterTitle')">
      <p>{{ t('helpCenter.checklist.filterText') }}</p>
      <ul class="list-disc pl-6 space-y-1">
        <li>{{ t('helpCenter.checklist.filterSearch') }}</li>
        <li>{{ t('helpCenter.checklist.filterColumn') }}</li>
        <li>{{ t('helpCenter.checklist.filterRemoved') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.bulkTitle')">
      <p>{{ t('helpCenter.checklist.bulkText') }}</p>
      <HelpTip>{{ t('helpCenter.checklist.bulkTip') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.noteTitle')">
      <p>{{ t('helpCenter.checklist.noteText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.columnsTitle')">
      <p>{{ t('helpCenter.checklist.columnsText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
