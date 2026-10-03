/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FontFamilyPicker from '@/components/documents/fonts/FontFamilyPicker.vue'
import FontPreview from '@/components/documents/fonts/FontPreview.vue'
import type {FontFamilyOption, LetterPage} from '@/api/generated/schema'

/**
 * The fonts of a letter: one family for the body, the header and the footer each, every one the
 * default font until another is picked. The fonts come from the station, its association and the
 * instance; the font pages are where they are uploaded.
 */
const page = defineModel<LetterPage>({required: true})

defineProps<{
  fonts: readonly FontFamilyOption[]
}>()

const {t} = useI18n()

type FontKey = 'bodyFont' | 'headerFont' | 'footerFont'

const parts: readonly {key: FontKey, label: string}[] = [
  {key: 'bodyFont', label: 'documentFonts.bodyFont'},
  {key: 'headerFont', label: 'documentFonts.headerFont'},
  {key: 'footerFont', label: 'documentFonts.footerFont'},
]

function set(key: FontKey, family: string | null) {
  page.value = {...page.value, [key]: family}
}
</script>

<template>
  <div class="space-y-3" data-testid="letter-fonts">
    <div class="grid gap-3 sm:grid-cols-3">
      <FontFamilyPicker v-for="part in parts" :key="part.key" :model-value="page[part.key]" :label="t(part.label)"
                        :fonts="fonts" :test-id="`letter-${part.key}`" @update:model-value="family => set(part.key, family)"/>
    </div>
    <FontPreview :family="page.bodyFont"/>
  </div>
</template>
