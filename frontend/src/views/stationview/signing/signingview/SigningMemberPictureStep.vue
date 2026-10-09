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
import SigningStepFrame from './SigningStepFrame.vue'
import SigningStepNav from './SigningStepNav.vue'
import type {SignatureDraft} from '@/util/signatureDraft'
import type {ThroughAccountSigner} from './batchFlow'

/**
 * A member in the reader's care who signs their own field through the reader's account draws their own
 * signature on a screen of its own, since the reader's saved one is not theirs. It is not kept.
 */
const props = defineProps<{
  signer: ThroughAccountSigner
  position: number
  total: number
}>()

const draft = defineModel<SignatureDraft | null>({required: true})

defineEmits<{next: []; back: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.flow.memberPicture.heading', {name: props.signer.name}))
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <MutedText tag="p" size="sm">{{ t('signing.flow.memberPicture.hint', {name: signer.name}) }}</MutedText>
    <SignaturePad v-model="draft"/>
    <template #actions>
      <SigningStepNav :blocked="draft === null" @next="$emit('next')" @back="$emit('back')"/>
    </template>
  </SigningStepFrame>
</template>
