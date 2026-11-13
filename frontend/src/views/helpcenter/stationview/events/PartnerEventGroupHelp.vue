/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedEventGroup.title')" :subtitle="t('helpCenter.federatedEventGroup.subtitle')">
    <HelpSection :title="t('helpCenter.federatedEventGroup.whatIs')">
      <p>{{ t('helpCenter.federatedEventGroup.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.federatedEventGroup.signUpTitle')">
      <p>{{ t('helpCenter.federatedEventGroup.signUpText') }}</p>
      <p>{{ t('helpCenter.federatedEventGroup.partsText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
