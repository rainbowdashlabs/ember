/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SignaturePad from '@/components/input/SignaturePad.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'
import SigningStepNav from '../signingview/SigningStepNav.vue'
import type {SignatureDraft} from '@/util/signatureDraft'

/**
 * The person draws their signature, with their name under the pad as the document prints it under the
 * field. The picture is not kept beyond this signature.
 */
const props = defineProps<{
  name: string
  position: number
  total: number
}>()

const draft = defineModel<SignatureDraft | null>({required: true})

defineEmits<{next: []; back: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.witness.draw.heading', {name: props.name}))
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <MutedText tag="p" size="sm">{{ t('signing.witness.draw.hint') }}</MutedText>
    <SignaturePad v-model="draft" :signer="name"/>
    <template #actions>
      <SigningStepNav :blocked="draft === null" @next="$emit('next')" @back="$emit('back')"/>
    </template>
  </SigningStepFrame>
</template>
