/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import SigningDocument from '../signingview/SigningDocument.vue'
import WitnessAgreement from './WitnessAgreement.vue'
import {getWitnessDocument} from '@/api/witnessSigning'
import type {FillInResponse, WitnessFieldResponse} from '@/api/generated/schema'
import type {FillInValues} from '../signingview/fillIns'

/**
 * The person reads the document as its request froze it, ticks the statement in their own name and fills
 * in what the document asks of them. They may also give the device back without signing.
 */
const props = defineProps<{
  field: WitnessFieldResponse
  name: string
  fillIns: FillInResponse[]
  ready: boolean
  position: number
  total: number
}>()

const agreed = defineModel<boolean>('agreed', {required: true})
const values = defineModel<FillInValues>('values', {required: true})

defineEmits<{next: []; giveBack: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.witness.read.heading', {name: props.name}))
const documentTitle = computed(() => props.field.documentTitle ?? t('signing.flow.untitled'))
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <SigningDocument :field-id="field.fieldId" :title="documentTitle" :file-name="`${documentTitle}.pdf`"
                     :load="getWitnessDocument"/>
    <WitnessAgreement v-model:agreed="agreed" v-model:values="values" :field="field" :name="name" :fill-ins="fillIns"/>
    <template #actions>
      <ButtonRow pair>
        <SecondaryButton type="button" data-testid="witness-give-back" @click="$emit('giveBack')">
          {{ t('signing.witness.read.giveBack') }}
        </SecondaryButton>
        <PrimaryButton type="button" :disabled="!ready" data-testid="signing-next" @click="$emit('next')">
          {{ t('signing.flow.next') }}
        </PrimaryButton>
      </ButtonRow>
    </template>
  </SigningStepFrame>
</template>
