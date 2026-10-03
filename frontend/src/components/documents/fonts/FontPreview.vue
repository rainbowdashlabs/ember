/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import {FontStyle} from '@/api/generated/schema'
import {DEFAULT_FONT, previewStyle} from './fontOptions'

/**
 * A line of sample text for a family. Uploaded fonts never reach the browser, so the line is set in
 * the browser's own sans serif font with the style's weight and slant, and says which font the document
 * prints instead.
 */
const props = withDefaults(defineProps<{
  family: string | null
  fontStyle?: FontStyle
}>(), {
  fontStyle: FontStyle.REGULAR,
})

const {t} = useI18n()

const drawn = computed(() => previewStyle(props.fontStyle))
</script>

<template>
  <div class="space-y-1 rounded-md border border-dashed border-(--border) px-3 py-2" data-testid="font-preview">
    <p class="font-sans text-base" :style="drawn">{{ t('documentFonts.sample') }}</p>
    <MutedText size="xs" tag="p">{{ t('documentFonts.previewNote', {family: family ?? DEFAULT_FONT}) }}</MutedText>
  </div>
</template>
