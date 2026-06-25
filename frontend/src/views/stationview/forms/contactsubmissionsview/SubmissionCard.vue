/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {FormAnswer, FormResponse} from '@/api/types'

/**
 * Single contact-form submission card showing the answers and the acknowledge action.
 */
const props = defineProps<{
    submission: FormResponse
    answers: FormAnswer[]
    ackPending: boolean
    questionTitle: (answer: FormAnswer) => string
    formatAnswer: (answer: FormAnswer) => string
    formatTimestamp: (iso: string) => string
}>()

const emit = defineEmits<{
    (e: 'acknowledge', submission: FormResponse): void
}>()

const {t} = useI18n()

function onAcknowledge() {
    emit('acknowledge', props.submission)
}
</script>

<template>
    <NeutralContainer class="space-y-3">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <SubHeader class="mb-0">{{ formatTimestamp(submission.submittedAt) }}</SubHeader>
            <SuccessBadge v-if="submission.acknowledgedAt">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                {{ t('forms.contactSubmissions.acknowledged') }}
            </SuccessBadge>
        </div>

        <div v-for="answer in answers" :key="answer.id" class="space-y-0.5">
            <p class="text-xs text-(--text-muted)">{{ questionTitle(answer) }}</p>
            <p class="text-sm whitespace-pre-line">{{ formatAnswer(answer) }}</p>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-(--border)">
            <p v-if="submission.acknowledgedAt && submission.acknowledgedByIdentity"
               class="text-xs text-(--text-muted) flex items-center gap-1.5">
                {{ t('forms.contactSubmissions.acknowledgedBy') }}
                <MemberName :identity="submission.acknowledgedByIdentity" size="sm"/>
                <span>·</span>
                <span>{{ formatTimestamp(submission.acknowledgedAt) }}</span>
            </p>
            <span v-else class="text-xs text-(--text-muted) italic">
                {{ t('forms.contactSubmissions.notAcknowledged') }}
            </span>
            <PrimaryButton
                v-if="!submission.acknowledgedAt"
                :disabled="ackPending"
                @click="onAcknowledge"
            >
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                {{ t('forms.contactSubmissions.acknowledge') }}
            </PrimaryButton>
        </div>
    </NeutralContainer>
</template>
