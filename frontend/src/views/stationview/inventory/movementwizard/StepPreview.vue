/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FlowDiagram from '@/components/movement/FlowDiagram.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import type {FlowPreview} from '@/api/movements'

/**
 * What will happen, drawn before anything is written.
 *
 * <p>The step that earns the wizard: the whole path, who answers each step, and where the piece will be
 * in between. Where no chain serves the combination this says so and points at the page that writes one,
 * rather than letting a submit fail with a sentence about a flow with no steps.
 */
const props = defineProps<{
  preview: FlowPreview | null
  /** Whether the answer has come back, so a missing chain is told apart from one still being fetched. */
  resolved: boolean
}>()

const {t} = useI18n()
const router = useRouter()
const routes = useInventoryRoutes()

const steps = computed(() => (props.preview?.flow.steps ?? []).map(step => ({...step, current: false})))

function toFlows() {
  if (routes.flows) void router.push({name: routes.flows})
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('movements.wizard.preview.title') }}</SubHeader>

    <Spinner v-if="!props.resolved"/>

    <template v-else-if="props.preview">
      <MutedText tag="p" size="sm">{{ t('movements.wizard.preview.hint') }}</MutedText>
      <FlowDiagram :owner-kind="props.preview.ownerKind" :steps="steps"/>
      <ol class="space-y-1 text-sm" data-testid="wizard-preview-steps">
        <li v-for="step in steps" :key="step.id" class="flex items-center gap-2">
          <span class="text-(--text-muted)">{{ step.position + 1 }}.</span>
          <span>{{ step.label }}</span>
          <MutedText size="sm">{{ t(`movements.actor.${step.actor}`) }}</MutedText>
        </li>
      </ol>
    </template>

    <template v-else>
      <Alert variant="error" data-testid="wizard-no-flow">{{ t('movements.wizard.preview.noFlow') }}</Alert>
      <SecondaryButton v-if="routes.flows" :icon="['fas', 'diagram-project']" @click="toFlows">
        {{ t('movements.wizard.preview.toFlows') }}
      </SecondaryButton>
    </template>
  </div>
</template>
