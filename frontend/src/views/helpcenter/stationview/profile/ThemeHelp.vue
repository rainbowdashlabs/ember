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
import SubHeader from '@/components/typography/SubHeader.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.themeUser.title')" :subtitle="t('helpCenter.themeUser.subtitle')">
    <HelpSection :title="t('helpCenter.themeUser.whatIs')">
      <p>{{ t('helpCenter.themeUser.whatIsText') }}</p>
      <p>{{ t('helpCenter.themeUser.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.themeUser.pickTitle')">
      <p>{{ t('helpCenter.themeUser.pickText') }}</p>
    </HelpSection>

    <!-- Dummy: Theme selector -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('theme.title') }}</SubHeader>
      <ThemeSelector :show-themes="true" :show-feel="true"/>
      <p class="text-sm text-(--text-muted)">{{ t('theme.controlledByStation') }}</p>
      <p class="text-sm text-(--text-muted)">{{ t('theme.feelControlledByStation') }}</p>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.themeUser.restrictedTitle')">
      <p>{{ t('helpCenter.themeUser.restrictedText') }}</p>
      <p>{{ t('helpCenter.themeUser.restrictedControlled') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.themeUser.tip') }}</HelpTip>
  </HelpArticle>
</template>
