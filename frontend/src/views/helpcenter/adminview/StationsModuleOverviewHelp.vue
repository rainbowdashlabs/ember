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
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStationsOverview.title')" :subtitle="t('helpCenter.adminStationsOverview.subtitle')">
    <HelpSection :title="t('helpCenter.adminStationsOverview.whatIs')">
      <p>{{ t('helpCenter.adminStationsOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationsOverview.pagesTitle')">
      <p>{{ t('helpCenter.adminStationsOverview.pageStations') }}</p>
      <p>{{ t('helpCenter.adminStationsOverview.pageApplications') }}</p>
      <p>{{ t('helpCenter.adminStationsOverview.pageSettings') }}</p>
    </HelpSection>

    <!-- Dummy: Station list overview -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>{{ t('helpCenter.adminStationsOverview.dummyTitle') }}</SectionHeader>
      <div class="grid gap-4 sm:grid-cols-2">
        <PrimaryContainer class="flex flex-col items-center justify-center gap-3 cursor-pointer py-6 border-dashed">
          <font-awesome-icon :icon="['fas', 'plus']" class="text-2xl"/>
          <span class="font-medium">{{ t('helpCenter.adminStationsOverview.dummyCreate') }}</span>
        </PrimaryContainer>
        <NeutralContainer class="flex items-center justify-between py-4">
          <span class="font-medium">DLRG Musterstadt</span>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-4">
          <span class="font-medium">DLRG Beispielburg</span>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-4">
          <span class="font-medium">DLRG Neustadt</span>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.adminStationsOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
