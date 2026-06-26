/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const { t } = useI18n()
</script>

<template>
  <HelpSection :title="t('helpCenter.quizTestCreate.sectionsTitle')">
    <p>{{ t('helpCenter.quizTestCreate.sectionsText') }}</p>

    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('quiz.sections.title') }}</SectionHeader>
        <SecondaryButton :icon="['fas', 'plus']" disabled>
          {{ t('quiz.sections.add') }}
        </SecondaryButton>
      </div>

      <NeutralContainer>
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionLabel>{{ t('helpCenter.quizTestCreate.sectionLabel', { n: 1 }) }}</SectionLabel>
            <div class="flex gap-1">
              <DeleteButton disabled />
            </div>
          </div>

          <TextInput :model-value="'Grundwissen Brandschutz'" disabled />
          <TextAreaInput :model-value="'Fragen zu den Grundlagen'" disabled />

          <div class="space-y-3">
            <label class="text-xs text-(--text-muted) font-medium">{{ t('quiz.sections.sources') }}</label>

            <div class="flex flex-col sm:flex-row gap-2 items-start sm:items-center p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
              <SelectInput :model-value="'1'" class="flex-1" disabled>
                <option value="1">Brandschutz-Katalog</option>
              </SelectInput>

              <SelectInput :model-value="''" class="flex-1" disabled>
                <option value="">{{ t('quiz.sections.allCategories') }}</option>
              </SelectInput>

              <div class="flex items-center gap-2">
                <label class="text-xs text-(--text-muted) whitespace-nowrap">{{ t('quiz.sections.questionCount') }}</label>
                <NumberInput :model-value="10" class="w-20" disabled />
              </div>

              <DeleteButton disabled />
            </div>

            <SecondaryButton :icon="['fas', 'plus']" disabled>
              {{ t('quiz.sections.addSource') }}
            </SecondaryButton>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </HelpSection>
</template>
