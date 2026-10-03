/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import AuthImage from '@/components/display/AuthImage.vue'
import {FontStyle} from '@/api/generated/schema'
import {useFontSamples} from '@/composables/useFontSamples'

/**
 * A line of sample text in a family, as a picture the server draws, so a list of families needs none of
 * their files; only the template editor loads those. The glyphs are black on a transparent ground and
 * turned light in the dark theme.
 * Nothing shows where no screen above says where samples are drawn, or where the picture cannot be had.
 */
const props = withDefaults(defineProps<{
  /** The family, or null for the default font. */
  family: string | null
  /** The version of the sample the list of families named. */
  version: string
  /** What the picture shows, for a screen reader. */
  label: string
  fontStyle?: FontStyle
}>(), {
  fontStyle: FontStyle.REGULAR,
})

const address = useFontSamples()

const src = computed(() => address ? address(props.family, props.fontStyle, props.version) : null)
</script>

<template>
  <AuthImage v-if="src" :src="src" :alt="label" data-testid="font-sample"
             class="block h-5 w-auto max-w-full object-contain object-left dark:invert"/>
</template>
