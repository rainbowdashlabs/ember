/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import FederationCompatibilityBadge from './federationview/FederationCompatibilityBadge.vue'
import IncomingPairRequests, { type IncomingPairRequest } from './federationview/IncomingPairRequests.vue'
import OutgoingPairRequests from './federationview/OutgoingPairRequests.vue'
import { federation } from '@/api'
import {
  FederationStatus,
  type FederationContract,
  type OutgoingPairRequestResponse,
  type PairRequestResponse,
  type PartnerResponse,
  type RemotePairRequestResponse,
} from '@/api/generated/schema'
import { resolveFederationVersion } from '@/util/federationVersion'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useFlashMessage } from '@/composables/useFlashMessage'
import { apiErrorBody } from '@/util/apiError'
import { describeFailure, type Failure } from '@/util/failure'

const { t } = useI18n()
const router = useRouter()
const { canManageFederation, loaded } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const partners = ref<PartnerResponse[]>([])
const pairRequests = ref<PairRequestResponse[]>([])
const remotePairRequests = ref<RemotePairRequestResponse[]>([])
const outgoingRequests = ref<OutgoingPairRequestResponse[]>([])
const localContract = ref<FederationContract | null>(null)
const {message: success, flash} = useFlashMessage(3000)

const showInviteModal = ref(false)
const generatedCode = ref('')
const acceptCode = ref('')
const acceptFailure = ref<Failure | null>(null)

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [p, r, remote, outgoing, info] = await Promise.all([
    federation.listPartners(),
    federation.listPairRequests(),
    federation.listRemotePairRequests(),
    federation.listOutgoingPairRequests(),
    federation.getFederationInfo(),
  ])
  partners.value = p
  pairRequests.value = r
  remotePairRequests.value = remote
  outgoingRequests.value = outgoing
  localContract.value = info.contract
}, {autoLoad: false})

const nothingToShow = computed(() => partners.value.length === 0
  && pairRequests.value.length === 0
  && remotePairRequests.value.length === 0
  && outgoingRequests.value.length === 0)

/** A request of another instance is answered over its own route, since its id counts apart from the local ones. */
async function handleAcceptRequest(request: IncomingPairRequest) {
  try {
    await (request.instanceHost
      ? federation.acceptRemotePairRequest(request.id)
      : federation.acceptPairRequest(request.id))
    flash(t('federation.connected'))
    await reload()
    refreshSidebarCounts()
  } catch (e) { failure.value = federationFailure(e) }
}

async function handleDeclineRequest(request: IncomingPairRequest) {
  try {
    await (request.instanceHost
      ? federation.declineRemotePairRequest(request.id)
      : federation.declinePairRequest(request.id))
    await reload()
    refreshSidebarCounts()
  } catch (e) { failure.value = federationFailure(e) }
}

function openInviteModal() {
  acceptFailure.value = null
  showInviteModal.value = true
}

async function generateInvite() {
  try {
    const res = await federation.createInvite()
    generatedCode.value = res.inviteCode
  } catch (e) { failure.value = federationFailure(e) }
}

/**
 * A code stands until it is used, so a refusal names what is in the way. The reason travels as its
 * own field and each one has its own sentence; anything unforeseen falls back to the general one
 * rather than claiming the code has run out.
 *
 * <p>Nothing comes back where the server named no reason at all, which is a request that never
 * reached the far side rather than one it turned down. The described failure says that better.
 */
function refusalReason(e: unknown): string | undefined {
  const body = apiErrorBody(e)
  const reason = body?.error
  if (!reason || body?.code) return undefined
  switch (reason) {
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

/**
 * The failure with the federation reason in front of it, where there was one.
 *
 * <p>A partner turning us down is not a fault in Ember and offering to report it buries the reports
 * that are, so a named refusal is never reportable however the status came back.
 */
function federationFailure(e: unknown): Failure {
  const described = describeFailure(e, t)
  const refusal = refusalReason(e)
  return refusal ? {...described, message: refusal, reportable: false} : described
}

async function handleAccept() {
  if (!acceptCode.value.trim()) return
  acceptFailure.value = null
  try {
    const result = await federation.acceptInvite(acceptCode.value.trim())
    showInviteModal.value = false
    acceptCode.value = ''
    generatedCode.value = ''
    flash(result.status === FederationStatus.ACTIVE ? t('federation.connected') : t('federation.requestSent'))
    await reload()
  } catch (e) { acceptFailure.value = federationFailure(e) }
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
      :empty="nothingToShow"
      :empty-message="t('federation.noPartners')"
      :failure="failure"
      :loading="loading"
    >
      <IncomingPairRequests
          :local="pairRequests"
          :remote="remotePairRequests"
          @accept="handleAcceptRequest"
          @decline="handleDeclineRequest"
      />
      <OutgoingPairRequests :requests="outgoingRequests"/>

      <div class="space-y-2">
        <NeutralContainer v-for="p in partners" :key="p.partner.id" class="flex items-center gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium">{{ p.partnerStationName }}</div>
            <div v-if="p.partner.federationContract" class="text-xs text-[var(--text-muted)]">
              v{{ resolveFederationVersion(p.partner.federationContract.core) }}
            </div>
          </div>
          <FederationCompatibilityBadge :local="localContract" :partner="p.partner" />
          <SuccessBadge v-if="p.partner.status === FederationStatus.ACTIVE">{{ t('federation.active') }}</SuccessBadge>
          <ErrorBadge v-else-if="p.partner.status === FederationStatus.SUSPENDED">{{ t('federation.suspended') }}</ErrorBadge>
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
          <form class="flex gap-2" @submit.prevent="handleAccept">
            <TextInput v-model="acceptCode" :placeholder="t('federation.codePlaceholder')" class="flex-1 font-mono text-sm" />
            <PrimaryButton type="submit" :disabled="!acceptCode.trim()">{{ t('federation.connect') }}</PrimaryButton>
          </form>
          <FailureAlert :failure="acceptFailure" class="mt-2"/>
        </div>
      </div>
    </Modal>

  </ViewContent>
</template>
