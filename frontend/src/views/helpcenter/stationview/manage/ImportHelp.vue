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

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.import.title')" :subtitle="t('helpCenter.import.subtitle')">
    <HelpSection :title="t('helpCenter.import.whatIs')">
      <p>{{ t('helpCenter.import.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.import.transferTitle')">
      <p>{{ t('helpCenter.import.transferText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.import.tip') }}</HelpTip>
  </HelpArticle>
</template>
