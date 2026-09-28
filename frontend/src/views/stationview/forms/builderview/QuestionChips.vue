/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ChipButton from '@/components/button/ChipButton.vue'
import { questionChips } from './settingChips'
import type { QuestionDraft } from './types'

/**
 * The settings of a question that differ from their default, under its title. Each opens the menu
 * they are changed in, and a question at its defaults shows none.
 */
const props = defineProps<{
  question: QuestionDraft
}>()

const emit = defineEmits<{
  open: [event: MouseEvent]
}>()

const { t } = useI18n()

const chips = computed(() => questionChips(props.question, t))
</script>

<template>
  <div v-if="chips.length > 0" class="flex flex-wrap gap-1.5" data-testid="question-chips">
    <ChipButton v-for="chip in chips" :key="chip" @click="emit('open', $event)">{{ chip }}</ChipButton>
  </div>
</template>
