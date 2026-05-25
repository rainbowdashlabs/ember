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
import ThemePicker from '@/components/theme/ThemePicker.vue'
import ThemeToggle from '@/components/theme/ThemeToggle.vue'

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

    <!-- Dummy: Theme picker -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('theme.selectTheme') }}</SubHeader>
      <ThemePicker />
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.themeUser.darkModeTitle')">
      <p>{{ t('helpCenter.themeUser.darkModeText') }}</p>
      <p>{{ t('helpCenter.themeUser.darkModeSystem') }}</p>
      <p>{{ t('helpCenter.themeUser.darkModeLight') }}</p>
      <p>{{ t('helpCenter.themeUser.darkModeDark') }}</p>
    </HelpSection>

    <!-- Dummy: Dark mode toggle -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('theme.darkMode') }}</SubHeader>
      <div class="flex items-center gap-2">
        <ThemeToggle />
        <span class="text-sm text-(--text-muted)">{{ t('helpCenter.themeUser.darkModeClickHint') }}</span>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.themeUser.restrictedTitle')">
      <p>{{ t('helpCenter.themeUser.restrictedText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.themeUser.tip') }}</HelpTip>
  </HelpArticle>
</template>
