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
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupWelcome.title')" :subtitle="t('helpCenter.setupWelcome.subtitle')">
    <HelpSection :title="t('helpCenter.setupWelcome.whatIs')">
      <p>{{ t('helpCenter.setupWelcome.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupWelcome.howTo')">
      <p>{{ t('helpCenter.setupWelcome.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupWelcome.howToStep2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupWelcome.exampleTitle')">
      <p>{{ t('helpCenter.setupWelcome.exampleText') }}</p>
      <WizardFrame step-id="welcome" :save-label="t('setup.actions.getStarted')">
        <p>{{ t('setup.steps.welcome.body1') }}</p>
        <p>{{ t('setup.steps.welcome.body2') }}</p>
        <ul class="list-disc list-inside text-sm text-(--text-muted) space-y-1">
          <li>{{ t('setup.steps.welcome.bullet1') }}</li>
          <li>{{ t('setup.steps.welcome.bullet2') }}</li>
          <li>{{ t('setup.steps.welcome.bullet3') }}</li>
        </ul>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupWelcome.leaveTitle')">
      <p>{{ t('helpCenter.setupWelcome.leaveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupWelcome.tip') }}</HelpTip>
  </HelpArticle>
</template>
