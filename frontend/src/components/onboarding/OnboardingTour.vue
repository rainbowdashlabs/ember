/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import {useOnboardingTour} from '@/composables/useOnboardingTour'

const {t} = useI18n()
const {isActive, currentStep, currentStepData, totalSteps, nextStep, prevStep, skipTour} = useOnboardingTour()
</script>

<template>
  <Teleport to="body">
    <Transition name="tour">
      <div v-if="isActive && currentStepData"
           class="fixed bottom-0 left-0 right-0 z-50 border-t border-(--border) bg-(--bg) shadow-[0_-4px_20px_rgba(0,0,0,0.15)]">
        <div class="mx-auto max-w-3xl px-4 py-4">
          <!-- Progress bar -->
          <div class="mb-3 h-1 rounded-full bg-(--border) overflow-hidden">
            <div
                class="h-full bg-primary rounded-full transition-all duration-300"
                :style="{ width: `${((currentStep + 1) / totalSteps) * 100}%` }"
            />
          </div>

          <div class="flex items-start gap-4">
            <!-- Icon -->
            <div class="hidden sm:flex items-center justify-center h-10 w-10 shrink-0 rounded-full bg-primary/10">
              <font-awesome-icon :icon="currentStepData.icon" class="h-5 w-5 text-primary"/>
            </div>

            <!-- Content -->
            <div class="flex-1 min-w-0">
              <SubHeader class="text-sm font-bold">
                {{ t(`tour.steps.${currentStepData.id}.title`) }}
              </SubHeader>
              <p class="text-xs text-(--text-muted) mt-0.5 leading-relaxed">
                {{ t(`tour.steps.${currentStepData.id}.body`) }}
              </p>
            </div>

            <!-- Actions -->
            <div class="flex items-center gap-2 shrink-0">
              <SecondaryButton :icon="['fas', 'chevron-left']" v-if="currentStep > 0" class="text-xs" @click="prevStep">
                {{ t('tour.back') }}
              </SecondaryButton>
              <PrimaryButton @click="nextStep">
                {{ currentStep === totalSteps - 1 ? t('tour.finish') : t('tour.next') }}
                <font-awesome-icon v-if="currentStep < totalSteps - 1" :icon="['fas', 'chevron-right']" class="ml-1"/>
              </PrimaryButton>
              <MutedIconButton
                  :icon="['fas', 'xmark']"
                  :label="t('tour.skip')"
                  hover="text"
                  @click="skipTour"
              />
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.tour-enter-active,
.tour-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.tour-enter-from,
.tour-leave-to {
  transform: translateY(100%);
  opacity: 0;
}
</style>
