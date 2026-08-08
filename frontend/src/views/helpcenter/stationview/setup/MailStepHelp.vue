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
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupMail.title')" :subtitle="t('helpCenter.setupMail.subtitle')">
    <HelpSection :title="t('helpCenter.setupMail.whatIs')">
      <p>{{ t('helpCenter.setupMail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupMail.howTo')">
      <p>{{ t('helpCenter.setupMail.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupMail.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupMail.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupMail.exampleTitle')">
      <p>{{ t('helpCenter.setupMail.exampleText') }}</p>
      <WizardFrame step-id="mail" skippable>
        <InfoContainer class="space-y-1">
          <p class="font-medium text-sm">{{ t('setup.steps.mail.worksWithoutTitle') }}</p>
          <p class="text-sm">{{ t('setup.steps.mail.worksWithoutBody') }}</p>
        </InfoContainer>
        <p class="text-sm text-(--text-muted)">{{ t('setup.steps.mail.scopeNote') }}</p>
        <div class="space-y-3">
          <label class="block text-sm">
            {{ t('setup.steps.mail.provider') }}
            <SelectInput model-value="SMTP">
              <option value="SMTP">SMTP</option>
            </SelectInput>
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.mail.senderAddress') }}
            <TextInput model-value="wache@example.org"/>
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.mail.senderName') }}
            <TextInput model-value="Feuerwehr Musterstadt"/>
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.mail.smtpHost') }}
            <TextInput model-value="mail.example.org"/>
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.mail.smtpPort') }}
            <NumberInput :model-value="587"/>
          </label>
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="true"/>
            {{ t('setup.steps.mail.smtpSsl') }}
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.mail.smtpUser') }}
            <TextInput model-value="wache@example.org"/>
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.mail.smtpPassword') }}
            <TextInput model-value="geheim" type="password"/>
          </label>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupMail.withoutTitle')">
      <p>{{ t('helpCenter.setupMail.withoutText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupMail.tip') }}</HelpTip>
  </HelpArticle>
</template>
