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
      :title="t('helpCenter.inventorySelfCheck.title')"
      :subtitle="t('helpCenter.inventorySelfCheck.subtitle')"
  >
    <HelpSection :title="t('helpCenter.inventorySelfCheck.whatIs')">
      <p>{{ t('helpCenter.inventorySelfCheck.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventorySelfCheck.whereText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheck.answersTitle')">
      <ul class="list-disc ps-5 space-y-1">
        <li>{{ t('helpCenter.inventorySelfCheck.answersHaveIt') }}</li>
        <li>{{ t('helpCenter.inventorySelfCheck.answersWrongRecord') }}</li>
        <li>{{ t('helpCenter.inventorySelfCheck.answersLost') }}</li>
        <li>{{ t('helpCenter.inventorySelfCheck.answersExchange') }}</li>
        <li>{{ t('helpCenter.inventorySelfCheck.answersNeverHad') }}</li>
        <li>{{ t('helpCenter.inventorySelfCheck.answersHaveOne') }}</li>
      </ul>
      <p>{{ t('helpCenter.inventorySelfCheck.answersBorrowedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheck.saveTitle')">
      <p>{{ t('helpCenter.inventorySelfCheck.saveText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheck.afterTitle')">
      <p>{{ t('helpCenter.inventorySelfCheck.afterText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventorySelfCheck.guardianTitle')">
      <p>{{ t('helpCenter.inventorySelfCheck.guardianText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventorySelfCheck.tip') }}</HelpTip>
  </HelpArticle>
</template>
