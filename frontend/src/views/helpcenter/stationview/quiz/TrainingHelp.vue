/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.trainingTitle')" :subtitle="t('helpCenter.quiz.trainingSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.trainingWhatIs')">
      <p>{{ t('helpCenter.quiz.trainingWhatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.trainingStep1')">
      <p>{{ t('helpCenter.quiz.trainingStep1Text') }}</p>
    </HelpSection>

    <!-- Dummy: Catalog selection -->
    <div class="space-y-2">
      <NeutralContainer>
        <label class="flex items-center gap-3 cursor-pointer">
          <ToggleInput :model-value="true" disabled />
          <div>
            <span class="font-medium">Brandschutz Grundlagen</span>
            <p class="text-xs text-(--text-muted)">12 Fragen</p>
          </div>
        </label>
      </NeutralContainer>
      <NeutralContainer>
        <label class="flex items-center gap-3 cursor-pointer">
          <ToggleInput :model-value="false" disabled />
          <div>
            <span class="font-medium">Erste Hilfe</span>
            <p class="text-xs text-(--text-muted)">8 Fragen</p>
          </div>
        </label>
      </NeutralContainer>
      <div class="flex justify-end">
        <PrimaryButton disabled>{{ t('quiz.training.start') }}</PrimaryButton>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.quiz.trainingStep2')">
      <p>{{ t('helpCenter.quiz.trainingStep2Text') }}</p>
    </HelpSection>

    <!-- Dummy: Question with progress -->
    <div class="space-y-3">
      <div class="space-y-1">
        <div class="flex justify-between text-sm text-(--text-muted)">
          <span>{{ t('quiz.training.progress') }}</span>
          <span>3 / 12</span>
        </div>
        <div class="w-full h-2 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
          <div class="h-full rounded-full bg-primary" style="width: 25%" />
        </div>
      </div>

      <NeutralContainer>
        <div class="space-y-3">
          <span class="text-xs font-semibold text-(--text-muted) uppercase">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</span>
          <h3 class="font-medium">Welche Nummer hat die Feuerwehr?</h3>
          <TextInput model-value="112" disabled />
        </div>
      </NeutralContainer>

      <div class="flex justify-end gap-3">
        <SecondaryButton disabled>
          <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" />
          {{ t('quiz.training.showAnswer') }}
        </SecondaryButton>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.quiz.trainingStep3')">
      <p>{{ t('helpCenter.quiz.trainingStep3Text') }}</p>
    </HelpSection>

    <!-- Dummy: Revealed answer -->
    <SuccessContainer>
      <div>
        <label class="text-xs font-semibold block mb-1">{{ t('quiz.training.correctAnswer') }}</label>
        <p class="text-sm">112</p>
      </div>
    </SuccessContainer>

    <HelpSection :title="t('helpCenter.quiz.trainingFinish')">
      <p>{{ t('helpCenter.quiz.trainingFinishText') }}</p>
    </HelpSection>

    <!-- Dummy: Finished -->
    <SuccessContainer>
      <div class="text-center space-y-2 py-2">
        <font-awesome-icon :icon="['fas', 'trophy']" class="text-2xl" />
        <p class="font-semibold">{{ t('quiz.training.finished') }}</p>
        <div class="flex justify-center gap-3">
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'redo']" class="mr-1" />
            {{ t('quiz.training.restart') }}
          </SecondaryButton>
          <PrimaryButton disabled>{{ t('common.back') }}</PrimaryButton>
        </div>
      </div>
    </SuccessContainer>

    <HelpTip>{{ t('helpCenter.quiz.trainingTip') }}</HelpTip>
  </HelpArticle>
</template>
