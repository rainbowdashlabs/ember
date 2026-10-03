/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TextInput from '@/components/input/text/TextInput.vue'
import type {Placeholder} from '@/api/generated/schema'
import {withPlaceholder} from '../placeholderText'
import PlaceholderPopover from './PlaceholderPopover.vue'

/**
 * A one line text that takes placeholders, such as the title of a template, with the picker beside it
 * putting a placeholder at the end.
 */
const text = defineModel<string>({required: true})

defineProps<{
  placeholders: Placeholder[]
  legal: boolean
  /** What the empty field shows. */
  prompt?: string
}>()
</script>

<template>
  <div class="flex items-center gap-2">
    <TextInput v-model="text" class="flex-1" :placeholder="prompt"/>
    <PlaceholderPopover :placeholders="placeholders" :legal="legal"
                        @pick="placeholder => text = withPlaceholder(text, placeholder.key)"/>
  </div>
</template>
