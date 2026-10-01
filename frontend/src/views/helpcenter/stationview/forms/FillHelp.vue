/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import BulletList from '@/components/typography/BulletList.vue'
import MemberSelector from '@/views/stationview/forms/fillview/MemberSelector.vue'
import FillPages from '@/views/stationview/forms/fillview/FillPages.vue'
import {useFormWalk} from '@/composables/useFormWalk'
import {sampleAnswers, sampleFillTargets, samplePages, sampleQuestions} from '@/views/helpcenter/stationview/forms/fillhelp/fixtures'

const {t} = useI18n()

const answers = ref(sampleAnswers())
const walk = useFormWalk(ref(samplePages()), ref(sampleQuestions(t)), answers)
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsFill.title')" :subtitle="t('helpCenter.formsFill.subtitle')">
    <HelpSection :title="t('helpCenter.formsFill.whatIs')">
      <p>{{ t('helpCenter.formsFill.whatIsText') }}</p>
    </HelpSection>

    <div inert class="space-y-4">
      <MemberSelector :model-value="null" :options="sampleFillTargets(t)"/>
      <FillPages v-model:answers="answers" :walk="walk" :send-label="t('forms.submit')"/>
    </div>

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

    <HelpSection :title="t('helpCenter.formsFill.pagesTitle')">
      <p>{{ t('helpCenter.formsFill.pagesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsFill.continueTitle')">
      <p>{{ t('helpCenter.formsFill.continueText') }}</p>
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
