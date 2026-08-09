/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TourProgressBar from './onboardingtour/TourProgressBar.vue'
import TourStepDetails from './onboardingtour/TourStepDetails.vue'
import TourActions from './onboardingtour/TourActions.vue'
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
          <TourProgressBar :step="currentStep" :total="totalSteps"/>

          <div class="flex items-start gap-4">
            <TourStepDetails
                :icon="currentStepData.icon"
                :title="t(`tour.steps.${currentStepData.id}.title`)"
                :body="t(`tour.steps.${currentStepData.id}.body`)"
            />
            <TourActions
                :step="currentStep"
                :total="totalSteps"
                @next="nextStep"
                @prev="prevStep"
                @skip="skipTour"
            />
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
