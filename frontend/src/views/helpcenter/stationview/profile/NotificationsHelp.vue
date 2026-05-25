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
  <HelpArticle :title="t('helpCenter.notifications.title')" :subtitle="t('helpCenter.notifications.subtitle')">
    <HelpSection :title="t('helpCenter.notifications.whatIs')">
      <p>{{ t('helpCenter.notifications.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.notifications.typesTitle')">
      <p>{{ t('helpCenter.notifications.typesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.notifications.tip') }}</HelpTip>
  </HelpArticle>
</template>
