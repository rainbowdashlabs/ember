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
import SigningStepFrame from './SigningStepFrame.vue'
import SigningProof from './SigningProof.vue'
import SigningBindingDetails from './SigningBindingDetails.vue'
import type {BatchStartResponse} from '@/api/generated/schema'
import type {TypedProof} from './useBatchSigning'
import type {Failure} from '@/util/failure'

/**
 * The confirmation: the signature is prepared as the screen appears, then confirmed with the one action the
 * account allows. Details of what the confirmation binds to stay behind "Mehr erfahren".
 */
defineProps<{
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
  <SigningStepFrame :title="t('signing.flow.confirm.heading')" :position="position" :total="total">
    <p class="text-sm">{{ t('signing.flow.confirm.hint') }}</p>
    <Spinner v-if="preparing" size="md"/>
    <SigningProof
        v-else-if="offer"
        :offer="offer"
        :busy="busy"
        @authenticator="$emit('authenticator')"
        @secret="(proof, secret) => $emit('secret', proof, secret)"
    />
    <FailureAlert :failure="failure"/>
    <SigningBindingDetails v-if="offer" :offer="offer"/>
    <template #actions>
      <SecondaryButton type="button" :disabled="busy" data-testid="signing-back" @click="$emit('back')">
        {{ t('signing.flow.back') }}
      </SecondaryButton>
    </template>
  </SigningStepFrame>
</template>
