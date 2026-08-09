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
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupFinish.title')" :subtitle="t('helpCenter.setupFinish.subtitle')">
    <HelpSection :title="t('helpCenter.setupFinish.whatIs')">
      <p>{{ t('helpCenter.setupFinish.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFinish.howTo')">
      <p>{{ t('helpCenter.setupFinish.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupFinish.howToStep2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFinish.exampleTitle')">
      <p>{{ t('helpCenter.setupFinish.exampleText') }}</p>
      <WizardFrame step-id="finish" hide-actions>
        <div class="space-y-6 text-center">
          <p>{{ t('setup.steps.finish.body') }}</p>
          <Alert variant="info">{{ t('setup.steps.finish.mailReminder') }}</Alert>
          <div class="flex flex-wrap items-center gap-3 justify-center pt-4">
            <PrimaryButton>{{ t('setup.steps.finish.takeTour') }}</PrimaryButton>
            <SecondaryButton>{{ t('setup.steps.finish.goDashboard') }}</SecondaryButton>
          </div>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFinish.missingTitle')">
      <p>{{ t('helpCenter.setupFinish.missingText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupFinish.tip') }}</HelpTip>
  </HelpArticle>
</template>
