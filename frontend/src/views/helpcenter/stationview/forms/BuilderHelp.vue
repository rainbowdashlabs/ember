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
import DummyMetadataPanel from '@/views/helpcenter/stationview/forms/builderhelp/DummyMetadataPanel.vue'
import DummyRatingQuestion from '@/views/helpcenter/stationview/forms/builderhelp/DummyRatingQuestion.vue'
import DummyChoiceQuestion from '@/views/helpcenter/stationview/forms/builderhelp/DummyChoiceQuestion.vue'
import DummyAddQuestionBar from '@/views/helpcenter/stationview/forms/builderhelp/DummyAddQuestionBar.vue'
import DummyRestrictionsPanel from '@/views/helpcenter/stationview/forms/builderhelp/DummyRestrictionsPanel.vue'
import DummyActionsBar from '@/views/helpcenter/stationview/forms/builderhelp/DummyActionsBar.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsBuilder.title')" :subtitle="t('helpCenter.formsBuilder.subtitle')">
    <HelpSection :title="t('helpCenter.formsBuilder.whatIs')">
      <p>{{ t('helpCenter.formsBuilder.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.metadataTitle')">
      <p>{{ t('helpCenter.formsBuilder.metadataText') }}</p>
      <DummyMetadataPanel/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.questionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.questionsText') }}</p>
      <DummyRatingQuestion/>
      <DummyChoiceQuestion/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.addQuestionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.addQuestionsText') }}</p>
      <DummyAddQuestionBar/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.typesTitle')">
      <p>{{ t('helpCenter.formsBuilder.typesIntro') }}</p>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.CHOICE')">
      <p>{{ t('helpCenter.formsBuilder.typeChoiceText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.formsBuilder.typeChoiceMulti') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceDropdown') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceOther') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceLimit') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceShuffle') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.TEXT')">
      <p>{{ t('helpCenter.formsBuilder.typeTextText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.formsBuilder.typeTextLong') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.RATING')">
      <p>{{ t('helpCenter.formsBuilder.typeRatingText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.formsBuilder.typeRatingScale') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeRatingIcon') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.DATE')">
      <p>{{ t('helpCenter.formsBuilder.typeDateText') }}</p>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.RANKING')">
      <p>{{ t('helpCenter.formsBuilder.typeRankingText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.formsBuilder.typeRankingShuffle') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.LIKERT')">
      <p>{{ t('helpCenter.formsBuilder.typeLikertText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.formsBuilder.typeLikertStatements') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeLikertScale') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeLikertShuffle') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.restrictionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.restrictionsText') }}</p>
      <p>{{ t('helpCenter.formsBuilder.restrictionsMemberText') }}</p>
      <DummyRestrictionsPanel/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.saveTitle')">
      <p>{{ t('helpCenter.formsBuilder.saveText') }}</p>
      <DummyActionsBar/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsBuilder.tip') }}</HelpTip>
  </HelpArticle>
</template>
