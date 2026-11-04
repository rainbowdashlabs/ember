/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * What the station wrote as the trial began, kept on the page for as long as the trial runs, so a
 * family on a list that sends no mail reads it as well.
 */
const props = defineProps<{message: string}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="waitlist-trial-message">
    <SectionHeader class="text-lg font-semibold">{{ t('waitingList.publicStatus.trialMessageTitle') }}</SectionHeader>
    <p class="text-sm whitespace-pre-line">{{ props.message }}</p>
  </NeutralContainer>
</template>
