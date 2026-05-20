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
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.mailConfig.title')" :subtitle="t('helpCenter.mailConfig.subtitle')">
    <HelpSection :title="t('helpCenter.mailConfig.whatIs')">
      <p>{{ t('helpCenter.mailConfig.whatIsText') }}</p>
      <p>{{ t('helpCenter.mailConfig.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailConfig.providersTitle')">
      <p>{{ t('helpCenter.mailConfig.providersText') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerNone') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerSmtp') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerRapidmail') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerBrevo') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerSweego') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerTwilio') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailConfig.euTitle')">
      <p>{{ t('helpCenter.mailConfig.euText') }}</p>
      <p>{{ t('helpCenter.mailConfig.euRapidmail') }}</p>
      <p>{{ t('helpCenter.mailConfig.euBrevo') }}</p>
      <p>{{ t('helpCenter.mailConfig.euSweego') }}</p>
      <p>{{ t('helpCenter.mailConfig.euTwilio') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailConfig.freeTitle')">
      <p>{{ t('helpCenter.mailConfig.freeText') }}</p>
      <p>{{ t('helpCenter.mailConfig.freeBrevo') }}</p>
      <p>{{ t('helpCenter.mailConfig.freeSweego') }}</p>
      <p>{{ t('helpCenter.mailConfig.freeTwilio') }}</p>
      <p>{{ t('helpCenter.mailConfig.freeRapidmail') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailConfig.senderTitle')">
      <p>{{ t('helpCenter.mailConfig.senderText') }}</p>
      <p>{{ t('helpCenter.mailConfig.senderAddress') }}</p>
      <p>{{ t('helpCenter.mailConfig.senderName') }}</p>
    </HelpSection>

    <!-- Dummy: Provider selection -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.mailTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.mailHint') }}</p>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('stationManage.mailProvider') }}</label>
        <SelectInput model-value="BREVO" disabled>
          <option value="NONE">{{ t('stationManage.mailProviderNone') }}</option>
          <option value="RAPIDMAIL">RapidMail</option>
          <option value="TWILIO">Twilio</option>
          <option value="SWEEGO">Sweego</option>
          <option value="BREVO">Brevo</option>
          <option value="SMTP">{{ t('stationManage.mailProviderCustomSmtp') }}</option>
        </SelectInput>
      </div>

      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSenderAddress') }}</label>
          <TextInput model-value="noreply@jugendfeuerwehr.de" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSenderName') }}</label>
          <TextInput model-value="Jugendfeuerwehr Musterstadt" disabled/>
        </div>
      </div>

      <SubHeader>Brevo</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailBrevoHint') }}</p>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpUser') }}</label>
          <TextInput model-value="user@example.com" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">API Key</label>
          <TextInput model-value="" :placeholder="t('stationManage.mailApiKeyPlaceholder')" type="password" disabled/>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.mailConfig.smtpTitle')">
      <p>{{ t('helpCenter.mailConfig.smtpText') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpHost') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpPort') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpUser') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpPassword') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpSsl') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpProviderInfo') }}</p>
    </HelpSection>

    <!-- Dummy: Custom SMTP -->
    <NeutralContainer class="space-y-4">
      <SubHeader>SMTP</SubHeader>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailProviderName') }}</label>
          <TextInput model-value="Eigener Server" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailProviderUrl') }}</label>
          <TextInput model-value="https://example.com/privacy" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpHost') }}</label>
          <TextInput model-value="mail.example.com" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpPort') }}</label>
          <NumberInput :model-value="587" :min="1" :max="65535" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpUser') }}</label>
          <TextInput model-value="user@example.com" disabled/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpPassword') }}</label>
          <TextInput model-value="" :placeholder="t('stationManage.mailPasswordPlaceholder')" type="password" disabled/>
        </div>
      </div>
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">SSL</label>
        <ToggleInput :model-value="true" disabled/>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.mailConfig.limitsTitle')">
      <p>{{ t('helpCenter.mailConfig.limitsText') }}</p>
      <p>{{ t('helpCenter.mailConfig.limitsDaily') }}</p>
      <p>{{ t('helpCenter.mailConfig.limitsMonthly') }}</p>
      <p>{{ t('helpCenter.mailConfig.limitsUsage') }}</p>
    </HelpSection>

    <!-- Dummy: Limits -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('stationManage.mailLimits') }}</SubHeader>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailDailyLimit') }}</label>
          <NumberInput :model-value="300" :min="1" :max="10000" disabled/>
          <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSentToday', {count: 12, limit: 300}) }}</p>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailMonthlyLimit') }}</label>
          <NumberInput :model-value="9000" :min="1" :max="100000" disabled/>
          <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSentMonth', {count: 247, limit: 9000}) }}</p>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.mailConfig.testTitle')">
      <p>{{ t('helpCenter.mailConfig.testText') }}</p>
    </HelpSection>

    <!-- Dummy: Save & Test buttons -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center gap-2">
        <PrimaryButton disabled>{{ t('stationManage.save') }}</PrimaryButton>
        <SuccessButton disabled>
          <font-awesome-icon :icon="['fas', 'plug']" class="mr-1"/>
          {{ t('stationManage.mailTest') }}
        </SuccessButton>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.mailConfig.tip') }}</HelpTip>
  </HelpArticle>
</template>
