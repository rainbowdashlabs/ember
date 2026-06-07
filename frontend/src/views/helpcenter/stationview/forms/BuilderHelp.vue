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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import BulletList from '@/components/typography/BulletList.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsBuilder.title')" :subtitle="t('helpCenter.formsBuilder.subtitle')">
    <HelpSection :title="t('helpCenter.formsBuilder.whatIs')">
      <p>{{ t('helpCenter.formsBuilder.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.metadataTitle')">
      <p>{{ t('helpCenter.formsBuilder.metadataText') }}</p>
    </HelpSection>

    <!-- Dummy: Form metadata -->
    <NeutralContainer>
      <div class="space-y-3">
        <TextInput model-value="Zufriedenheitsumfrage" disabled />
        <TextAreaInput model-value="Wie gefällt dir unsere Jugendfeuerwehr?" disabled />
        <div class="grid grid-cols-2 gap-4">
          <div>
            <FieldLabel hint class="mb-1">{{ t('forms.startAt') }}</FieldLabel>
            <DateTimeInput disabled />
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('forms.endAt') }}</FieldLabel>
            <DateTimeInput disabled />
          </div>
        </div>
        <div class="flex gap-6">
          <FieldLabel inline class="opacity-60">
            <ToggleInput :model-value="false" disabled />
            {{ t('forms.shuffleQuestions') }}
          </FieldLabel>
          <FieldLabel inline class="opacity-60">
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.allowEdit') }}
          </FieldLabel>
          <FieldLabel inline class="opacity-60">
            <ToggleInput :model-value="false" disabled />
            {{ t('forms.forced') }}
          </FieldLabel>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.formsBuilder.questionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.questionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Question card -->
    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <SectionLabel>1. {{ t('forms.questionTypes.RATING') }}</SectionLabel>
          <div class="flex gap-1">
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
            <DeleteButton disabled />
          </div>
        </div>
        <TextInput model-value="Wie zufrieden bist du insgesamt?" disabled />
        <TextInput model-value="1 = sehr unzufrieden, 5 = sehr zufrieden" disabled />
        <div class="flex gap-4">
          <FieldLabel inline class="opacity-60">
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.questionRequired') }}
          </FieldLabel>
        </div>
      </div>
    </NeutralContainer>

    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <SectionLabel>2. {{ t('forms.questionTypes.CHOICE') }}</SectionLabel>
          <div class="flex gap-1">
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
            <DeleteButton disabled />
          </div>
        </div>
        <TextInput model-value="Was gefällt dir am besten?" disabled />
        <div class="flex gap-4 flex-wrap opacity-60">
          <FieldLabel inline>
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.choice.multiSelect') }}
          </FieldLabel>
          <FieldLabel inline>
            <ToggleInput :model-value="false" disabled />
            {{ t('forms.choice.dropdown') }}
          </FieldLabel>
          <FieldLabel inline>
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.choice.allowOther') }}
          </FieldLabel>
        </div>
        <div class="space-y-1 opacity-60">
          <FieldHint>{{ t('forms.choice.options') }}</FieldHint>
          <div class="flex gap-2 items-center">
            <TextInput model-value="Übungen" disabled class="flex-1" />
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
            <DeleteButton disabled />
          </div>
          <div class="flex gap-2 items-center">
            <TextInput model-value="Gemeinschaft" disabled class="flex-1" />
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
            <DeleteButton disabled />
          </div>
          <SecondaryButton disabled>{{ t('forms.choice.addOption') }}</SecondaryButton>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.formsBuilder.addQuestionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.addQuestionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Add question buttons -->
    <div class="flex flex-wrap gap-2">
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('forms.questionTypes.CHOICE') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('forms.questionTypes.TEXT') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('forms.questionTypes.RATING') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('forms.questionTypes.DATE') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('forms.questionTypes.RANKING') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('forms.questionTypes.LIKERT') }}
      </SecondaryButton>
    </div>

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
    </HelpSection>

    <!-- Dummy: Restrictions -->
    <NeutralContainer>
      <div class="space-y-3">
        <SubHeader class="text-sm">{{ t('forms.restrictions.title') }}</SubHeader>
        <div class="space-y-2">
          <FieldHint>{{ t('forms.restrictions.roles') }}</FieldHint>
          <div class="flex flex-wrap gap-2">
            <span class="px-2 py-1 text-xs rounded border border-primary bg-primary/15 text-primary">Mitglied</span>
            <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Team</span>
          </div>
        </div>
        <div class="space-y-2">
          <FieldHint>{{ t('forms.restrictions.groups') }}</FieldHint>
          <div class="flex flex-wrap gap-2">
            <span class="px-2 py-1 text-xs rounded border border-primary bg-primary/15 text-primary">Anfänger</span>
            <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Fortgeschritten</span>
          </div>
        </div>
        <div class="space-y-2">
          <FieldHint>{{ t('forms.restrictions.tags') }}</FieldHint>
          <div class="flex flex-wrap gap-2">
            <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Wettkampfgruppe</span>
          </div>
        </div>
        <p class="text-xs text-(--text-muted) italic">{{ t('forms.restrictions.noRestrictions') }}</p>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.formsBuilder.saveTitle')">
      <p>{{ t('helpCenter.formsBuilder.saveText') }}</p>
    </HelpSection>

    <!-- Dummy: Actions -->
    <div class="flex justify-end gap-3">
      <SecondaryButton disabled>{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton disabled>{{ t('common.save') }}</PrimaryButton>
    </div>

    <HelpTip>{{ t('helpCenter.formsBuilder.tip') }}</HelpTip>
  </HelpArticle>
</template>
