/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminLegal.title')" :subtitle="t('helpCenter.adminLegal.subtitle')">
    <HelpSection :title="t('helpCenter.adminLegal.whatTitle')">
      <p>{{ t('helpCenter.adminLegal.whatText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminLegal.tip') }}</HelpTip>
  </HelpArticle>
</template>
