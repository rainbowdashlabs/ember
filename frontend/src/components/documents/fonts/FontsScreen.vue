/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {DocumentFontView, DocumentFontsResponse, FontOrigin} from '@/api/generated/schema'
import type {FontSource, FontUpload} from '@/api/documentFonts'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {showToast} from '@/util/toast'
import FontUploadForm from './FontUploadForm.vue'
import OwnFontList from './OwnFontList.vue'
import ReachedFamilyList from './ReachedFamilyList.vue'

/**
 * The fonts of one owner, the same screen for a station, an association and the instance: the upload,
 * the owner's own families with their styles, and the families its templates reach from further up.
 * What differs per owner is the source the screen reads and writes through.
 */
const props = defineProps<{
  source: FontSource
  origin: FontOrigin
}>()

const {t} = useI18n()

const fonts = ref<DocumentFontsResponse>({own: [], reachable: []})
const pending = ref<DocumentFontView | null>(null)

const {loading, failure} = useAsyncLoader(async () => {
  fonts.value = await props.source.list()
})

const uploading = useAsyncAction(async (upload: FontUpload) => {
  fonts.value = await props.source.upload(upload)
  return fonts.value
})

const removing = useAsyncAction(async (font: DocumentFontView) => {
  fonts.value = await props.source.remove(font.id)
  return fonts.value
})

const reached = computed(() => fonts.value.reachable.filter(family => family.origin !== props.origin))
const shownFailure = computed(() => failure.value ?? uploading.failure.value ?? removing.failure.value)

async function upload(upload: FontUpload) {
  if (await uploading.run(upload)) showToast(t('documentFonts.uploaded'), 'success')
}

async function remove() {
  const font = pending.value
  if (!font) return
  pending.value = null
  if (await removing.run(font)) showToast(t('documentFonts.deleted'), 'success')
}
</script>

<template>
  <div class="space-y-6">
    <FailureAlert :failure="shownFailure"/>
    <Spinner v-if="loading" size="lg"/>
    <template v-else>
      <FontUploadForm :busy="uploading.running.value" @upload="upload"/>
      <OwnFontList :fonts="fonts.own" @remove="font => pending = font"/>
      <ReachedFamilyList v-if="reached.length > 0" :families="reached"/>
    </template>
    <ConfirmDeleteModal
        :model-value="pending !== null"
        :message="t('documentFonts.deleteConfirm', {family: pending?.family ?? '', style: pending ? t(`documentFonts.style.${pending.style}`) : ''})"
        :busy="removing.running.value"
        confirm-test-id="font-delete-confirm"
        @update:model-value="open => { if (!open) pending = null }"
        @confirm="remove"
    />
  </div>
</template>
