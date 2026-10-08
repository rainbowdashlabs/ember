/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {DocumentTimestampCheck} from '@/api/generated/schema'
import TimestampDetails from './TimestampDetails.vue'

/**
 * Timestamps on the file as a whole rather than inside a seal, which a seal gets when it is brought up
 * to date later. They prove the file existed at their time, and say nothing about who made it.
 */
defineProps<{
  timestamps: DocumentTimestampCheck[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="seal-document-timestamps">
    <SubHeader>{{ t('sealVerification.documentTimestamps.title') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('sealVerification.documentTimestamps.text') }}</MutedText>
    <TimestampDetails v-for="(entry, index) in timestamps" :key="index"
                      :stamp="entry.timestamp" :covers-whole-file="entry.coversWholeFile"/>
  </NeutralContainer>
</template>
