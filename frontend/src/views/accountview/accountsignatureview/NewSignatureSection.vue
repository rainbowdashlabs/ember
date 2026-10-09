/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SignaturePad from '@/components/input/SignaturePad.vue'
import FileUploadField from '@/components/input/FileUploadField.vue'
import {SignatureImageSource} from '@/api/generated/schema'
import {SIGNATURE_IMAGE_MAX_BYTES} from '@/api/signing'
import {draftBlob, type SignatureDraft} from '@/util/signatureDraft'

/**
 * Makes a new signature picture: drawn or typed on the screen, or a photo or scan of one on paper. Either
 * replaces the picture saved before once it is saved.
 */
defineProps<{busy: boolean}>()

const emit = defineEmits<{save: [image: Blob, source: SignatureImageSource]}>()

const {t} = useI18n()

const draft = ref<SignatureDraft | null>(null)

function saveDraft() {
  if (draft.value) emit('save', draftBlob(draft.value), draft.value.source)
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="signature-new">
    <SectionHeader>{{ t('accountSignature.new.heading') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('accountSignature.new.hint') }}</MutedText>
    <SignaturePad v-model="draft"/>
    <PrimaryButton
        type="button"
        :icon="['fas', 'floppy-disk']"
        :disabled="busy || !draft"
        data-testid="signature-save"
        @click="saveDraft"
    >
      {{ t('accountSignature.new.save') }}
    </PrimaryButton>
    <SubHeader>{{ t('accountSignature.new.uploadHeading') }}</SubHeader>
    <FileUploadField
        accept="image/png,image/jpeg,image/webp"
        :max-size="SIGNATURE_IMAGE_MAX_BYTES"
        :disabled="busy"
        :hint="t('accountSignature.new.uploadHint')"
        :label="t('accountSignature.new.upload')"
        @select="file => emit('save', file, SignatureImageSource.UPLOADED)"
    />
  </NeutralContainer>
</template>
