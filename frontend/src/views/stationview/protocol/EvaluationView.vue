/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol, stationMembers } from '@/api'
import type { EvaluationResponse } from '@/api/protocol'
import type { StationMember } from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'
import EvaluationTable from './evaluationview/EvaluationTable.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()


const runId = computed(() => Number(route.params.id))
const evalData = ref<EvaluationResponse | null>(null)
const memberMap = ref<Map<number, StationMember>>(new Map())

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const [ev, members] = await Promise.all([
    protocol.getEvaluation(runId.value),
    stationMembers.listMembers(),
  ])
  evalData.value = ev
  memberMap.value = new Map(members.map(m => [m.id, m]))
}, {autoLoad: false})

function exportAllZip() {
  return protocol.exportAllZip(runId.value)
}

function exportTablePdf() {
  return protocol.evaluationPdf(runId.value)
}

function openMemberPdf(memberId: number) {
  return protocol.exportMemberPdf(runId.value, memberId)
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-run-detail', params: { id: runId } })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ t('protocol.evaluation') }}</SectionHeader>
      <div class="flex gap-2 ml-auto">
        <PrimaryButton @click="exportTablePdf">
          <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> {{ t('protocol.exportTable') }}
        </PrimaryButton>
        <SecondaryButton @click="exportAllZip">
          <font-awesome-icon :icon="['fas', 'download']" class="mr-1" /> {{ t('protocol.exportAll') }}
        </SecondaryButton>
      </div>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading && evalData">
      <MutedText tag="p" size="sm">
        {{ evalData.protocolName }} — {{ new Date(evalData.testDate).toLocaleDateString('de-DE') }}
        <template v-if="evalData.passThreshold"> — {{ t('protocol.threshold') }}: {{ evalData.passThreshold }}P</template>
      </MutedText>

      <EvaluationTable
        :eval-data="evalData"
        :member-map="memberMap"
        @member-pdf="openMemberPdf"
      />
    </template>
  </ViewContent>
</template>
