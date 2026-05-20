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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsFill.title')" :subtitle="t('helpCenter.formsFill.subtitle')">
    <HelpSection :title="t('helpCenter.formsFill.whatIs')">
      <p>{{ t('helpCenter.formsFill.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Fill form view -->
    <SectionHeader>Zufriedenheitsumfrage</SectionHeader>
    <p class="text-sm text-(--text-muted) mb-4">Wie gefällt dir unsere Jugendfeuerwehr? Bitte beantworte alle Fragen ehrlich.</p>

    <!-- Member selector for member managers -->
    <NeutralContainer class="mb-4">
      <div class="space-y-2">
        <label class="text-xs text-(--text-muted)">{{ t('forms.fillForWhom') }}</label>
        <SelectInput model-value="self" disabled>
          <option value="self">{{ t('forms.fillForSelfDefault') }}</option>
          <option value="max">Max Mustermann</option>
        </SelectInput>
      </div>
    </NeutralContainer>

    <!-- Text question -->
    <NeutralContainer class="space-y-3">
      <div>
        <p class="font-medium">Was hat dir zuletzt besonders gut gefallen? <span class="text-error">*</span></p>
        <p class="text-xs text-(--text-muted) mt-0.5">Schreib es einfach mit deinen eigenen Worten.</p>
      </div>
      <TextInput model-value="" :placeholder="t('forms.questionTitle')" disabled />
    </NeutralContainer>

    <div class="flex justify-end mt-4">
      <PrimaryButton disabled>
        <font-awesome-icon :icon="['fas', 'paper-plane']" class="mr-2"/>
        {{ t('forms.submit') }}
      </PrimaryButton>
    </div>

    <HelpSection :title="t('helpCenter.formsFill.questionsTitle')">
      <p>{{ t('helpCenter.formsFill.questionsText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li>{{ t('helpCenter.formsFill.typeChoice') }}</li>
        <li>{{ t('helpCenter.formsFill.typeText') }}</li>
        <li>{{ t('helpCenter.formsFill.typeRating') }}</li>
        <li>{{ t('helpCenter.formsFill.typeDate') }}</li>
        <li>{{ t('helpCenter.formsFill.typeRanking') }}</li>
        <li>{{ t('helpCenter.formsFill.typeLikert') }}</li>
      </ul>
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
