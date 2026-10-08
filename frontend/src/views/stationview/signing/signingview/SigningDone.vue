/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import {RequestState, StepUpProof} from '@/api/generated/schema'
import type {SigningCompleteResponse} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * The signature as it now stands: when it was given, with which proof, whether that proof is bound to
 * the document, and whether the document still waits for somebody else.
 *
 * <p>The heading takes the focus as the result replaces the form, so the reader hears that it worked
 * instead of finding the focus on a button that no longer exists.
 */
const props = defineProps<{outcome: SigningCompleteResponse}>()

const {t} = useI18n()
const headingId = useId()

/** The proofs a signature is confirmed with; signing never takes a backup code or another device. */
const PROOF_KEYS: Readonly<Partial<Record<StepUpProof, string>>> = {
  [StepUpProof.PASSKEY]: 'signing.done.proof.passkey',
  [StepUpProof.SECURITY_KEY]: 'signing.done.proof.securityKey',
  [StepUpProof.TOTP]: 'signing.done.proof.code',
  [StepUpProof.PASSWORD]: 'signing.done.proof.password',
}

const proofText = computed(() => t(props.outcome.bound ? 'signing.done.bound' : 'signing.done.unbound', {
  proof: t(PROOF_KEYS[props.outcome.proof] ?? 'signing.done.proof.code'),
}))

const complete = computed(() => props.outcome.requestState === RequestState.COMPLETE)

onMounted(() => document.getElementById(headingId)?.focus())
</script>

<template>
  <SuccessContainer class="space-y-3 max-w-3xl" data-testid="signing-done">
    <SectionHeader :id="headingId" tabindex="-1" class="focus:outline-none">{{ t('signing.done.heading') }}</SectionHeader>
    <p>{{ t('signing.done.signedAt', {time: formatDateTime(outcome.settledAt)}) }}</p>
    <p>{{ proofText }}</p>
    <p>{{ complete ? t('signing.done.complete') : t('signing.done.waiting') }}</p>
    <div class="flex flex-wrap gap-x-6 gap-y-2">
      <AppLink href="/station/requirements" :icon="['fas', 'clipboard-check']">{{ t('signing.done.toRequirements') }}</AppLink>
      <AppLink href="/station/documents" :icon="['fas', 'file-lines']">{{ t('signing.done.toDocuments') }}</AppLink>
    </div>
  </SuccessContainer>
</template>
