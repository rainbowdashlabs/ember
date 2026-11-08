/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import type {WitnessFieldResponse, WitnessPersonResponse} from '@/api/generated/schema'

/**
 * Who stands in front of the witness, for a field any of the member's guardians may sign: the witness
 * picks the guardian who is there.
 */
defineProps<{
  field: WitnessFieldResponse
  position: number
  total: number
}>()

defineEmits<{pick: [person: WitnessPersonResponse]; back: []}>()

const {t} = useI18n()
</script>

<template>
  <SigningStepFrame :title="t('signing.witness.signer.heading')" :position="position" :total="total">
    <MutedText tag="p" size="sm">{{ t('signing.witness.signer.hint', {name: field.memberName}) }}</MutedText>
    <ul class="space-y-2">
      <li v-for="person in field.signers" :key="person.memberId">
        <SecondaryButton type="button" class="w-full justify-start" :data-testid="`witness-signer-${person.memberId}`"
                         @click="$emit('pick', person)">
          {{ person.name }}
        </SecondaryButton>
      </li>
    </ul>
    <template #actions>
      <SecondaryButton type="button" data-testid="signing-back" @click="$emit('back')">{{ t('signing.flow.back') }}</SecondaryButton>
    </template>
  </SigningStepFrame>
</template>
