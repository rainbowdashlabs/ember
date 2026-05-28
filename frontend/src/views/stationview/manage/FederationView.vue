/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { useSession } from '@/composables/useSession'
import { federation } from '@/api'
import type { PartnerResponse, PairRequest } from '@/api/federation'
import { resolveFederationVersion } from '@/util/federationVersion'

const { t } = useI18n()
const router = useRouter()
const { canManageFederation, loaded } = useSession()

const partners = ref<PartnerResponse[]>([])
const pairRequests = ref<PairRequest[]>([])
const localVersion = ref('')
const loading = ref(true)
const error = ref('')
const success = ref('')

// Invite modal
const showInviteModal = ref(false)
const generatedCode = ref('')
const acceptCode = ref('')

async function loadData() {
  loading.value = true
  try {
    const [p, r, info] = await Promise.all([
      federation.listPartners(),
      federation.listPairRequests(),
      federation.getFederationInfo(),
    ])
    partners.value = p
    pairRequests.value = r
    localVersion.value = info.federationVersion
  }
  catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function handleAcceptRequest(id: number) {
  try {
    await federation.acceptPairRequest(id)
    success.value = t('federation.connected')
    setTimeout(() => { success.value = '' }, 3000)
    await loadData()
  } catch { error.value = t('common.error') }
}

async function handleDeclineRequest(id: number) {
  try {
    await federation.declinePairRequest(id)
    await loadData()
  } catch { error.value = t('common.error') }
}

async function generateInvite() {
  try {
    const res = await federation.createInvite()
    generatedCode.value = res.inviteCode
  } catch { error.value = t('common.error') }
}

async function handleAccept() {
  if (!acceptCode.value.trim()) return
  try {
    const result = await federation.acceptInvite(acceptCode.value.trim())
    showInviteModal.value = false
    acceptCode.value = ''
    generatedCode.value = ''
    success.value = result.status === 'ACTIVE' ? t('federation.connected') : t('federation.requestSent')
    setTimeout(() => { success.value = '' }, 3000)
    await loadData()
  } catch { error.value = t('federation.invalidCode') }
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('federation.title') }}</SectionHeader>
      <PrimaryButton v-if="canManageFederation()" @click="showInviteModal = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('federation.addPartner') }}
      </PrimaryButton>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <!-- Pending pair requests -->
    <div v-if="!loading && pairRequests.length > 0" class="mb-6">
      <SubHeader class="mb-2">{{ t('federation.pairRequests') }}</SubHeader>
      <div class="space-y-2">
        <NeutralContainer v-for="req in pairRequests" :key="req.id" class="flex items-center gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium">{{ req.stationName }}</div>
            <div class="text-xs text-[var(--text-muted)]">{{ new Date(req.createdAt).toLocaleDateString('de-DE') }}</div>
          </div>
          <SecondaryButton compact @click="handleAcceptRequest(req.id)">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/> {{ t('federation.acceptRequest') }}
          </SecondaryButton>
          <DeleteButton @click="handleDeclineRequest(req.id)"/>
        </NeutralContainer>
      </div>
    </div>

    <div v-if="!loading && partners.length === 0 && pairRequests.length === 0" class="text-center text-[var(--text-muted)] py-8">
      {{ t('federation.noPartners') }}
    </div>

    <div class="space-y-2">
      <NeutralContainer v-for="p in partners" :key="p.partner.id" class="flex items-center gap-2">
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ p.partnerStationName }}</div>
          <div class="text-xs text-[var(--text-muted)]">v{{ resolveFederationVersion(p.partner.federationVersion) }}</div>
        </div>
        <InfoBadge v-if="localVersion && p.partner.federationVersion !== localVersion" :title="t('federation.versionMismatchHint')">
          <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
          {{ t('federation.versionMismatch') }}
        </InfoBadge>
        <SuccessBadge v-if="p.partner.status === 'ACTIVE'">{{ t('federation.active') }}</SuccessBadge>
        <ErrorBadge v-else-if="p.partner.status === 'SUSPENDED'">{{ t('federation.suspended') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('federation.pending') }}</SecondaryBadge>
        <PrimaryButton compact @click="router.push({ name: 'station-federation-partner', params: { id: p.partner.id } })">
          <font-awesome-icon :icon="['fas', 'sliders']" class="mr-1" /> {{ t('federation.manage') }}
        </PrimaryButton>
      </NeutralContainer>
    </div>

    <!-- Invite / Accept Modal -->
    <Modal v-model="showInviteModal">
      <SubHeader class="mb-3">{{ t('federation.addPartner') }}</SubHeader>
      <div class="space-y-4">
        <div>
          <SubHeader>{{ t('federation.createInvite') }}</SubHeader>
          <p class="text-sm text-[var(--text-muted)] mb-2">{{ t('federation.createInviteHint') }}</p>
          <PrimaryButton @click="generateInvite">{{ t('federation.generate') }}</PrimaryButton>
          <div v-if="generatedCode" class="mt-2 p-3 bg-[var(--bg-accent)] rounded font-mono text-sm text-center select-all break-all">
            {{ generatedCode }}
          </div>
        </div>
        <div class="border-t border-[var(--border)] pt-4">
          <SubHeader>{{ t('federation.acceptInvite') }}</SubHeader>
          <p class="text-sm text-[var(--text-muted)] mb-2">{{ t('federation.acceptInviteHint') }}</p>
          <form @submit.prevent="handleAccept" class="flex gap-2">
            <TextInput v-model="acceptCode" :placeholder="t('federation.codePlaceholder')" class="flex-1 font-mono text-sm" />
            <PrimaryButton type="submit" :disabled="!acceptCode.trim()">{{ t('federation.connect') }}</PrimaryButton>
          </form>
        </div>
      </div>
    </Modal>

  </ViewContent>
</template>
