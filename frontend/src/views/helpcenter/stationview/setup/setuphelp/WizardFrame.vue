/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {STEP_ORDER, type WizardStepId} from '@/views/stationview/setup/steps'

/**
 * Static snapshot of the wizard shell every setup step is wrapped in — heading, step counter,
 * progress bar, hint line and the footer buttons. Each step help article fills the slot with
 * its own body so the frame itself is written once.
 */
const props = defineProps<{
  stepId: WizardStepId
  skippable?: boolean
  saveLabel?: string
  hideActions?: boolean
}>()

const {t} = useI18n()

const position = computed(() => STEP_ORDER.indexOf(props.stepId) + 1)
const progressPct = computed(() => Math.round((position.value / STEP_ORDER.length) * 100))
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center gap-3 flex-wrap">
      <SectionHeader class="flex-1">{{ t(`setup.steps.${stepId}.title`) }}</SectionHeader>
      <span class="text-xs text-(--text-muted) whitespace-nowrap">
        {{ t('setup.stepCounter', {current: position, total: STEP_ORDER.length}) }}
      </span>
    </div>
    <div class="h-1 bg-(--bg-accent) rounded-full overflow-hidden">
      <div class="h-full bg-(--accent)" :style="{width: progressPct + '%'}"/>
    </div>
    <p class="text-sm text-(--text-muted)">{{ t(`setup.steps.${stepId}.hint`) }}</p>

    <div class="space-y-4">
      <slot/>
    </div>

    <div v-if="!hideActions"
         class="flex flex-wrap items-center gap-3 justify-between pt-4 border-t border-(--border)">
      <SecondaryButton v-if="position > 1">{{ t('setup.actions.back') }}</SecondaryButton>
      <span v-else/>
      <div class="flex items-center gap-3">
        <SecondaryButton v-if="skippable">{{ t('setup.actions.skip') }}</SecondaryButton>
        <PrimaryButton>{{ saveLabel ?? t('setup.actions.saveAndContinue') }}</PrimaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
