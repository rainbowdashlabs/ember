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
import BulletList from '@/components/typography/BulletList.vue'
import DummyForm from '@/views/helpcenter/stationview/forms/fillhelp/DummyForm.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsFill.title')" :subtitle="t('helpCenter.formsFill.subtitle')">
    <HelpSection :title="t('helpCenter.formsFill.whatIs')">
      <p>{{ t('helpCenter.formsFill.whatIsText') }}</p>
    </HelpSection>

    <DummyForm/>

    <HelpSection :title="t('helpCenter.formsFill.questionsTitle')">
      <p>{{ t('helpCenter.formsFill.questionsText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.formsFill.typeChoice') }}</li>
        <li>{{ t('helpCenter.formsFill.typeText') }}</li>
        <li>{{ t('helpCenter.formsFill.typeRating') }}</li>
        <li>{{ t('helpCenter.formsFill.typeDate') }}</li>
        <li>{{ t('helpCenter.formsFill.typeRanking') }}</li>
        <li>{{ t('helpCenter.formsFill.typeLikert') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsFill.memberManagerTitle')">
      <p>{{ t('helpCenter.formsFill.memberManagerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsFill.submitTitle')">
      <p>{{ t('helpCenter.formsFill.submitText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsFill.tip') }}</HelpTip>
  </HelpArticle>
</template>
