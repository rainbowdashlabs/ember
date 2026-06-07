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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.modules.title')" :subtitle="t('helpCenter.modules.subtitle')">
    <HelpSection :title="t('helpCenter.modules.whatIs')">
      <p>{{ t('helpCenter.modules.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.modules.howToTitle')">
      <p>{{ t('helpCenter.modules.howToText') }}</p>
    </HelpSection>

    <!-- Dummy: Module toggle list -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.modulesTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.modulesHint') }}</p>
      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleInventory') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleNews') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleEvents') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleAttendance') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleForms') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="false" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleLostAndFound') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="false" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleWaitingList') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleQuiz') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="false" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleTestProtocol') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleKnowledgeBase') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <ToggleInput :model-value="false" disabled/>
          <span class="text-sm font-medium">{{ t('stationManage.moduleBoards') }}</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.modules.tip') }}</HelpTip>
  </HelpArticle>
</template>
