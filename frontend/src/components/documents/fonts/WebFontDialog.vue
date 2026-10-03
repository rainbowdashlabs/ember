/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import FileInput from '@/components/input/FileInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {DocumentFontView} from '@/api/generated/schema'
import type {WebFontUpload} from '@/api/documentFonts'

/**
 * Uploads the web version of one style, which the template editor shows the style in instead of the
 * file documents print with: the file, as WOFF2, WOFF or a TrueType or OpenType font, and the uploader's
 * word that the owner may use the font. A web version the style already has is replaced.
 */
const props = defineProps<{
  /** The style the web version is for, or null while the dialog is closed. */
  font: DocumentFontView | null
  busy: boolean
}>()

const emit = defineEmits<{
  upload: [upload: WebFontUpload]
  close: []
}>()

const {t} = useI18n()

const file = ref<File | null>(null)
const confirmed = ref(false)

const ready = computed(() => file.value !== null && confirmed.value)
const title = computed(() => props.font
    ? t('documentFonts.webTitle', {family: props.font.family, style: t(`documentFonts.style.${props.font.style}`)})
    : '')

watch(() => props.font, () => {
  file.value = null
  confirmed.value = false
})

function submit() {
  if (file.value && ready.value) emit('upload', {file: file.value, confirmed: confirmed.value})
}
</script>

<template>
  <Modal :model-value="font !== null" @update:model-value="open => { if (!open) emit('close') }">
    <div class="space-y-4" data-testid="web-font-dialog">
      <SubHeader>{{ title }}</SubHeader>
      <MutedText size="sm" tag="p">{{ t('documentFonts.webHint') }}</MutedText>
      <FileInput accept=".woff2,.woff,.ttf,.otf,font/woff2,font/woff,font/ttf,font/otf" :label="t('documentFonts.webChooseFile')"
                 @select="chosen => file = chosen"/>
      <ToggleSetting v-model="confirmed" :label="t('documentFonts.licenceLabel')" :hint="t('documentFonts.licenceHint')"/>
      <ButtonRow pair align="end">
        <SecondaryButton data-cancel :disabled="busy" @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton data-confirm :icon="['fas', 'upload']" :disabled="!ready || busy" data-testid="web-font-submit"
                       @click="submit">
          {{ t('documentFonts.upload') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
