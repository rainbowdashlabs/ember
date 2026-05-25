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
import ThemeSelector from '@/components/theme/ThemeSelector.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.theming.title')" :subtitle="t('helpCenter.theming.subtitle')">
    <HelpSection :title="t('helpCenter.theming.whatIs')">
      <p>{{ t('helpCenter.theming.whatIsText') }}</p>
    </HelpSection>

    <ThemeSelector />

    <HelpTip>{{ t('helpCenter.theming.tip') }}</HelpTip>
  </HelpArticle>
</template>
