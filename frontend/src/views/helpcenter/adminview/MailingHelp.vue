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
import NumberInput from '@/components/input/number/NumberInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MailingProviderExample from './mailinghelp/MailingProviderExample.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminMailing.title')" :subtitle="t('helpCenter.adminMailing.subtitle')">
    <HelpSection :title="t('helpCenter.adminMailing.whatTitle')">
      <p>{{ t('helpCenter.adminMailing.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailing.fieldsTitle')">
      <p>{{ t('helpCenter.adminMailing.fieldsText') }}</p>
      <p>{{ t('helpCenter.mailConfig.smtpEncryption') }}</p>
      <p>{{ t('helpCenter.mailConfig.providerKeysNote') }}</p>
      <p class="flex flex-wrap gap-x-4">
        <router-link
            v-for="(name, key) in RELAY_PROVIDER_NAMES"
            :key="key"
            :to="{name: 'help-station-mailing-vendor', params: {vendor: String(key).toLowerCase()}}"
            class="underline"
        >
          {{ name }}
        </router-link>
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailing.webhookTitle')">
      <p>{{ t('helpCenter.adminMailing.webhookText') }}</p>
      <p>{{ t('helpCenter.adminMailing.webhookKeyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailing.exampleTitle')">
      <MailingProviderExample/>

      <NeutralContainer class="space-y-2 mt-4">
        <SectionHeader>{{ t('adminSettings.mailing.instanceTitle') }}</SectionHeader>
        <FieldLabel>{{ t('adminSettings.mailing.digestInterval') }}</FieldLabel>
        <NumberInput :model-value="60" />
        <MutedText tag="div">{{ t('adminSettings.mailing.digestIntervalHint') }}</MutedText>
        <FieldLabel>{{ t('adminSettings.mailing.stationShare') }}</FieldLabel>
        <NumberInput :model-value="50" />
        <MutedText tag="div">{{ t('adminSettings.mailing.stationShareHint') }}</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailing.stationsTitle')">
      <p>{{ t('helpCenter.adminMailing.stationsText') }}</p>
      <p>{{ t('helpCenter.adminMailing.shareText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminMailing.tip') }}</HelpTip>
  </HelpArticle>
</template>
