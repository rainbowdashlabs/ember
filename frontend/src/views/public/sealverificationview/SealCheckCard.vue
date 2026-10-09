/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, type Component} from 'vue'
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {SealCheck} from '@/api/generated/schema'
import SealCheckDetails from './SealCheckDetails.vue'
import SealCheckFacts from './SealCheckFacts.vue'
import {partnerName, verdictOf, type SealVerdict} from './sealVerdict'

/**
 * One seal found in the checked file: its verdict first, in one line a reader without any knowledge
 * of signatures understands, then the plain facts, then the technical details folded away.
 */
const props = defineProps<{
  check: SealCheck
}>()

const {t} = useI18n()

interface VerdictLook {
  container: Component
  icon: [string, string]
  tone: string
}

const LOOKS: Record<SealVerdict, VerdictLook> = {
  sealedHere: {container: SuccessContainer, icon: ['fas', 'circle-check'], tone: 'text-success-badge'},
  sealedByPartner: {container: SuccessContainer, icon: ['fas', 'handshake'], tone: 'text-success-badge'},
  altered: {container: ErrorContainer, icon: ['fas', 'circle-xmark'], tone: 'text-error-badge'},
  modifiedAfterSealing: {container: ErrorContainer, icon: ['fas', 'triangle-exclamation'], tone: 'text-warning-badge'},
  notIssuedHere: {container: InfoContainer, icon: ['fas', 'ban'], tone: 'text-info-badge'},
  invalid: {container: ErrorContainer, icon: ['fas', 'ban'], tone: 'text-error-badge'},
  unclear: {container: NeutralContainer, icon: ['fas', 'circle-question'], tone: 'text-(--text-muted)'},
}

const verdict = computed(() => verdictOf(props.check))
const look = computed(() => LOOKS[verdict.value])
const named = computed(() => ({partner: partnerName(props.check) ?? t('common.unknown')}))
</script>

<template>
  <component :is="look.container" class="space-y-3" :data-verdict="verdict" data-testid="seal-check">
    <div class="flex items-start gap-3">
      <AppIcon :icon="look.icon" :class="look.tone" class="mt-1 text-xl shrink-0"/>
      <div class="space-y-1">
        <SubHeader>{{ t(`sealVerification.verdict.${verdict}.title`, named) }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t(`sealVerification.verdict.${verdict}.text`, named) }}</MutedText>
      </div>
    </div>
    <SealCheckFacts :check="check"/>
    <SealCheckDetails :check="check"/>
  </component>
</template>
