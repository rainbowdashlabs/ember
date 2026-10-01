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
import FormPagesHelpSections from '@/views/helpcenter/stationview/forms/builderhelp/FormPagesHelpSections.vue'
import FormMetadataEditor from '@/views/stationview/forms/builderview/FormMetadataEditor.vue'
import QuestionEditor from '@/views/stationview/forms/builderview/QuestionEditor.vue'
import AddQuestionMenu from '@/views/stationview/forms/builderview/AddQuestionMenu.vue'
import FormRestrictionsEditor from '@/views/stationview/forms/builderview/FormRestrictionsEditor.vue'
import FormEditorActions from '@/views/stationview/forms/builderview/FormEditorActions.vue'
import {FormPurpose, FormVisibility, QUESTION_TYPES_BY_PURPOSE} from '@/api/forms'
import {
  sampleGroups,
  sampleLayoutEditor,
  sampleMembers,
  sampleRestriction,
  sampleTags,
} from '@/views/helpcenter/stationview/forms/builderhelp/fixtures'

const {t} = useI18n()

const layout = sampleLayoutEditor(t)
const shownQuestions = layout.pages.value[0]!.questions.slice(0, 2)
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsBuilder.title')" :subtitle="t('helpCenter.formsBuilder.subtitle')">
    <HelpSection :title="t('helpCenter.formsBuilder.whatIs')">
      <p>{{ t('helpCenter.formsBuilder.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.metadataTitle')">
      <p>{{ t('helpCenter.formsBuilder.metadataText') }}</p>
      <FormMetadataEditor inert :purpose="FormPurpose.INTERNAL" :title="t('helpCenter.sample.forms.survey')"
                          :description="t('helpCenter.sample.forms.surveyIntro')" start-at="" end-at=""
                          :shuffle-questions="false" allow-edit :forced="false" :visibility="FormVisibility.UNLISTED"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.questionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.questionsText') }}</p>
      <div inert class="space-y-3">
        <QuestionEditor v-for="(question, index) in shownQuestions" :key="question.id" :question="question"
                        :number="layout.numberOf(question)" :first="index === 0" :last="false" :other-pages="[]"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.addQuestionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.addQuestionsText') }}</p>
      <AddQuestionMenu :question-types="QUESTION_TYPES_BY_PURPOSE[FormPurpose.INTERNAL]"/>
    </HelpSection>

    <FormPagesHelpSections/>

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
      <FormRestrictionsEditor inert :model-value="sampleRestriction()" :groups="sampleGroups(t)" :tags="sampleTags(t)"
                              :members="sampleMembers()"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.saveTitle')">
      <p>{{ t('helpCenter.formsBuilder.saveText') }}</p>
      <FormEditorActions inert :save="() => undefined"/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsBuilder.tip') }}</HelpTip>
  </HelpArticle>
</template>
