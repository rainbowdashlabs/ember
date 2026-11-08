/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DockButton from '@/components/button/DockButton.vue'
import {useOnboardingTasks} from '@/composables/useOnboardingTasks'
import type {OnboardingLevel} from '@/api/generated/schema'

/**
 * A round button in the corner while somebody wanders off mid-task. Tapping it picks the guide back
 * up, and it is gone the moment nothing is open.
 *
 * <p>It carries the rocket the setup has in the sidebar, not the Ember logo: the help center's button
 * beside it shows that logo, and two buttons that look alike were taken for one another.
 */
const props = defineProps<{
  level: OnboardingLevel
}>()

const {t} = useI18n()
const {openOf, startNext, activeTaskId, guideDismissed, reopenGuide} = useOnboardingTasks()

const visible = computed(() => openOf(props.level).length > 0 && (activeTaskId.value === null || guideDismissed.value))

function resume() {
  reopenGuide()
  if (activeTaskId.value === null) startNext(props.level)
}
</script>

<template>
  <DockButton v-if="visible" :label="t('onboarding.resume')" :icon="['fas', 'rocket']" data-testid="onboarding-resume"
              @click="resume"/>
</template>
