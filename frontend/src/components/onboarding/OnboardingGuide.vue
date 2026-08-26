/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import {useOnboardingGuide} from '@/composables/useOnboardingGuide'
import {useOnboardingTasks} from '@/composables/useOnboardingTasks'
import {emberGuide} from '@/composables/useEmberLogo'
import {OnboardingTaskState} from '@/api/onboarding'
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
const {box, step, steps, pointing, revealing, blocked, gaze, targetLow, finished, reducedMotion, onStepRoute, dismiss} =
    useOnboardingGuide()
const {stop, skip, load, status} = useOnboardingTasks()

/**
 * The task whose steps have just run out: what it was called, where its list is, and whether it
 * actually counts as done.
 *
 * Those last two are not the same question. Reaching the end of the steps means the reader was
 * walked through them; whether the task is settled is derived from the data on the server. A reader
 * who closed the questions of an event instead of sending them walked every step and answered
 * nothing, and Ember congratulating them on it would be a lie they find out about on the next page.
 */
const completed = ref<{title: string, route: string, done: boolean} | null>(null)

const walking = computed(() => activeTaskId.value !== null && !guideDismissed.value && step.value !== null)
const visible = computed(() => walking.value || completed.value !== null)

/** Where the tasks of a level are listed, which is where a reader goes on from a finished one. */
const LISTS: Record<string, string> = {
  MEMBER: 'dashboard-overview',
  STATION: 'dashboard-overview',
  INSTANCE: 'admin-overview',
}

/**
 * The walk ends where the steps end. Ember says so rather than vanishing mid-page, and the list is
 * read again, because whether the task counts as done is the server's answer and not the last
 * click's.
 */
watch(finished, async ended => {
  if (!ended) return
  const level = activeLevel.value
  const taskId = activeTaskId.value
  const title = activeTaskKey.value ? t(`onboarding.tasks.${activeTaskKey.value}.title`) : ''
  stop()
  if (level) await load(level)
  const task = level && taskId ? (status.value[level]?.tasks ?? []).find(entry => entry.id === taskId) : undefined
  completed.value = {
    title,
    route: (level && LISTS[level]) || 'dashboard-overview',
    // A task that is no longer listed cannot be asked about, and saying nothing is friendlier there.
    done: task === undefined || task.state === OnboardingTaskState.DONE,
  }
})

function goToList() {
  const route = completed.value?.route
  completed.value = null
  if (route) router.push({name: route}).catch(() => {})
}
const logo = computed(() => {
  if (completed.value) return emberGuide(completed.value.done ? 'cheer' : 'sober')
  return emberGuide(pointing.value ? 'glow' : 'faq')
})

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

/**
 * The ring's own geometry, plus the shade that falls over everything outside it. One spread shadow
 * large enough to cover any screen darkens the whole page and leaves the target lit, which needs no
 * second element and changes nothing about what can be clicked: the layer catches no pointer events.
 */
const spotlight = computed(() => (ring.value ? {...ring.value, boxShadow: '0 0 0 9999px rgb(0 0 0 / 0.55)'} : undefined))

/** The task being walked, named above the step so a reader who looked away can pick it up again. */
const taskTitle = computed(() => {
  if (completed.value) return completed.value.title
  return activeTaskKey.value ? t(`onboarding.tasks.${activeTaskKey.value}.title`) : ''
})

const bubbleText = computed(() => {
  if (completed.value) return t(completed.value.done ? 'onboarding.guide.finished' : 'onboarding.guide.notYet')
  if (!activeTaskKey.value || !step.value) return ''
  if (revealing.value) return t('onboarding.guide.reveal')
  if (!pointing.value && step.value.route) {
    return t('onboarding.guide.elsewhere', {page: t(`onboarding.routes.${step.value.route}`)})
  }
  if (blocked.value) return t('onboarding.guide.blocked')
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

/** Puts the bubble away, whether it is walking a task or standing on a finished one. */
function close() {
  completed.value = null
  dismiss()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="pointer-events-none fixed inset-0 z-50">
      <div
          v-if="spotlight"
          aria-hidden="true"
          class="absolute rounded-theme border-4 border-primary"
          :style="spotlight"
      />
      <div
          v-if="ring && !reducedMotion"
          aria-hidden="true"
          class="absolute rounded-theme border-2 border-primary animate-ping"
          :style="ring"
      />

      <div role="status" aria-live="polite" tabindex="-1" @keydown.esc="close"
           :class="targetLow ? 'top-4' : 'bottom-4'"
           class="pointer-events-auto absolute left-1/2 w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2
                  rounded-theme border border-(--border) bg-(--bg) p-4 shadow-xl">
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
            <p class="text-sm font-semibold text-(--text-muted)">{{ taskTitle }}</p>
            <p class="text-base text-(--text)">{{ bubbleText }}</p>
            <div v-if="completed" class="flex flex-wrap items-center gap-2">
              <PrimaryButton class="text-sm" @click="goToList">{{ t('onboarding.guide.toList') }}</PrimaryButton>
              <SecondaryButton class="text-sm" @click="completed = null">
                {{ t('onboarding.guide.stayHere') }}
              </SecondaryButton>
            </div>
            <div v-else class="flex flex-wrap items-center gap-2">
              <PrimaryButton v-if="!pointing && !onStepRoute && step?.route" class="text-sm" @click="goToStepRoute">
                {{ t('onboarding.guide.takeMeThere') }}
              </PrimaryButton>
              <SecondaryButton class="text-sm" @click="skipTask">{{ t('onboarding.guide.skip') }}</SecondaryButton>
              <span class="ml-auto text-xs text-(--text-muted)">{{ progress }}</span>
            </div>
          </div>
          <MutedIconButton
              :icon="['fas', 'xmark']"
              :label="t('onboarding.guide.dismiss')"
              hover="text"
              @click="close"
          />
        </div>
      </div>
    </div>
  </Teleport>
</template>
