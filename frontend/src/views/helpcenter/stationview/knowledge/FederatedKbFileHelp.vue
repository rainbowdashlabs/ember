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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedKbFile.title')"
               :subtitle="t('helpCenter.federatedKbFile.subtitle')">
    <HelpSection :title="t('helpCenter.federatedKbFile.whatIs')">
      <p>{{ t('helpCenter.federatedKbFile.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedKbFile.howTo')">
      <p>{{ t('helpCenter.federatedKbFile.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedKbFile.howToStep2') }}</p>
      <p>{{ t('helpCenter.federatedKbFile.howToStep3') }}</p>

      <!-- Dummy: header bar of a shared file -->
      <NeutralContainer class="mt-4 space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <SecondaryButton compact>
            <font-awesome-icon :icon="['fas', 'chevron-left']"/>
            {{ t('kb.backToBrowse') }}
          </SecondaryButton>
          <PageHeader class="flex-1 !mb-0">Einsatzhinweise Höhenrettung</PageHeader>
          <PrimaryButton compact>
            <font-awesome-icon :icon="['fas', 'copy']"/>
            {{ t('federation.copyToStation') }}
          </PrimaryButton>
        </div>
        <StationBadge station-name="Wache Nordstadt"/>
        <MutedText tag="p" size="sm">Ablauf und Ausrüstung für den Höhenrettungseinsatz.</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedKbFile.editTitle')">
      <p>{{ t('helpCenter.federatedKbFile.editText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedKbFile.limitTitle')">
      <p>{{ t('helpCenter.federatedKbFile.limitText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedKbFile.tip') }}</HelpTip>
  </HelpArticle>
</template>
