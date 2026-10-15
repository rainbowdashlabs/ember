/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import WitnessHandedOver from './WitnessHandedOver.vue'
import WitnessHandOverStep from './WitnessHandOverStep.vue'
import WitnessReadStep from './WitnessReadStep.vue'
import WitnessDrawStep from './WitnessDrawStep.vue'
import WitnessTakeBackStep from './WitnessTakeBackStep.vue'
import type {FillInResponse, WitnessFieldResponse} from '@/api/generated/schema'
import type {FillInValues} from '../signingview/fillIns'
import type {SignatureDraft} from '@/util/signatureDraft'
import {positionOf, WITNESS_STEPS, type WitnessStep} from './witnessFlow'

/**
 * The part of signing in front of a witness the person holds the device for: taking it up, reading and
 * agreeing, drawing, and handing it back. The step is the flow's; each screen only says where to go.
 */
const props = defineProps<{
  step: WitnessStep
  field: WitnessFieldResponse
  name: string
  witness: string
  fillIns: FillInResponse[]
  ready: boolean
  abandoned: boolean
}>()

const agreed = defineModel<boolean>('agreed', {required: true})
const values = defineModel<FillInValues>('values', {required: true})
const draft = defineModel<SignatureDraft | null>('draft', {required: true})

defineEmits<{go: [step: WitnessStep]; giveBack: []; takenBack: []}>()

const {t} = useI18n()

const position = computed(() => positionOf(props.step))
const total = WITNESS_STEPS.length
</script>

<template>
  <WitnessHandedOver :label="t('signing.witness.handOver.heading', {name})">
    <WitnessHandOverStep v-if="step === 'handOver'" :name="name" :position="position" :total="total" @next="$emit('go', 'read')"/>
    <WitnessReadStep v-else-if="step === 'read'" v-model:agreed="agreed" v-model:values="values" :field="field" :name="name"
                     :fill-ins="fillIns" :ready="ready" :position="position" :total="total"
                     @next="$emit('go', 'draw')" @give-back="$emit('giveBack')"/>
    <WitnessDrawStep v-else-if="step === 'draw'" v-model="draft" :name="name" :position="position" :total="total"
                     @next="$emit('go', 'takeBack')" @back="$emit('go', 'read')"/>
    <WitnessTakeBackStep v-else :witness="witness" :abandoned="abandoned" :position="position" :total="total"
                         @next="$emit('takenBack')"/>
  </WitnessHandedOver>
</template>
