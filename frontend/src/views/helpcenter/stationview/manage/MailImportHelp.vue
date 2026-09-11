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
  <HelpArticle :title="t('helpCenter.mailImport.title')" :subtitle="t('helpCenter.mailImport.subtitle')">
    <HelpSection :title="t('helpCenter.mailImport.whatIs')">
      <p>{{ t('helpCenter.mailImport.whatIsText') }}</p>
      <p>{{ t('helpCenter.mailImport.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailImport.mailboxTitle')">
      <p>{{ t('helpCenter.mailImport.mailboxText') }}</p>
      <p>{{ t('helpCenter.mailImport.mailboxTestText') }}</p>
      <p>{{ t('helpCenter.mailImport.mailboxIntervalText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailImport.senderTitle')">
      <p>{{ t('helpCenter.mailImport.senderText') }}</p>
      <p>{{ t('helpCenter.mailImport.senderWarningText') }}</p>
      <p>{{ t('helpCenter.mailImport.senderDkimText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailImport.ruleTitle')">
      <p>{{ t('helpCenter.mailImport.ruleOrderText') }}</p>
      <p>{{ t('helpCenter.mailImport.ruleTypeText') }}</p>
      <p>{{ t('helpCenter.mailImport.ruleSizeText') }}</p>
      <p>{{ t('helpCenter.mailImport.ruleActionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailImport.memberTitle')">
      <p>{{ t('helpCenter.mailImport.memberText') }}</p>
      <p>{{ t('helpCenter.mailImport.memberSubjectText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.mailImport.logTitle')">
      <p>{{ t('helpCenter.mailImport.logText') }}</p>
      <p>{{ t('helpCenter.mailImport.logDuplicateText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.mailImport.tip') }}</HelpTip>
  </HelpArticle>
</template>
