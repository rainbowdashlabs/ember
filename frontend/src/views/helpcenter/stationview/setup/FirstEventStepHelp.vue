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
import InfoContainer from '@/components/container/InfoContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupFirstEvent.title')" :subtitle="t('helpCenter.setupFirstEvent.subtitle')">
    <HelpSection :title="t('helpCenter.setupFirstEvent.whatIs')">
      <p>{{ t('helpCenter.setupFirstEvent.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFirstEvent.howTo')">
      <p>{{ t('helpCenter.setupFirstEvent.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupFirstEvent.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupFirstEvent.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFirstEvent.exampleTitle')">
      <p>{{ t('helpCenter.setupFirstEvent.exampleText') }}</p>
      <WizardFrame step-id="first-event" hide-actions>
        <InfoContainer class="space-y-2">
          <p class="font-medium text-sm">{{ t('setup.steps.first-event.aboutTitle') }}</p>
          <p class="text-sm">{{ t('setup.steps.first-event.aboutBody') }}</p>
        </InfoContainer>
        <div class="flex flex-wrap items-center gap-3">
          <PrimaryButton>{{ t('setup.steps.first-event.openEditor') }}</PrimaryButton>
          <span class="text-sm text-(--text-muted) underline">{{ t('setup.actions.skip') }}</span>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFirstEvent.moduleTitle')">
      <p>{{ t('helpCenter.setupFirstEvent.moduleText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupFirstEvent.tip') }}</HelpTip>
  </HelpArticle>
</template>
