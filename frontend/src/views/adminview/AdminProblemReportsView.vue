/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {ProblemReport} from '@/api/problemReports'
import {listReports, acknowledgeReport, acknowledgeAllReports, deleteReport} from '@/api/problemReports'

const {t} = useI18n()

const reports = ref<ProblemReport[]>([])
const loading = ref(true)
const includeAcknowledged = ref(false)
const expandedId = ref<number | null>(null)

async function loadData() {
  loading.value = true
  try {
    reports.value = await listReports(includeAcknowledged.value)
  } catch { /* ignore */ }
  loading.value = false
}

async function ack(id: number) {
  await acknowledgeReport(id)
  reports.value = reports.value.map(r => r.id === id ? {...r, acknowledged: true} : r)
  if (!includeAcknowledged.value) {
    reports.value = reports.value.filter(r => r.id !== id)
  }
}

async function ackAll() {
  await acknowledgeAllReports()
  if (!includeAcknowledged.value) {
    reports.value = []
  } else {
    reports.value = reports.value.map(r => ({...r, acknowledged: true}))
  }
}

async function remove(id: number) {
  await deleteReport(id)
  reports.value = reports.value.filter(r => r.id !== id)
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function parseRequests(json: string | null | undefined): any[] {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

function toggle(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>
          <font-awesome-icon :icon="['fas', 'flag']" class="mr-2"/>
          {{ t('problemReport.adminTitle') }}
          <span v-if="reports.length > 0"> ({{ reports.length }})</span>
        </SectionHeader>
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput v-model="includeAcknowledged" @update:model-value="loadData"/>
            {{ t('problemReport.showAcknowledged') }}
          </label>
          <SecondaryButton v-if="reports.some(r => !r.acknowledged)" :icon="['fas', 'check-double']" @click="ackAll">
            {{ t('problemReport.acknowledgeAll') }}
          </SecondaryButton>
        </div>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <EmptyState v-if="!loading && reports.length === 0">{{ t('problemReport.empty') }}</EmptyState>

      <NeutralContainer v-for="r in reports" :key="r.id" class="space-y-2">
        <div class="flex items-start justify-between gap-3 cursor-pointer" @click="toggle(r.id)">
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-1">
              <span class="text-sm font-semibold">{{ r.reporterName }}</span>
              <SuccessBadge v-if="r.acknowledged">{{ t('problemReport.acknowledged') }}</SuccessBadge>
              <MutedText size="xs">{{ formatDate(r.createdAt) }}</MutedText>
            </div>
            <p class="text-sm">{{ r.message }}</p>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <IconButton v-if="!r.acknowledged" :icon="['fas', 'check']" :label="t('problemReport.acknowledge')"
                        class="text-success hover:bg-success/15" @click.stop="ack(r.id)"/>
            <DeleteButton @click.stop="remove(r.id)"/>
            <font-awesome-icon :icon="['fas', expandedId === r.id ? 'chevron-up' : 'chevron-down']"
                               class="h-3 w-3 text-(--text-muted) ml-1"/>
          </div>
        </div>

        <template v-if="expandedId === r.id">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs border-t border-(--border) pt-2">
            <div v-if="r.pageUrl">
              <span class="font-semibold text-(--text-muted)">{{ t('problemReport.page') }}:</span>
              <span class="ml-1 break-all">{{ r.pageUrl }}</span>
            </div>
            <div v-if="r.userRoles">
              <span class="font-semibold text-(--text-muted)">{{ t('problemReport.roles') }}:</span>
              <span class="ml-1">{{ r.userRoles }}</span>
            </div>
            <div v-if="r.browserInfo">
              <span class="font-semibold text-(--text-muted)">{{ t('problemReport.browser') }}:</span>
              <span class="ml-1 break-all">{{ r.browserInfo }}</span>
            </div>
            <div v-if="r.screenSize">
              <span class="font-semibold text-(--text-muted)">{{ t('problemReport.screen') }}:</span>
              <span class="ml-1">{{ r.screenSize }}</span>
            </div>
          </div>

          <div v-if="parseRequests(r.recentRequests).length > 0" class="border-t border-(--border) pt-2">
            <p class="text-xs font-semibold text-(--text-muted) mb-1">{{ t('problemReport.recentRequests') }}</p>
            <div class="overflow-x-auto">
              <table class="w-full text-xs font-mono border-collapse">
                <thead>
                  <tr class="text-left text-(--text-muted)">
                    <th class="px-2 py-1">{{ t('problemReport.reqTime') }}</th>
                    <th class="px-2 py-1">{{ t('problemReport.reqMethod') }}</th>
                    <th class="px-2 py-1">{{ t('problemReport.reqUrl') }}</th>
                    <th class="px-2 py-1">{{ t('problemReport.reqStatus') }}</th>
                    <th class="px-2 py-1">{{ t('problemReport.reqDuration') }}</th>
                    <th class="px-2 py-1">{{ t('problemReport.reqError') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(req, i) in parseRequests(r.recentRequests)" :key="i"
                      :class="req.error ? 'text-error' : ''">
                    <td class="px-2 py-0.5">{{ req.timestamp?.substring(11, 19) }}</td>
                    <td class="px-2 py-0.5">{{ req.method }}</td>
                    <td class="px-2 py-0.5 break-all">{{ req.url }}</td>
                    <td class="px-2 py-0.5">
                      <ErrorBadge v-if="req.status && req.status >= 400">{{ req.status }}</ErrorBadge>
                      <SuccessBadge v-else-if="req.status">{{ req.status }}</SuccessBadge>
                      <span v-else>–</span>
                    </td>
                    <td class="px-2 py-0.5">{{ req.duration }}ms</td>
                    <td class="px-2 py-0.5 break-all">{{ req.error ?? '' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
