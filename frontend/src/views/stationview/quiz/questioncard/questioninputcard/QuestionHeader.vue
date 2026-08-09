/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import AuthImage from '@/components/display/AuthImage.vue'
import type { QuizQuestion } from '@/api/quiz'
import { quiz } from '@/api'

const props = defineProps<{
  question: QuizQuestion
  directImageSrc?: string | null
}>()

const questionImageSrc = computed(() =>
  props.question.imageUrl ? quiz.questionImageUrl(props.question.id, 300) : null
)
</script>

<template>
  <div class="space-y-1">
    <p class="font-semibold">{{ question.title }}</p>
    <p v-if="question.description" class="text-sm text-(--text-muted)">{{ question.description }}</p>
    <img v-if="directImageSrc" :src="directImageSrc" class="max-h-48 rounded-lg object-contain" alt=""/>
    <AuthImage v-else-if="questionImageSrc" :src="questionImageSrc" class="max-h-48 rounded-lg object-contain" alt="" />
  </div>
</template>
