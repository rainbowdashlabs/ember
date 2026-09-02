/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
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
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import FederationCompatibilityBadge from './federationview/FederationCompatibilityBadge.vue'
import { federation } from '@/api'
import type { FederationContract, PartnerResponse, PairRequest } from '@/api/federation'
import { resolveFederationVersion } from '@/util/federationVersion'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useFlashMessage } from '@/composables/useFlashMessage'
import { apiErrorBody } from '@/util/apiError'
import { formatDate } from '@/util/format'

const { t } = useI18n()
const router = useRouter()
const { canManageFederation, loaded } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const partners = ref<PartnerResponse[]>([])
const pairRequests = ref<PairRequest[]>([])
const localContract = ref<FederationContract | null>(null)
const {message: success, flash} = useFlashMessage(3000)

const showInviteModal = ref(false)
const generatedCode = ref('')
const acceptCode = ref('')
const acceptError = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  const [p, r, info] = await Promise.all([
    federation.listPartners(),
    federation.listPairRequests(),
    federation.getFederationInfo(),
  ])
  partners.value = p
  pairRequests.value = r
  localContract.value = info.contract
}, {autoLoad: false})

async function handleAcceptRequest(id: number) {
  try {
    await federation.acceptPairRequest(id)
    flash(t('federation.connected'))
    await reload()
    refreshSidebarCounts()
  } catch { error.value = t('common.error') }
}

async function handleDeclineRequest(id: number) {
  try {
    await federation.declinePairRequest(id)
    await reload()
    refreshSidebarCounts()
  } catch { error.value = t('common.error') }
}

function openInviteModal() {
  acceptError.value = ''
  showInviteModal.value = true
}

async function generateInvite() {
  try {
    const res = await federation.createInvite()
    generatedCode.value = res.inviteCode
  } catch { error.value = t('common.error') }
}

/**
 * A code stands until it is used, so a refusal names what is in the way. The reason travels as its
 * own field and each one has its own sentence; anything unforeseen falls back to the general one
 * rather than claiming the code has run out.
 */
function refusalMessage(e: unknown): string {
  switch (apiErrorBody(e)?.error) {
    case 'MALFORMED': return t('federation.refused.malformed')
    case 'OTHER_INSTANCE': return t('federation.refused.otherInstance')
    case 'HOST_REFUSED': return t('federation.refused.hostRefused')
    case 'REMOTE_UNREACHABLE': return t('federation.refused.remoteUnreachable')
    case 'REMOTE_TIMEOUT': return t('federation.refused.remoteTimeout')
    case 'REMOTE_REFUSED': return t('federation.refused.remoteRefused')
    case 'REMOTE_STATION_GONE': return t('federation.refused.remoteStationGone')
    case 'CONTRACT_MISMATCH': return t('federation.refused.contractMismatch')
    case 'UNKNOWN_STATION': return t('federation.refused.unknownStation')
    case 'OWN_STATION': return t('federation.refused.ownStation')
    case 'ALREADY_PARTNERED': return t('federation.refused.alreadyPartnered')
    case 'REQUEST_PENDING': return t('federation.refused.requestPending')
    case 'SPENT_TOKEN': return t('federation.refused.spentToken')
    default: return t('federation.refused.unknown')
  }
}

async function handleAccept() {
  if (!acceptCode.value.trim()) return
  acceptError.value = ''
  try {
    const result = await federation.acceptInvite(acceptCode.value.trim())
    showInviteModal.value = false
    acceptCode.value = ''
    generatedCode.value = ''
    flash(result.status === 'ACTIVE' ? t('federation.connected') : t('federation.requestSent'))
    await reload()
  } catch (e) { acceptError.value = refusalMessage(e) }
}

watch(loaded, (v) => { if (v) reload() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.station-federation.title')"
      :subtitle="t('pages.station-federation.subtitle')"
  >
    <div class="flex items-center justify-end mb-4">
      <PrimaryButton v-if="canManageFederation()" @click="openInviteModal">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('federation.addPartner') }}
      </PrimaryButton>
    </div>

    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <AsyncSection
      :empty="partners.length === 0 && pairRequests.length === 0"
      :empty-message="t('federation.noPartners')"
      :error="error"
      :loading="loading"
    >
      <div v-if="pairRequests.length > 0" class="mb-6">
        <SubHeader class="mb-2">{{ t('federation.pairRequests') }}</SubHeader>
        <div class="space-y-2">
          <NeutralContainer v-for="req in pairRequests" :key="req.id" class="flex items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="font-medium">{{ req.stationName }}</div>
              <div class="text-xs text-[var(--text-muted)]">{{ formatDate(req.createdAt) }}</div>
            </div>
            <SecondaryButton compact @click="handleAcceptRequest(req.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/> {{ t('federation.acceptRequest') }}
            </SecondaryButton>
            <DeleteButton @click="handleDeclineRequest(req.id)"/>
          </NeutralContainer>
        </div>
      </div>

      <div class="space-y-2">
        <NeutralContainer v-for="p in partners" :key="p.partner.id" class="flex items-center gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium">{{ p.partnerStationName }}</div>
            <div v-if="p.partner.federationContract" class="text-xs text-[var(--text-muted)]">
              v{{ resolveFederationVersion(p.partner.federationContract.core) }}
            </div>
          </div>
          <FederationCompatibilityBadge :local="localContract" :partner="p.partner" />
          <SuccessBadge v-if="p.partner.status === 'ACTIVE'">{{ t('federation.active') }}</SuccessBadge>
          <ErrorBadge v-else-if="p.partner.status === 'SUSPENDED'">{{ t('federation.suspended') }}</ErrorBadge>
          <SecondaryBadge v-else>{{ t('federation.pending') }}</SecondaryBadge>
          <PrimaryButton compact @click="router.push({ name: 'station-federation-partner', params: { id: p.partner.id } })">
            <font-awesome-icon :icon="['fas', 'sliders']" class="mr-1" /> {{ t('federation.manage') }}
          </PrimaryButton>
        </NeutralContainer>
      </div>
    </AsyncSection>

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
          <Alert v-if="acceptError" variant="error" class="mt-2">{{ acceptError }}</Alert>
        </div>
      </div>
    </Modal>

  </ViewContent>
</template>
