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
  <HelpArticle
      :title="t('helpCenter.inventorySelfCheckReview.title')"
      :subtitle="t('helpCenter.inventorySelfCheckReview.subtitle')"
  >
    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.whatIs')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.perRowTitle')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.perRowText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.correctTitle')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.correctText') }}</p>
      <p>{{ t('helpCenter.inventorySelfCheckReview.correctFateText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.refuseTitle')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.refuseText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.identifierTitle')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.identifierText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.approvalTitle')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.approvalText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheckReview.resultTitle')">
      <p>{{ t('helpCenter.inventorySelfCheckReview.resultText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventorySelfCheckReview.tip') }}</HelpTip>
  </HelpArticle>
</template>
