/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import {useOnboardingGuide} from '@/composables/useOnboardingGuide'
import {useOnboardingTasks} from '@/composables/useOnboardingTasks'
import {emberGuide} from '@/composables/useEmberLogo'
import {activeLevel, activeTaskId, activeTaskKey, activeStep, guideDismissed} from '@/util/onboardingState'

/**
 * Ember standing next to whatever the reader should do next.
 *
 * The layer over the page catches no clicks of its own: the ring and the bubble sit on top, and
 * everything underneath stays as usable as it was.
 *
 * The way there is offered only when there is nothing to point at. Offering it beside a ring that
 * already sits on the thing to click would invite the reader to skip the very step being taught.
 */
const {t} = useI18n()
const router = useRouter()
const {box, step, steps, pointing, revealing, gaze, finished, reducedMotion, onStepRoute, dismiss} =
    useOnboardingGuide()
const {stop, skip, load} = useOnboardingTasks()

const visible = computed(() => activeTaskId.value !== null && !guideDismissed.value && step.value !== null)

/**
 * The walk ends where the steps end. Ember steps aside and the list is read again, because whether
 * the task counts as done is the server's answer and not the last click's.
 */
watch(finished, async done => {
  if (!done) return
  const level = activeLevel.value
  stop()
  if (level) await load(level)
})
const logo = computed(() => emberGuide(pointing.value ? 'glow' : 'faq'))

const ring = computed(() => {
  if (!box.value) return undefined
  const padding = 6
  return {
    top: `${box.value.top - padding}px`,
    left: `${box.value.left - padding}px`,
    width: `${box.value.width + padding * 2}px`,
    height: `${box.value.height + padding * 2}px`,
  }
})

const bubbleText = computed(() => {
  if (!activeTaskKey.value || !step.value) return ''
  if (revealing.value) return t('onboarding.guide.reveal')
  if (!pointing.value && step.value.route) {
    return t('onboarding.guide.elsewhere', {page: t(`onboarding.routes.${step.value.route}`)})
  }
  return t(`onboarding.steps.${activeTaskKey.value}.${activeStep.value}`)
})

const progress = computed(() => `${Math.min(activeStep.value + 1, steps.value.length)}/${steps.value.length}`)

function goToStepRoute() {
  if (step.value?.route) router.push({name: step.value.route}).catch(() => {})
}

function skipTask() {
  if (activeLevel.value && activeTaskId.value) skip(activeLevel.value, activeTaskId.value)
  stop()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="pointer-events-none fixed inset-0 z-50">
      <div
          v-if="ring"
          aria-hidden="true"
          class="absolute rounded-theme border-2 border-primary"
          :class="reducedMotion ? '' : 'animate-pulse'"
          :style="ring"
      />

      <div role="status" aria-live="polite" tabindex="-1" @keydown.esc="dismiss"
           class="pointer-events-auto absolute bottom-4 left-1/2 w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2
                  rounded-theme border border-(--border) bg-(--bg) p-3 shadow-lg">
        <div class="flex items-start gap-3">
          <LayeredEmberLogo
              :layers="logo.layers"
              :active-layers="logo.activeLayers"
              size="w-10 h-10 shrink-0"
              :pixel-size="128"
              :auto-blink="!reducedMotion"
              :gaze-positions="[gaze]"
          />
          <div class="min-w-0 flex-1 space-y-2">
            <p class="text-sm text-(--text)">{{ bubbleText }}</p>
            <div class="flex flex-wrap items-center gap-2">
              <PrimaryButton v-if="!pointing && !onStepRoute && step?.route" class="text-xs" @click="goToStepRoute">
                {{ t('onboarding.guide.takeMeThere') }}
              </PrimaryButton>
              <SecondaryButton class="text-xs" @click="skipTask">{{ t('onboarding.guide.skip') }}</SecondaryButton>
              <span class="ml-auto text-xs text-(--text-muted)">{{ progress }}</span>
            </div>
          </div>
          <MutedIconButton
              :icon="['fas', 'xmark']"
              :label="t('onboarding.guide.dismiss')"
              hover="text"
              @click="dismiss"
          />
        </div>
      </div>
    </div>
  </Teleport>
</template>
