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
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

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
        <TextInput model-value="Wie gefällt dir unsere Jugendfeuerwehr?" disabled />
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('forms.startAt') }}</label>
            <DateTimeInput disabled />
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('forms.endAt') }}</label>
            <DateTimeInput disabled />
          </div>
        </div>
        <div class="flex gap-6">
          <label class="flex items-center gap-2 text-sm opacity-60">
            <ToggleInput :model-value="false" disabled />
            {{ t('forms.shuffleQuestions') }}
          </label>
          <label class="flex items-center gap-2 text-sm opacity-60">
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.allowEdit') }}
          </label>
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
          <span class="text-xs font-semibold text-(--text-muted) uppercase">1. {{ t('forms.questionTypes.RATING') }}</span>
          <div class="flex gap-1">
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
            <DeleteButton disabled />
          </div>
        </div>
        <TextInput model-value="Wie zufrieden bist du insgesamt?" disabled />
        <TextInput model-value="1 = sehr unzufrieden, 5 = sehr zufrieden" disabled />
        <div class="flex gap-4">
          <label class="flex items-center gap-2 text-sm opacity-60">
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.questionRequired') }}
          </label>
        </div>
      </div>
    </NeutralContainer>

    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold text-(--text-muted) uppercase">2. {{ t('forms.questionTypes.CHOICE') }}</span>
          <div class="flex gap-1">
            <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
            <DeleteButton disabled />
          </div>
        </div>
        <TextInput model-value="Was gefällt dir am besten?" disabled />
        <div class="flex gap-4 flex-wrap opacity-60">
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.choice.multiSelect') }}
          </label>
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="false" disabled />
            {{ t('forms.choice.dropdown') }}
          </label>
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="true" disabled />
            {{ t('forms.choice.allowOther') }}
          </label>
        </div>
        <div class="space-y-1 opacity-60">
          <label class="text-xs text-(--text-muted)">{{ t('forms.choice.options') }}</label>
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
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('forms.questionTypes.CHOICE') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('forms.questionTypes.TEXT') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('forms.questionTypes.RATING') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('forms.questionTypes.DATE') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('forms.questionTypes.RANKING') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('forms.questionTypes.LIKERT') }}
      </SecondaryButton>
    </div>

    <HelpSection :title="t('helpCenter.formsBuilder.typesTitle')">
      <p>{{ t('helpCenter.formsBuilder.typesIntro') }}</p>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.CHOICE')">
      <p>{{ t('helpCenter.formsBuilder.typeChoiceText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li>{{ t('helpCenter.formsBuilder.typeChoiceMulti') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceDropdown') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceOther') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceLimit') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeChoiceShuffle') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.TEXT')">
      <p>{{ t('helpCenter.formsBuilder.typeTextText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li>{{ t('helpCenter.formsBuilder.typeTextLong') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.RATING')">
      <p>{{ t('helpCenter.formsBuilder.typeRatingText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li>{{ t('helpCenter.formsBuilder.typeRatingScale') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeRatingIcon') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.DATE')">
      <p>{{ t('helpCenter.formsBuilder.typeDateText') }}</p>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.RANKING')">
      <p>{{ t('helpCenter.formsBuilder.typeRankingText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li>{{ t('helpCenter.formsBuilder.typeRankingShuffle') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('forms.questionTypes.LIKERT')">
      <p>{{ t('helpCenter.formsBuilder.typeLikertText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li>{{ t('helpCenter.formsBuilder.typeLikertStatements') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeLikertScale') }}</li>
        <li>{{ t('helpCenter.formsBuilder.typeLikertShuffle') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsBuilder.restrictionsTitle')">
      <p>{{ t('helpCenter.formsBuilder.restrictionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Restrictions -->
    <NeutralContainer>
      <div class="space-y-3">
        <h3 class="text-sm font-semibold">{{ t('forms.restrictions.title') }}</h3>
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.roles') }}</label>
          <div class="flex flex-wrap gap-2">
            <span class="px-2 py-1 text-xs rounded border border-primary bg-primary/15 text-primary">Mitglied</span>
            <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Team</span>
          </div>
        </div>
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.groups') }}</label>
          <div class="flex flex-wrap gap-2">
            <span class="px-2 py-1 text-xs rounded border border-primary bg-primary/15 text-primary">Anfänger</span>
            <span class="px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">Fortgeschritten</span>
          </div>
        </div>
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted)">{{ t('forms.restrictions.tags') }}</label>
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
