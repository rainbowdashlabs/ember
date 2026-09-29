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
import StationModuleToggle from '@/components/modules/StationModuleToggle.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'
import {StationModules, type StationModuleName} from '@/api/types'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'

const {t} = useI18n()

/** What a new station typically starts with. */
const ENABLED = new Set<StationModuleName>([StationModules.NEWS, StationModules.EVENTS, StationModules.KNOWLEDGE_BASE])
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
          <StationModuleToggle
              v-for="mod in STATION_MODULE_OPTIONS"
              :key="mod.value"
              :module="mod"
              :model-value="ENABLED.has(mod.value)"
              disabled
          />
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupModules.laterTitle')">
      <p>{{ t('helpCenter.setupModules.laterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupModules.tip') }}</HelpTip>
  </HelpArticle>
</template>
