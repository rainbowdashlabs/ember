/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberGuide, defaultGazePositions} from '@/composables/useEmberLogo'
import {useOnboardingTasks} from '@/composables/useOnboardingTasks'
import {flowFor} from '@/util/onboardingFlows'
import {canInstall, runInstall} from '@/util/installPrompt'
import {OnboardingTaskState, type OnboardingLevelName, type OnboardingTaskView} from '@/api/onboarding'

/**
 * What Ember still asks of the reader, on the level it asks it.
 *
 * On the station and the instance the list is shared, so it also says who settled a task: a
 * colleague arriving later can see whose decision they are looking at, and take a skipped one back
 * up.
 *
 * Who may see a level is not decided here. The card asks for it, and somebody who may not have it
 * is told so by the request rather than by a second permission check that could disagree with the
 * first: the card then simply has nothing to show and draws nothing.
 */
const props = defineProps<{
  level: OnboardingLevelName
}>()

const {t} = useI18n()
const {status, load, start, skip, discard, resume, confirm} = useOnboardingTasks()

const tasks = computed(() => status.value[props.level]?.tasks ?? [])
const open = computed(() => tasks.value.filter(task => task.state === OnboardingTaskState.OPEN))
const settled = computed(() => tasks.value.filter(task => task.state !== OnboardingTaskState.OPEN))
const skipped = computed(() => tasks.value.filter(task => task.state === OnboardingTaskState.SKIPPED))
const shared = computed(() => props.level !== 'MEMBER')

/**
 * Whether anything here still asks something of the reader.
 *
 * <p>An open task does, and so does a skipped one: it waits to be taken back up or thrown away. A
 * task that is done or thrown away asks nothing, and the card used to stay on the dashboard for
 * those too, so somebody who had settled every one of them kept a panel that only said so.
 */
const anythingLeft = computed(() => open.value.length > 0 || skipped.value.length > 0)
const logo = computed(() => emberGuide(open.value.length === 0 ? 'cheer' : 'plain'))

/** Whether Ember can walk somebody through this, or whether it happens outside the application. */
function walkable(task: OnboardingTaskView): boolean {
  return flowFor(task.key).length > 0
}

/**
 * Who a task is about, by name.
 *
 * <p>A guardian reads their tasks about a particular child, and "dein Kind" is the wrong words for
 * somebody looking after two. Where a task is about nobody in particular the general wording stands.
 */
function subjectName(task: OnboardingTaskView): string {
  return task.subject ?? t('onboarding.child')
}

/**
 * The heading of a task. Somebody it is about is named in the heading itself where the wording has
 * room for them, and appended in brackets where it has not.
 */
function title(task: OnboardingTaskView): string {
  const text = t(`onboarding.tasks.${task.key}.title`, {name: subjectName(task)})
  return task.subject && !text.includes(task.subject) ? `${text} (${task.subject})` : text
}

function body(task: OnboardingTaskView): string {
  return t(`onboarding.tasks.${task.key}.body`, {name: subjectName(task)})
}

/**
 * Whether the browser can be asked to install Ember for this task. Only the task about keeping
 * Ember to hand offers it, and only where the browser made the offer in the first place; everywhere
 * else the task keeps the written instructions it always had.
 */
function installable(task: OnboardingTaskView): boolean {
  return task.key === 'member.bookmark' && canInstall.value
}

/** Ticks the task off when the reader went through with the installation, and not before. */
async function install(task: OnboardingTaskView) {
  if (await runInstall()) confirm(props.level, task.id)
}

onMounted(() => load(props.level))
</script>

<template>
  <NeutralContainer v-if="anythingLeft" class="space-y-4">
    <div class="flex items-start gap-3">
      <LayeredEmberLogo
          :layers="logo.layers"
          :active-layers="logo.activeLayers"
          size="w-12 h-12 shrink-0"
          :pixel-size="128"
          :auto-blink="true"
          :gaze-positions="defaultGazePositions"
      />
      <div class="min-w-0">
        <SectionHeader>{{ t(`onboarding.card.${level}.title`) }}</SectionHeader>
        <MutedText tag="p" size="sm">
          {{ open.length > 0
              ? t(`onboarding.card.${level}.hint`)
              : t('onboarding.card.allDone') }}
        </MutedText>
      </div>
      <MutedText class="ml-auto shrink-0" size="sm">
        {{ settled.length }}/{{ tasks.length }}
      </MutedText>
    </div>

    <ul class="space-y-3">
      <li v-for="task in open" :key="task.id" :data-testid="`onboarding-task-${task.id}`"
          class="space-y-1 border-t border-(--border) pt-3 first:border-0 first:pt-0">
        <div class="font-medium">{{ title(task) }}</div>
        <MutedText tag="p" size="sm">{{ body(task) }}</MutedText>
        <div class="flex flex-wrap items-center gap-2 pt-1">
          <PrimaryButton v-if="walkable(task)" class="text-xs" @click="start(level, task)">
            {{ t('onboarding.card.begin') }}
          </PrimaryButton>
          <PrimaryButton v-if="installable(task)" class="text-xs" @click="install(task)">
            {{ t('onboarding.card.install') }}
          </PrimaryButton>
          <SecondaryButton v-if="task.confirmable" class="text-xs" @click="confirm(level, task.id)">
            {{ t('onboarding.card.confirm') }}
          </SecondaryButton>
          <SecondaryButton class="text-xs" @click="skip(level, task.id)">
            {{ t('onboarding.card.skip') }}
          </SecondaryButton>
        </div>
      </li>
    </ul>

    <div v-if="skipped.length > 0" class="space-y-2 border-t border-(--border) pt-3">
      <MutedText tag="p" size="sm">{{ t('onboarding.card.skippedTitle') }}</MutedText>
      <MutedText tag="p" size="sm">{{ t('onboarding.card.skippedHint') }}</MutedText>
      <div v-for="task in skipped" :key="task.id" class="flex flex-wrap items-center gap-2 text-sm">
        <span class="text-(--text-muted)">{{ title(task) }}</span>
        <MutedText v-if="shared && task.actorName" size="sm">
          {{ t('onboarding.card.settledBy', {name: task.actorName}) }}
        </MutedText>
        <SecondaryButton class="ml-auto text-xs" @click="resume(level, task.id)">
          {{ t('onboarding.card.resume') }}
        </SecondaryButton>
        <SecondaryButton class="text-xs" @click="discard(level, task.id)">
          {{ t('onboarding.card.discard') }}
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
