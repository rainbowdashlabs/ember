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
import ThemeDefaultsPanel from '@/views/stationview/manage/stationthemeview/ThemeDefaultsPanel.vue'
import CustomColorsPanel from '@/views/stationview/manage/stationthemeview/CustomColorsPanel.vue'
import LogoSection from '@/views/stationview/manage/stationview/LogoSection.vue'
import {DEFAULT_THEME} from '@/theme/themes'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const LOGO_MAX_SIZE = 2 * 1024 * 1024
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupBranding.title')" :subtitle="t('helpCenter.setupBranding.subtitle')">
    <HelpSection :title="t('helpCenter.setupBranding.whatIs')">
      <p>{{ t('helpCenter.setupBranding.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupBranding.howTo')">
      <p>{{ t('helpCenter.setupBranding.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupBranding.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupBranding.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupBranding.exampleTitle')">
      <p>{{ t('helpCenter.setupBranding.exampleText') }}</p>
      <WizardFrame step-id="branding" skippable>
        <ThemeDefaultsPanel :lock-theme="false" :lock-feel="false"/>
        <CustomColorsPanel :enabled="true" :colors="DEFAULT_THEME.colors" preset-key=""/>
        <LogoSection :has-logo="false" :logo-object-url="null" :uploading="false"
                     :logo-error="null" :max-size="LOGO_MAX_SIZE"/>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupBranding.lockTitle')">
      <p>{{ t('helpCenter.setupBranding.lockText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupBranding.tip') }}</HelpTip>
  </HelpArticle>
</template>
