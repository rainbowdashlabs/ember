/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubmissionCard from './SubmissionCard.vue'
import type {FormAnswer, FormResponseEntry} from '@/api/generated/schema'

/**
 * Vertically stacked list of contact-form submission cards.
 */
defineProps<{
    submissions: FormResponseEntry[]
    answersByResponse: Map<number, FormAnswer[]>
    ackInFlight: Set<number>
    questionTitle: (answer: FormAnswer) => string
    formatAnswer: (answer: FormAnswer) => string
    formatTimestamp: (iso: string) => string
}>()

const emit = defineEmits<{
    (e: 'acknowledge', submission: FormResponseEntry): void
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
            @acknowledge="(s: FormResponseEntry) => emit('acknowledge', s)"
        />
    </div>
</template>
