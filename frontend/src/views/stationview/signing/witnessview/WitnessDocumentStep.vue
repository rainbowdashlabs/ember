/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import WitnessOpenFields from './WitnessOpenFields.vue'
import WitnessTemplates from './WitnessTemplates.vue'
import type {WitnessFieldResponse, WitnessOffersResponse, WitnessPersonResponse} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'

/**
 * What the person may sign in front of the witness: the open fields of their documents, and the templates
 * a document can be made from on the spot. Making one leads straight on where it has one field to sign.
 */
const props = defineProps<{
  person: WitnessPersonResponse
  offers: WitnessOffersResponse
  busy: boolean
  failure: Failure | null
  position: number
  total: number
}>()

defineEmits<{choose: [field: WitnessFieldResponse]; generate: [templateId: number]; back: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.witness.document.heading', {name: props.person.name}))
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <WitnessOpenFields :person="person" :fields="offers.fields" :busy="busy" @choose="field => $emit('choose', field)"/>
    <WitnessTemplates :templates="offers.templates" :busy="busy" @generate="id => $emit('generate', id)"/>
    <FailureAlert :failure="failure"/>
    <template #actions>
      <SecondaryButton type="button" :disabled="busy" data-testid="witness-other-person" @click="$emit('back')">
        {{ t('signing.witness.document.otherPerson') }}
      </SecondaryButton>
    </template>
  </SigningStepFrame>
</template>
