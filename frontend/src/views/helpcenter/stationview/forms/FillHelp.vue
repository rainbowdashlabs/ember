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
import InfoContainer from '@/components/container/InfoContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import BulletList from '@/components/typography/BulletList.vue'

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
    <InfoContainer class="mb-4">
      <div class="space-y-2">
        <FieldHint>{{ t('forms.fillForWhom') }}</FieldHint>
        <SelectInput model-value="self" disabled>
          <option value="self">{{ t('forms.fillForSelfDefault') }}</option>
          <option value="max">Max Mustermann</option>
        </SelectInput>
      </div>
    </InfoContainer>

    <!-- Text question -->
    <NeutralContainer class="space-y-3">
      <div>
        <p class="font-medium">Was hat dir zuletzt besonders gut gefallen? <span class="text-error">*</span></p>
        <p class="text-xs text-(--text-muted) mt-0.5">Schreib es einfach mit deinen eigenen Worten.</p>
      </div>
      <TextInput model-value="" :placeholder="t('forms.questionTitle')" disabled />
    </NeutralContainer>

    <!-- Choice question -->
    <NeutralContainer class="space-y-3">
      <div>
        <p class="font-medium">Was gefällt dir am besten?</p>
      </div>
      <div class="space-y-1">
        <div class="flex items-center gap-2 px-4 py-3 rounded-lg border-2 border-primary bg-primary/10 text-primary text-sm font-medium">
          <font-awesome-icon :icon="['fas', 'circle-dot']" class="text-primary shrink-0" />
          <span>{{ t('helpCenter.formsFill.dummyChoiceOption1') }}</span>
        </div>
        <div class="flex items-center gap-2 px-4 py-3 rounded-lg border-2 border-bg-light-accent dark:border-bg-dark-accent text-(--text) text-sm font-medium">
          <font-awesome-icon :icon="['fas', 'circle']" class="text-(--text-muted) shrink-0" />
          <span>{{ t('helpCenter.formsFill.dummyChoiceOption2') }}</span>
        </div>
      </div>
    </NeutralContainer>

    <!-- Rating question -->
    <NeutralContainer class="space-y-3">
      <div>
        <p class="font-medium">{{ t('helpCenter.formsFill.dummyRatingQuestion') }} <span class="text-error">*</span></p>
      </div>
      <div class="flex flex-wrap gap-1">
        <IconButton v-for="n in 5" :key="n" :icon="['fas', 'star']" :label="`Rating ${n}`"
                    :class="n <= 4 ? 'text-primary' : 'text-(--text-muted)'" class="text-2xl sm:text-xl p-1" disabled />
      </div>
    </NeutralContainer>

    <!-- Date question -->
    <NeutralContainer class="space-y-3">
      <div>
        <p class="font-medium">{{ t('helpCenter.formsFill.dummyDateQuestion') }}</p>
      </div>
      <DateInput model-value="2026-06-15" disabled />
    </NeutralContainer>

    <div class="flex justify-end mt-4">
      <PrimaryButton :icon="['fas', 'paper-plane']" disabled>
        {{ t('forms.submit') }}
      </PrimaryButton>
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

    <HelpSection :title="t('helpCenter.formsFill.memberManagerTitle')">
      <p>{{ t('helpCenter.formsFill.memberManagerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsFill.submitTitle')">
      <p>{{ t('helpCenter.formsFill.submitText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsFill.tip') }}</HelpTip>
  </HelpArticle>
</template>
