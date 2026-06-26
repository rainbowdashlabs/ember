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
import InfoContainer from '@/components/container/InfoContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'
import DummyEmailSettings from './settingshelp/DummyEmailSettings.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.settings.title')" :subtitle="t('helpCenter.settings.subtitle')">
    <HelpSection :title="t('helpCenter.settings.themeTitle')">
      <p>{{ t('helpCenter.settings.themeText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('theme.title') }}</SubHeader>
      <ThemeSelector :show-themes="true" :show-feel="true"/>
      <p class="text-sm text-(--text-muted)">{{ t('theme.controlledByStation') }}</p>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.settings.emailTitle')">
      <p>{{ t('helpCenter.settings.emailText') }}</p>
    </HelpSection>

    <InfoContainer class="space-y-2">
      <p class="text-sm">
        {{ t('userSettings.mailProviderInfo', {provider: 'Beispiel Mail GmbH'}) }}
      </p>
      <p class="text-xs">
        <a href="#" class="text-primary hover:underline">
          {{ t('userSettings.mailProviderPrivacy') }}
        </a>
      </p>
    </InfoContainer>

    <DummyEmailSettings/>

    <HelpSection :title="t('helpCenter.settings.typesTitle')">
      <p>{{ t('helpCenter.settings.typesText') }}</p>
      <p>{{ t('helpCenter.settings.typeNews') }}</p>
      <p>{{ t('helpCenter.settings.typeComments') }}</p>
      <p>{{ t('helpCenter.settings.typeEvents') }}</p>
      <p>{{ t('helpCenter.settings.typeEventChanges') }}</p>
      <p>{{ t('helpCenter.settings.typeExchanges') }}</p>
      <p>{{ t('helpCenter.settings.typeGroups') }}</p>
      <p>{{ t('helpCenter.settings.typeProfile') }}</p>
      <p>{{ t('helpCenter.settings.typeProcurement') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.settings.tip') }}</HelpTip>
  </HelpArticle>
</template>
