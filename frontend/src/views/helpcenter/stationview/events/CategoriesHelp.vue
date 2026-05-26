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
  <HelpArticle :title="t('categoryManage.title')" :subtitle="t('helpCenter.categories.subtitle')">
    <HelpSection :title="t('helpCenter.categories.whatIs')">
      <p>{{ t('helpCenter.categories.whatIsText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.categories.tip') }}</HelpTip>
  </HelpArticle>
</template>
