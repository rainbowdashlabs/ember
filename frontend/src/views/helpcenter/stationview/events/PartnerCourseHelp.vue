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
  <HelpArticle :title="t('helpCenter.federatedCourse.title')" :subtitle="t('helpCenter.federatedCourse.subtitle')">
    <HelpSection :title="t('helpCenter.federatedCourse.whatIs')">
      <p>{{ t('helpCenter.federatedCourse.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.federatedCourse.signUpTitle')">
      <p>{{ t('helpCenter.federatedCourse.signUpText') }}</p>
      <p>{{ t('helpCenter.federatedCourse.partsText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
