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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const MODULES = [
  {label: 'moduleInventory', enabled: false},
  {label: 'moduleNews', enabled: true},
  {label: 'moduleEvents', enabled: true},
  {label: 'moduleAttendance', enabled: false},
  {label: 'moduleForms', enabled: false},
  {label: 'moduleLostAndFound', enabled: false},
  {label: 'moduleWaitingList', enabled: false},
  {label: 'moduleQuiz', enabled: false},
  {label: 'moduleTestProtocol', enabled: false},
  {label: 'moduleKnowledgeBase', enabled: true},
  {label: 'moduleBoards', enabled: false},
  {label: 'moduleProcedures', enabled: false},
] as const
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupModules.title')" :subtitle="t('helpCenter.setupModules.subtitle')">
    <HelpSection :title="t('helpCenter.setupModules.whatIs')">
      <p>{{ t('helpCenter.setupModules.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupModules.howTo')">
      <p>{{ t('helpCenter.setupModules.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupModules.howToStep2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupModules.exampleTitle')">
      <p>{{ t('helpCenter.setupModules.exampleText') }}</p>
      <WizardFrame step-id="modules">
        <div class="space-y-3">
          <div v-for="mod in MODULES" :key="mod.label" class="flex items-center gap-3">
            <ToggleInput :model-value="mod.enabled"/>
            <span class="text-sm font-medium">{{ t(`stationManage.${mod.label}`) }}</span>
          </div>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupModules.laterTitle')">
      <p>{{ t('helpCenter.setupModules.laterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupModules.tip') }}</HelpTip>
  </HelpArticle>
</template>
