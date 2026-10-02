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
import CheckOverviewTabs from '@/views/stationview/inventory/checkoverviewview/CheckOverviewTabs.vue'
import MemberCheckTable from '@/views/stationview/inventory/checkoverviewview/MemberCheckTable.vue'
import {currentMemberId, members} from './checkoverviewhelp/fixtures'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryChecks.title')" :subtitle="t('helpCenter.inventoryChecks.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryChecks.whatIs')">
      <p>{{ t('helpCenter.inventoryChecks.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryChecks.howToTitle')">
      <p>{{ t('helpCenter.inventoryChecks.step1') }}</p>
      <p>{{ t('helpCenter.inventoryChecks.step2') }}</p>
      <p>{{ t('helpCenter.inventoryChecks.step3') }}</p>
      <p>{{ t('helpCenter.inventoryChecks.step4') }}</p>
      <div class="space-y-3">
        <CheckOverviewTabs active-tab="team"/>
        <MemberCheckTable :members="members" :current-member-id="currentMemberId"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryChecks.sortTitle')">
      <p>{{ t('helpCenter.inventoryChecks.sortText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryChecks.lastCheckTitle')">
      <p>{{ t('helpCenter.inventoryChecks.lastCheckText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryChecks.resultsTitle')">
      <p>{{ t('helpCenter.inventoryChecks.resultsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryChecks.tip') }}</HelpTip>
  </HelpArticle>
</template>
