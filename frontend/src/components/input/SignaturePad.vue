/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SignatureCanvas from './signaturepad/SignatureCanvas.vue'
import SignatureTyped from './signaturepad/SignatureTyped.vue'
import {SignatureImageSource} from '@/api/generated/schema'
import type {SignatureDraft} from '@/util/signatureDraft'

/**
 * Makes a signature picture on the screen: drawn with a finger, a pen or the mouse, or, for whoever
 * cannot draw, typed as the name and set in a handwriting style.
 *
 * <p>The two ways are tabs of one tab list, reachable and switchable with the keyboard. The model is the
 * picture made so far, or null while there is none; switching the way starts over, so the model is always
 * the picture of the tab that is open.
 */
const model = defineModel<SignatureDraft | null>({required: true})

const {t} = useI18n()

const mode = ref<string>(SignatureImageSource.DRAWN)
const canvas = ref<InstanceType<typeof SignatureCanvas> | null>(null)

const tabs = computed(() => [
  {key: SignatureImageSource.DRAWN, label: t('signaturePad.draw')},
  {key: SignatureImageSource.TYPED, label: t('signaturePad.type')},
])

function drawn(dataUrl: string | null) {
  model.value = dataUrl ? {dataUrl, source: SignatureImageSource.DRAWN} : null
}

function typed(dataUrl: string | null) {
  model.value = dataUrl ? {dataUrl, source: SignatureImageSource.TYPED} : null
}

watch(mode, () => {
  model.value = null
})
</script>

<template>
  <div class="space-y-3" data-testid="signature-pad">
    <TabBar v-model="mode" :tabs="tabs"/>
    <div v-if="mode === SignatureImageSource.DRAWN" class="space-y-2">
      <MutedText tag="p" size="sm">{{ t('signaturePad.drawHint') }}</MutedText>
      <SignatureCanvas ref="canvas" :label="t('signaturePad.drawLabel')" @change="drawn"/>
      <SecondaryButton type="button" :icon="['fas', 'rotate-left']" data-testid="signature-clear" @click="canvas?.clear()">
        {{ t('signaturePad.clear') }}
      </SecondaryButton>
    </div>
    <SignatureTyped v-else @change="typed"/>
  </div>
</template>
