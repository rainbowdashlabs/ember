/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import SigningProof from '../signingview/SigningProof.vue'
import type {BatchStartResponse} from '@/api/generated/schema'
import type {TypedProof} from '../signingview/useBatchSigning'
import type {Failure} from '@/util/failure'

/**
 * The witness has the device back and confirms with their own account that the person signed in front of
 * them, in the one way their account allows. Going back hands the device over again.
 */
defineProps<{
  name: string
  offer: BatchStartResponse | null
  preparing: boolean
  busy: boolean
  failure: Failure | null
  position: number
  total: number
}>()

defineEmits<{authenticator: []; secret: [proof: TypedProof, secret: string]; back: []}>()

const {t} = useI18n()
</script>

<template>
  <SigningStepFrame :title="t('signing.witness.confirm.heading')" :position="position" :total="total">
    <p class="font-medium" data-testid="witness-statement">{{ t('signing.witness.confirm.statement', {name}) }}</p>
    <p class="text-sm">{{ t('signing.witness.confirm.hint', {name}) }}</p>
    <Spinner v-if="preparing" size="md"/>
    <SigningProof
        v-else-if="offer"
        :offer="offer"
        :busy="busy"
        @authenticator="$emit('authenticator')"
        @secret="(proof, secret) => $emit('secret', proof, secret)"
    />
    <FailureAlert :failure="failure"/>
    <template #actions>
      <SecondaryButton type="button" :disabled="busy" data-testid="signing-back" @click="$emit('back')">
        {{ t('signing.flow.back') }}
      </SecondaryButton>
    </template>
  </SigningStepFrame>
</template>
