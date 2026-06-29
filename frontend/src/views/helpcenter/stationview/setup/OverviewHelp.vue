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
  <HelpArticle :title="t('helpCenter.setup.overviewTitle')" :subtitle="t('helpCenter.setup.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.setup.whatIs')">
      <p>{{ t('helpCenter.setup.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.setup.requiredVsOptional')">
      <p>{{ t('helpCenter.setup.requiredVsOptionalText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.setup.dashboardChecklist')">
      <p>{{ t('helpCenter.setup.dashboardChecklistText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.setup.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
