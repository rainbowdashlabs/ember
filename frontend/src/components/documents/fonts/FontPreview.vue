/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import {FontStyle, type FontFamilyOption} from '@/api/generated/schema'
import FontSample from './FontSample.vue'

/**
 * A line of sample text in the font a text prints in and the style it asks for, drawn by the server
 * with the real file, and the name of that font.
 */
const props = withDefaults(defineProps<{
  /** The family the text prints in, or null for the default font, as for a family no longer reached. */
  family: FontFamilyOption | null
  /** The family a text naming none prints in, as the owner's font list names it. */
  defaultFamily: string
  fontStyle?: FontStyle
}>(), {
  fontStyle: FontStyle.REGULAR,
})

const {t} = useI18n()

const printed = computed(() => props.family?.family ?? props.defaultFamily)
const version = computed(() => props.family?.sample ?? props.defaultFamily)
</script>

<template>
  <div class="space-y-1 rounded-md border border-dashed border-(--border) px-3 py-2" data-testid="font-preview">
    <FontSample :family="family?.family ?? null" :version="version" :font-style="fontStyle"
                :label="t('documentFonts.sampleOf', {family: printed})"/>
    <MutedText size="xs" tag="p">{{ t('documentFonts.previewNote', {family: printed}) }}</MutedText>
  </div>
</template>
