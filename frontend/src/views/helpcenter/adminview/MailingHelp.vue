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
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminMailing.title')" :subtitle="t('helpCenter.adminMailing.subtitle')">
    <HelpSection :title="t('helpCenter.adminMailing.whatTitle')">
      <p>{{ t('helpCenter.adminMailing.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailing.fieldsTitle')">
      <p>{{ t('helpCenter.adminMailing.fieldsText') }}</p>
    </HelpSection>

    <!-- Dummy: Mailing config form -->
    <HelpSection :title="t('helpCenter.adminMailing.exampleTitle')">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminSettings.mailing.title') }}</SectionHeader>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.mailing.provider') }}</FieldLabel>
            <SelectInput :model-value="'SMTP'" class="w-full">
              <option value="NONE">NONE</option>
              <option value="SMTP">SMTP</option>
              <option value="RAPIDMAIL">RAPIDMAIL</option>
              <option value="TWILIO">TWILIO</option>
              <option value="SWEEGO">SWEEGO</option>
              <option value="BREVO">BREVO</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.mailing.senderAddress') }}</FieldLabel>
            <TextInput :model-value="'noreply@beispiel.de'" />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.mailing.senderName') }}</FieldLabel>
            <TextInput :model-value="'DLRG Plattform'" />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.mailing.apiKey') }}</FieldLabel>
            <TextInput :model-value="'••••••••'" />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.mailing.dailySendLimit') }}</FieldLabel>
            <NumberInput :model-value="200" />
            <MutedText tag="div" class="mt-1">{{ t('adminSettings.mailing.dailySendLimitHint') }}</MutedText>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.mailing.notificationDigestIntervalMinutes') }}</FieldLabel>
            <NumberInput :model-value="60" />
            <MutedText tag="div" class="mt-1">{{ t('adminSettings.mailing.notificationDigestIntervalMinutesHint') }}</MutedText>
          </div>
        </div>

        <div class="flex justify-end">
          <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminMailing.tip') }}</HelpTip>
  </HelpArticle>
</template>
