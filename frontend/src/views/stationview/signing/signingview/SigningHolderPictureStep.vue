/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import SignaturePad from '@/components/input/SignaturePad.vue'
import SignerCaption from '@/components/input/signaturepad/SignerCaption.vue'
import SigningStepFrame from './SigningStepFrame.vue'
import SigningStepNav from './SigningStepNav.vue'
import type {SigningMarkChoice} from './useBatchSigning'

/**
 * The reader's own signature picture, shown once for every field they sign themselves or as a guardian:
 * the one they saved, or one drawn or typed here, which they may keep for next time. "Weiter" waits for a
 * picture.
 */
const props = defineProps<{
  savedUrl: string | null
  /** The reader's name, printed under the signature as the documents print it under their fields. */
  signer: string
  position: number
  total: number
}>()

const choice = defineModel<SigningMarkChoice>({required: true})

defineEmits<{next: []; back: []}>()

const {t} = useI18n()
const keepId = useId()

const showsSaved = computed(() => props.savedUrl !== null && choice.value.useSaved && choice.value.draft === null)
const ready = computed(() => choice.value.draft !== null || showsSaved.value)

const draft = computed({
  get: () => choice.value.draft,
  set: (value) => { choice.value = {...choice.value, draft: value} },
})

const keep = computed({
  get: () => choice.value.keep,
  set: (value: boolean) => { choice.value = {...choice.value, keep: value} },
})

function useSaved(saved: boolean) {
  choice.value = {draft: null, useSaved: saved, keep: false}
}
</script>

<template>
  <SigningStepFrame :title="t('signing.flow.picture.heading')" :position="position" :total="total">
    <div v-if="showsSaved && savedUrl" class="space-y-3" data-testid="signing-picture-saved">
      <img
          :src="savedUrl"
          :alt="t('signing.mark.savedAlt')"
          class="block max-w-sm max-h-28 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-white p-2"
      />
      <SignerCaption :name="signer" class="max-w-sm"/>
      <MutedText tag="p" size="sm">{{ t('signing.flow.picture.savedHint') }}</MutedText>
      <SecondaryButton type="button" :icon="['fas', 'pen']" data-testid="signing-mark-new" @click="useSaved(false)">
        {{ t('signing.mark.drawNew') }}
      </SecondaryButton>
    </div>
    <div v-else class="space-y-3">
      <MutedText tag="p" size="sm">{{ t('signing.mark.drawHint') }}</MutedText>
      <SignaturePad v-model="draft" :signer="signer"/>
      <div class="flex items-center gap-2">
        <CheckboxInput :id="keepId" v-model="keep"/>
        <FormLabel :for="keepId" class="mb-0">{{ t('signing.mark.keep') }}</FormLabel>
      </div>
      <SecondaryButton v-if="savedUrl" type="button" :icon="['fas', 'rotate-left']" data-testid="signing-mark-saved"
                       @click="useSaved(true)">
        {{ t('signing.mark.useSaved') }}
      </SecondaryButton>
    </div>
    <template #actions>
      <SigningStepNav :blocked="!ready" @next="$emit('next')" @back="$emit('back')"/>
    </template>
  </SigningStepFrame>
</template>
