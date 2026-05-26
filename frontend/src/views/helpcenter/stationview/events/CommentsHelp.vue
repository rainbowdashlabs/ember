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
  <HelpArticle :title="t('helpCenter.comments.title')" :subtitle="t('helpCenter.comments.subtitle')">
    <HelpSection :title="t('helpCenter.comments.whatIs')">
      <p>{{ t('helpCenter.comments.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.comments.threadingTitle')">
      <p>{{ t('helpCenter.comments.threadingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.comments.mentionTitle')">
      <p>{{ t('helpCenter.comments.mentionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.comments.editDeleteTitle')">
      <p>{{ t('helpCenter.comments.editDeleteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.comments.tip') }}</HelpTip>
  </HelpArticle>
</template>
