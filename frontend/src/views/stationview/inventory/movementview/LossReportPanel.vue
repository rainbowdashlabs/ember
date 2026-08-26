/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {movements} from '@/api'
import type {LossReport} from '@/api/movements'

/**
 * What a report that a piece of gear is gone carries, read at both ends.
 *
 * <p>Two notes with two authors and neither standing in for the other: the member said what happened to
 * them, the manager said what the station is asking for. The document is evidence for this request and is
 * read by opening the movement, which is why it is here rather than on the item or the member.
 */
const props = defineProps<{
  movementId: number
  report: LossReport
}>()

const {t} = useI18n()
const error = ref('')

async function download() {
  error.value = ''
  try {
    const blob = await movements.downloadDocument(props.movementId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = props.report.documentName ?? 'document'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="loss-report">
    <SubHeader>{{ t('movements.lossReport.title') }}</SubHeader>
    <MutedText size="sm">{{ t('movements.lossReport.hint') }}</MutedText>

    <div v-if="props.report.managerNote" class="text-sm">
      <div class="text-(--text-muted)">{{ t('movements.lossReport.managerNote') }}</div>
      <div data-testid="loss-report-manager-note">{{ props.report.managerNote }}</div>
    </div>

    <div v-if="props.report.memberNote" class="text-sm">
      <div class="text-(--text-muted)">{{ t('movements.lossReport.memberNote') }}</div>
      <div data-testid="loss-report-member-note">{{ props.report.memberNote }}</div>
      <MemberName v-if="props.report.memberNoteBy" :identity="props.report.memberNoteBy" class="text-xs"/>
    </div>

    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="props.report.documentName" class="flex items-center gap-2 text-sm">
      <span data-testid="loss-report-document">{{ props.report.documentName }}</span>
      <DownloadButton data-testid="loss-report-download" @click="download"/>
    </div>
  </NeutralContainer>
</template>
