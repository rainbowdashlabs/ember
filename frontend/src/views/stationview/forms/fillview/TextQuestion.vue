/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'

type TextAnswer = { text: string }

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<TextAnswer>({ required: true })

const longAnswer = computed(() => !!props.config.longAnswer)
</script>

<template>
  <TextAreaInput v-if="longAnswer" v-model="answer.text" />
  <TextInput v-else v-model="answer.text" />
</template>
