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
  <HelpArticle :title="t('helpCenter.adminBeaconFaults.title')"
               :subtitle="t('helpCenter.adminBeaconFaults.subtitle')">
    <HelpSection :title="t('helpCenter.adminBeaconFaults.whatIs')">
      <p>{{ t('helpCenter.adminBeaconFaults.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminBeaconFaults.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeaconFaults.readingTitle')">
      <p>{{ t('helpCenter.adminBeaconFaults.readingText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminBeaconFaults.tip') }}</HelpTip>
  </HelpArticle>
</template>
