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
import DummyGeneralSettings from '@/views/helpcenter/adminview/settingshelp/DummyGeneralSettings.vue'
import DummyThemeSettings from '@/views/helpcenter/adminview/settingshelp/DummyThemeSettings.vue'
import DummyAuthSettings from '@/views/helpcenter/adminview/settingshelp/DummyAuthSettings.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminSettings.title')" :subtitle="t('helpCenter.adminSettings.subtitle')">
    <HelpSection :title="t('helpCenter.adminSettings.registrationTitle')">
      <p>{{ t('helpCenter.adminSettings.registrationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.mailLocaleTitle')">
      <p>{{ t('helpCenter.adminSettings.mailLocaleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.themeTitle')">
      <p>{{ t('helpCenter.adminSettings.themeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.authTitle')">
      <p>{{ t('helpCenter.adminSettings.authText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.exampleTitle')">
      <DummyGeneralSettings/>
      <DummyThemeSettings/>
      <DummyAuthSettings/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
