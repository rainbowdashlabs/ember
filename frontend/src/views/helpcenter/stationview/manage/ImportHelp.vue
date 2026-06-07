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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.import.title')" :subtitle="t('helpCenter.import.subtitle')">
    <HelpSection :title="t('helpCenter.import.whatIs')">
      <p>{{ t('helpCenter.import.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.import.importTitle')">
      <p>{{ t('helpCenter.import.importText') }}</p>
    </HelpSection>

    <!-- Dummy: Import section -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.importTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.importHint') }}</p>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('stationManage.importSourceUrl') }}</FieldLabel>
          <TextInput model-value="" :placeholder="t('stationManage.importSourceUrlPlaceholder')" disabled/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('stationManage.importToken') }}</FieldLabel>
          <TextInput model-value="" :placeholder="t('stationManage.importTokenPlaceholder')" disabled/>
        </div>
      </div>
      <PrimaryButton disabled>{{ t('stationManage.importStart') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.import.transferTitle')">
      <p>{{ t('helpCenter.import.transferText') }}</p>
    </HelpSection>

    <!-- Dummy: Transfer section -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.transferTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.transferHint') }}</p>
      <PrimaryButton disabled>{{ t('stationManage.transferCreate') }}</PrimaryButton>
      <div class="space-y-2">
        <FieldLabel>{{ t('stationManage.transferTokenLabel') }}</FieldLabel>
        <div class="flex items-center gap-2">
          <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">eyJhbGciOiJIUzI1NiJ9.beispiel-token</code>
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'copy']"/>
          </SecondaryButton>
        </div>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.import.tip') }}</HelpTip>
  </HelpArticle>
</template>
