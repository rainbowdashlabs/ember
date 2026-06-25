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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import DummySessionsList from './sessionshelp/DummySessionsList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.sessions.title')" :subtitle="t('helpCenter.sessions.subtitle')">
    <HelpSection :title="t('helpCenter.sessions.whatIs')">
      <p>{{ t('helpCenter.sessions.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.sessions.listTitle')">
      <p>{{ t('helpCenter.sessions.listText') }}</p>
    </HelpSection>

    <DummySessionsList/>

    <HelpSection :title="t('helpCenter.sessions.gdprTitle')">
      <p>{{ t('helpCenter.sessions.gdprText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('userSettings.gdprTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('userSettings.gdprHint') }}</p>
      <DownloadButton disabled>{{ t('userSettings.gdprExport') }}</DownloadButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.sessions.deleteTitle')">
      <p>{{ t('helpCenter.sessions.deleteText') }}</p>
    </HelpSection>

    <ErrorContainer class="space-y-3">
      <SubHeader>{{ t('userSettings.deleteTitle') }}</SubHeader>
      <p class="text-sm">{{ t('userSettings.deleteWarning') }}</p>
      <ErrorButton :icon="['fas', 'trash']" disabled>
        {{ t('userSettings.deleteAccount') }}
      </ErrorButton>
    </ErrorContainer>

    <HelpTip>{{ t('helpCenter.sessions.tip') }}</HelpTip>
  </HelpArticle>
</template>
