/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import QuestionCard from '@/views/stationview/forms/fillview/QuestionCard.vue'
import type {PitchForm} from './pitchTypes'

/** A form being filled in, drawn by the application's own question cards. */
const props = defineProps<{
  form: PitchForm
}>()

const answers = ref<Record<number, Record<string, unknown>>>({...props.form.answers})
</script>

<template>
  <div class="space-y-3">
    <QuestionCard v-for="question in form.questions" :key="question.id"
                  v-model="answers[question.id]" :question="question"/>
  </div>
</template>
