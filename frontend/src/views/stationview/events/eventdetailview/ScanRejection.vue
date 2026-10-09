/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import {PaperState, type PaperSubmission} from '@/api/generated/schema'

/** Why the latest scan handed in for a document was turned down. Nothing shows where it was not. */
defineProps<{
  paper: PaperSubmission | null
}>()

const {t} = useI18n()
</script>

<template>
  <MutedText v-if="paper?.state === PaperState.REJECTED" size="sm" tag="p" data-testid="document-scan-rejection">
    {{ t('events.documents.scanRejectedReason', {reason: paper.rejectReason ?? ''}) }}
  </MutedText>
</template>
