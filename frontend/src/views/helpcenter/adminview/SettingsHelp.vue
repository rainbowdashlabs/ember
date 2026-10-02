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
import GeneralPanel from '@/views/adminview/adminsettingsview/GeneralPanel.vue'
import ThemePanel from '@/views/adminview/adminsettingsview/ThemePanel.vue'
import {DEFAULT_THEME_KEY, Feel} from '@/theme/themes'

const {t} = useI18n()

/** The mail languages an instance ships with, offered in the picker. */
const sampleMailLocales = ['de', 'en']

/** Saving does nothing in the help. */
async function noSave() {}
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

    <HelpSection :title="t('helpCenter.adminSettings.exampleTitle')">
      <div class="space-y-4">
        <GeneralPanel
          :registration-enabled="true"
          :available-mail-locales="sampleMailLocales"
          :force-pride-flag="false"
          default-mail-locale="de"
        />
        <ThemePanel :default-theme="DEFAULT_THEME_KEY" :default-feel="Feel.ROUNDED" :lock-feel="false" :save="noSave"/>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
