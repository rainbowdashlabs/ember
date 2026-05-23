/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
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
import { federation } from '@/api'
import type { PartnerResponse, FederationCapability } from '@/api/federation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()

const partnerId = computed(() => Number(route.params.id))
const partner = ref<PartnerResponse | null>(null)
const capabilities = ref<FederationCapability[]>([])
const loading = ref(true)
const error = ref('')

interface CapRow {
  label: string
  capability: string
  importCap: FederationCapability | undefined
  exportCap: FederationCapability | undefined
}

const capRows = computed<CapRow[]>(() => {
  const types = ['KB_SHARE', 'QUIZ_SHARE', 'PROTOCOL_SHARE', 'INVENTORY_LEND']
  const labels: Record<string, string> = {
    KB_SHARE: t('federation.cap.kb'),
    QUIZ_SHARE: t('federation.cap.quiz'),
    PROTOCOL_SHARE: t('federation.cap.protocol'),
    INVENTORY_LEND: t('federation.cap.inventory'),
  }
  return types.map(cap => ({
    label: labels[cap] ?? cap,
    capability: cap,
    importCap: capabilities.value.find(c => c.capability === cap && c.direction === 'IMPORT'),
    exportCap: capabilities.value.find(c => c.capability === cap && c.direction === 'EXPORT'),
  }))
})

async function loadData() {
  loading.value = true
  try {
    partner.value = await federation.getPartner(partnerId.value)
    capabilities.value = await federation.getCapabilities(partnerId.value)
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

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
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-3 mb-4">
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
      <p class="text-sm text-[var(--text-muted)] mb-4">
        {{ t('federation.version') }}: v{{ partner.partner.federationVersion }}
        &mdash; {{ t('federation.since') }}: {{ new Date(partner.partner.createdAt).toLocaleDateString('de-DE') }}
      </p>

      <!-- Capabilities table -->
      <NeutralContainer>
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-[var(--border)]">
              <th class="text-left py-2 px-3 font-medium">{{ t('federation.feature') }}</th>
              <th class="text-center py-2 px-3 font-medium w-32">{{ t('federation.receive') }}</th>
              <th class="text-center py-2 px-3 font-medium w-32">{{ t('federation.send') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in capRows" :key="row.capability" class="border-b border-[var(--border)] last:border-b-0">
              <td class="py-2 px-3">{{ row.label }}</td>
              <td class="py-2 px-3 text-center">
                <ToggleInput
                  :model-value="row.importCap?.enabled ?? false"
                  @update:model-value="toggleCap(row.capability, 'IMPORT', row.importCap?.enabled ?? false)"
                />
              </td>
              <td class="py-2 px-3 text-center">
                <ToggleInput
                  :model-value="row.exportCap?.enabled ?? false"
                  @update:model-value="toggleCap(row.capability, 'EXPORT', row.exportCap?.enabled ?? false)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
