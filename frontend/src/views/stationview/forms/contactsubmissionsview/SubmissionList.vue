/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubmissionCard from './SubmissionCard.vue'
import type {FormAnswer, FormResponse} from '@/api/types'

/**
 * Vertically stacked list of contact-form submission cards.
 */
defineProps<{
    submissions: FormResponse[]
    answersByResponse: Map<number, FormAnswer[]>
    ackInFlight: Set<number>
    questionTitle: (answer: FormAnswer) => string
    formatAnswer: (answer: FormAnswer) => string
    formatTimestamp: (iso: string) => string
}>()

const emit = defineEmits<{
    (e: 'acknowledge', submission: FormResponse): void
}>()
</script>

<template>
    <div class="space-y-3">
        <SubmissionCard
            v-for="sub in submissions"
            :key="sub.id"
            :submission="sub"
            :answers="answersByResponse.get(sub.id) ?? []"
            :ack-pending="ackInFlight.has(sub.id)"
            :question-title="questionTitle"
            :format-answer="formatAnswer"
            :format-timestamp="formatTimestamp"
            @acknowledge="(s: FormResponse) => emit('acknowledge', s)"
        />
    </div>
</template>
