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
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import FederationCompatibilityBadge from './federationview/FederationCompatibilityBadge.vue'
import { federation } from '@/api'
import type { FederationContract, PartnerResponse, FederationCapability } from '@/api/federation'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { partnerCompatibility, resolveFederationVersion } from '@/util/federationVersion'
import { formatDate } from '@/util/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()

const partnerId = computed(() => Number(route.params.id))
const partner = ref<PartnerResponse | null>(null)
const capabilities = ref<FederationCapability[]>([])
const localContract = ref<FederationContract | null>(null)

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const [p, caps, info] = await Promise.all([
    federation.getPartner(partnerId.value),
    federation.getCapabilities(partnerId.value),
    federation.getFederationInfo(),
  ])
  partner.value = p
  capabilities.value = caps
  localContract.value = info.contract
}, {autoLoad: false})

const compatibility = computed(() =>
  partner.value ? partnerCompatibility(localContract.value, partner.value.partner) : 'compatible')

interface CapRow {
  label: string
  capability: string
  importCap: FederationCapability | undefined
  exportCap: FederationCapability | undefined
}

const capRows = computed<CapRow[]>(() => {
  const curated = ['KB_SHARE', 'QUIZ_SHARE', 'PROTOCOL_SHARE', 'EVENT_SHARE', 'BOARD_SHARE', 'NEWS_SHARE', 'INVENTORY_LEND']
  const types = [
    ...curated,
    ...Object.keys(localContract.value?.features ?? {}).filter(type => !curated.includes(type)),
  ]
  const labels: Record<string, string> = {
    KB_SHARE: t('federation.cap.kb'),
    QUIZ_SHARE: t('federation.cap.quiz'),
    PROTOCOL_SHARE: t('federation.cap.protocol'),
    EVENT_SHARE: t('federation.cap.event'),
    BOARD_SHARE: t('federation.cap.board'),
    NEWS_SHARE: t('federation.cap.news'),
    INVENTORY_LEND: t('federation.cap.inventory'),
  }
  return types.map(cap => ({
    label: labels[cap] ?? cap,
    capability: cap,
    importCap: capabilities.value.find(c => c.capability === cap && c.direction === 'IMPORT'),
    exportCap: capabilities.value.find(c => c.capability === cap && c.direction === 'EXPORT'),
  }))
})

async function toggleCap(capability: string, direction: string, currentEnabled: boolean) {
  try {
    capabilities.value = await federation.setCapabilities(partnerId.value, [
      { capability, direction, enabled: !currentEnabled },
    ])
  } catch { error.value = t('common.error') }
}

async function handleSuspend() {
  try { await federation.suspendPartner(partnerId.value); await loadData() }
  catch { error.value = t('common.error') }
}

async function handleResume() {
  try { await federation.resumePartner(partnerId.value); await loadData() }
  catch { error.value = t('common.error') }
}

async function handleEnd() {
  try {
    await federation.endFederation(partnerId.value)
    router.push({ name: 'station-federation' })
  } catch { error.value = t('common.error') }
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.station-federation-partner.title')"
      :subtitle="t('pages.station-federation-partner.subtitle')"
  >
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'station-federation' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ partner?.partnerStationName ?? '' }}</SectionHeader>
      <template v-if="partner">
        <SuccessBadge v-if="partner.partner.status === 'ACTIVE'">{{ t('federation.active') }}</SuccessBadge>
        <ErrorBadge v-else-if="partner.partner.status === 'SUSPENDED'">{{ t('federation.suspended') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('federation.pending') }}</SecondaryBadge>
      </template>
      <div class="flex gap-2 ml-auto">
        <PrimaryButton v-if="partner?.partner.status === 'SUSPENDED'" class="!text-sm" @click="handleResume">
          {{ t('federation.resume') }}
        </PrimaryButton>
        <SecondaryButton v-if="partner?.partner.status === 'ACTIVE'" class="!text-sm" @click="handleSuspend">
          {{ t('federation.suspend') }}
        </SecondaryButton>
        <DeleteButton :label="t('federation.end')" @click="handleEnd" />
      </div>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading && partner">
      <MutedText tag="p" size="sm">
        <template v-if="partner.partner.federationContract">
          {{ t('federation.version') }}: v{{ resolveFederationVersion(partner.partner.federationContract.core) }},
        </template>
        {{ t('federation.since') }}: {{ formatDate(partner.partner.createdAt) }}
      </MutedText>

      <Alert v-if="compatibility === 'incompatible'" variant="error">{{ t('federation.incompatibleHint') }}</Alert>
      <Alert v-else-if="compatibility === 'unknown'" variant="info">{{ t('federation.versionUnknownHint') }}</Alert>

      <NeutralContainer>
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-[var(--border)]">
              <th class="text-left py-2 px-3 font-medium">{{ t('federation.feature') }}</th>
              <Th align="center" class="font-medium w-32">{{ t('federation.receive') }}</th>
              <Th align="center" class="font-medium w-32">{{ t('federation.send') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in capRows" :key="row.capability" class="border-b border-[var(--border)] last:border-b-0">
              <Td>
                {{ row.label }}
                <FederationCompatibilityBadge
                  class="ml-2"
                  :local="localContract"
                  :partner="partner.partner"
                  :capability="row.capability"
                />
              </Td>
              <Td align="center">
                <ToggleInput
                  :model-value="row.importCap?.enabled ?? false"
                  @update:model-value="toggleCap(row.capability, 'IMPORT', row.importCap?.enabled ?? false)"
                />
              </Td>
              <Td align="center">
                <ToggleInput
                  :model-value="row.exportCap?.enabled ?? false"
                  @update:model-value="toggleCap(row.capability, 'EXPORT', row.exportCap?.enabled ?? false)"
                />
              </Td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
