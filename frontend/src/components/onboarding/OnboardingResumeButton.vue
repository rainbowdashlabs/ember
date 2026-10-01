/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import BareButton from '@/components/button/BareButton.vue'
import {emberGuide} from '@/composables/useEmberLogo'
import {useOnboardingTasks} from '@/composables/useOnboardingTasks'
import type {OnboardingLevelName} from '@/api/onboarding'

/**
 * Ember waiting in the corner while somebody wanders off mid-task. Tapping it picks the guide back
 * up, and it is gone the moment nothing is open.
 */
const props = defineProps<{
  level: OnboardingLevelName
}>()

const {t} = useI18n()
const {openOf, startNext, activeTaskId, guideDismissed, reopenGuide} = useOnboardingTasks()
const logo = emberGuide('faq')

const visible = computed(() => openOf(props.level).length > 0 && (activeTaskId.value === null || guideDismissed.value))

function resume() {
  reopenGuide()
  if (activeTaskId.value === null) startNext(props.level)
}
</script>

<template>
  <BareButton
      v-if="visible"
      class="fixed bottom-4 right-16 z-40 flex h-10 w-10 items-center justify-center rounded-full
             border border-(--border) bg-(--bg) shadow-lg transition-colors hover:border-primary"
      :title="t('onboarding.resume')"
      @click="resume"
  >
    <LayeredEmberLogo
        :layers="logo.layers"
        :active-layers="logo.activeLayers"
        size="w-7 h-7"
        :pixel-size="128"
        :auto-blink="true"
    />
  </BareButton>
</template>
