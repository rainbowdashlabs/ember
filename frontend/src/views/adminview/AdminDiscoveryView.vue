/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {discovery} from '@/api'
import type {
  BlocklistKind,
  DiscoveryBlocklistEntry,
  DiscoveryIdentity,
  DiscoveryInfoProbe,
  DiscoveryPeer,
  DiscoveryPeerSource,
  DiscoverySettings,
} from '@/api/discovery'

const {t} = useI18n()

const loading = ref(true)
const error = ref('')
const flash = ref('')

const identity = ref<DiscoveryIdentity | null>(null)
const settings = ref<DiscoverySettings | null>(null)
const peers = ref<DiscoveryPeer[]>([])
const blocklist = ref<DiscoveryBlocklistEntry[]>([])

const draftEnabled = ref(true)
const draftDepth = ref(2)
const draftInterval = ref(60)

const probeBaseUrl = ref('')
const probeExpectedKey = ref('')
const probeResult = ref<DiscoveryInfoProbe | null>(null)
const probing = ref(false)
const probeError = ref('')

const blocklistValue = ref('')
const blocklistKind = ref<BlocklistKind>('BASE_URL')
const blocklistNote = ref('')

const peerActionInFlight = ref<string | null>(null)

const sourceLabel: Record<DiscoveryPeerSource, string> = {
  BOOTSTRAP: t('adminDiscovery.sourceBootstrap'),
  GOSSIP: t('adminDiscovery.sourceGossip'),
  MANUAL: t('adminDiscovery.sourceManual'),
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [id, set, pl, bl] = await Promise.all([
      discovery.getDiscoveryIdentity(),
      discovery.getDiscoverySettings(),
      discovery.listDiscoveryPeers(),
      discovery.listDiscoveryBlocklist(),
    ])
    identity.value = id
    settings.value = set
    peers.value = pl
    blocklist.value = bl
    draftEnabled.value = set.enabled
    draftDepth.value = set.maxDepth
    draftInterval.value = set.pingIntervalMinutes
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!settings.value) return
  flash.value = ''
  try {
    settings.value = await discovery.updateDiscoverySettings({
      enabled: draftEnabled.value,
      maxDepth: draftDepth.value,
      pingIntervalMinutes: draftInterval.value,
    })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

async function probe() {
  probing.value = true
  probeError.value = ''
  probeResult.value = null
  try {
    probeResult.value = await discovery.probeDiscoveryPeer(probeBaseUrl.value.trim())
  } catch {
    probeError.value = t('adminDiscovery.probeFailed')
  } finally {
    probing.value = false
  }
}

async function addPeer() {
  if (!probeBaseUrl.value.trim()) return
  flash.value = ''
  try {
    await discovery.addDiscoveryPeer(probeBaseUrl.value.trim(), probeExpectedKey.value.trim() || undefined)
    probeBaseUrl.value = ''
    probeExpectedKey.value = ''
    probeResult.value = null
    flash.value = t('adminDiscovery.added')
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  }
}

async function runPeerAction(p: DiscoveryPeer, action: () => Promise<unknown>) {
  peerActionInFlight.value = p.publicKey
  try {
    await action()
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  } finally {
    peerActionInFlight.value = null
  }
}

async function deletePeer(p: DiscoveryPeer) {
  if (!confirm(t('adminDiscovery.confirmDeletePeer'))) return
  await runPeerAction(p, () => discovery.deleteDiscoveryPeer(p.publicKey))
}

async function discoverNow() {
  flash.value = ''
  try {
    const result = await discovery.discoverNow()
    flash.value = t('adminDiscovery.discoverNowResult', {
      pings: result.pingsDispatched,
      stations: result.stationsFetched,
    })
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  }
}

async function seedFederation() {
  flash.value = ''
  try {
    const count = await discovery.seedFromFederation()
    flash.value = t('adminDiscovery.seedFederationResult', {count})
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  }
}

async function addToBlocklist() {
  if (!blocklistValue.value.trim()) return
  try {
    await discovery.addToBlocklist(blocklistValue.value.trim(), blocklistKind.value, blocklistNote.value.trim() || undefined)
    blocklistValue.value = ''
    blocklistNote.value = ''
    blocklist.value = await discovery.listDiscoveryBlocklist()
  } catch {
    error.value = t('common.error')
  }
}

async function removeFromBlocklist(entry: DiscoveryBlocklistEntry) {
  try {
    await discovery.removeFromBlocklist(entry.value)
    blocklist.value = await discovery.listDiscoveryBlocklist()
  } catch {
    error.value = t('common.error')
  }
}

function formatTimestamp(ts: string | null): string {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const sortedPeers = computed(() =>
  [...peers.value].sort((a, b) => (a.lastSeenAt > b.lastSeenAt ? -1 : 1)),
)

onMounted(loadAll)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <SectionHeader>{{ t('adminDiscovery.title') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('adminDiscovery.subtitle') }}</p>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="flash" variant="success">{{ flash }}</Alert>

      <template v-if="!loading && identity">
        <!-- Identity card -->
        <NeutralContainer class="space-y-2">
          <SubHeader>{{ t('adminDiscovery.identity') }}</SubHeader>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
            <div>
              <FieldLabel>{{ t('adminDiscovery.instanceId') }}</FieldLabel>
              <code class="break-all">{{ identity.instanceId }}</code>
            </div>
            <div>
              <FieldLabel>{{ t('adminDiscovery.baseUrl') }}</FieldLabel>
              <code class="break-all">{{ identity.baseUrl }}</code>
            </div>
            <div class="md:col-span-2">
              <FieldLabel>{{ t('adminDiscovery.publicKey') }}</FieldLabel>
              <code class="break-all text-xs">{{ identity.publicKey }}</code>
            </div>
          </div>
        </NeutralContainer>

        <!-- Settings card -->
        <NeutralContainer v-if="settings" class="space-y-3">
          <SubHeader>{{ t('adminDiscovery.settings') }}</SubHeader>
          <div class="space-y-3">
            <div class="flex items-center justify-between gap-3">
              <div>
                <FieldLabel>{{ t('adminDiscovery.enabled') }}</FieldLabel>
                <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.enabledHelp') }}</p>
              </div>
              <ToggleInput v-model="draftEnabled"/>
            </div>
            <div>
              <FieldLabel>{{ t('adminDiscovery.maxDepth') }}</FieldLabel>
              <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.maxDepthHelp') }}</p>
              <NumberInput v-model="draftDepth" :min="0" :max="settings.hardMaxDepth"/>
            </div>
            <div>
              <FieldLabel>{{ t('adminDiscovery.pingInterval') }}</FieldLabel>
              <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.pingIntervalHelp') }}</p>
              <NumberInput v-model="draftInterval" :min="60"/>
            </div>
            <div class="flex flex-wrap gap-2 pt-2">
              <SaveButton :action="saveSettings"/>
              <InfoButton @click="discoverNow">{{ t('adminDiscovery.discoverNow') }}</InfoButton>
              <SecondaryButton @click="seedFederation">{{ t('adminDiscovery.seedFederation') }}</SecondaryButton>
            </div>
          </div>
        </NeutralContainer>

        <!-- Add peer -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminDiscovery.addPeer') }}</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('adminDiscovery.addPeerHelp') }}</p>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <TextInput v-model="probeBaseUrl" :placeholder="t('adminDiscovery.baseUrlPlaceholder')"/>
            <TextInput v-model="probeExpectedKey" :placeholder="t('adminDiscovery.expectedKey')"/>
          </div>
          <div class="flex flex-wrap gap-2">
            <SecondaryButton :disabled="!probeBaseUrl.trim() || probing" @click="probe">
              {{ t('adminDiscovery.probe') }}
            </SecondaryButton>
            <SuccessButton :disabled="!probeBaseUrl.trim()" @click="addPeer">
              {{ t('adminDiscovery.add') }}
            </SuccessButton>
          </div>
          <Alert v-if="probeError" variant="error">{{ probeError }}</Alert>
          <Alert v-if="probeResult" variant="success">
            {{ t('adminDiscovery.probeOk') }}
            <code class="ml-2 text-xs break-all">{{ probeResult.instanceId }}</code>
          </Alert>
        </NeutralContainer>

        <!-- Peer registry -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminDiscovery.peers') }}</SubHeader>
          <EmptyState v-if="sortedPeers.length === 0">{{ t('adminDiscovery.peersEmpty') }}</EmptyState>
          <div v-else class="space-y-2">
            <div
              v-for="p in sortedPeers"
              :key="p.publicKey"
              class="rounded-(--radius-theme) border border-bg-light-accent dark:border-bg-dark-accent p-3 space-y-2"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="space-y-1 min-w-0">
                  <p class="font-medium break-all">{{ p.baseUrl }}</p>
                  <p class="text-xs text-(--text-muted)">
                    <code>{{ p.instanceId }}</code>
                  </p>
                </div>
                <div class="flex flex-wrap items-center gap-2">
                  <SecondaryBadge>{{ sourceLabel[p.source] }}</SecondaryBadge>
                  <PrimaryBadge v-if="p.reachable">{{ t('adminDiscovery.peerTable.reachable') }}</PrimaryBadge>
                  <ErrorBadge v-else>{{ t('adminDiscovery.no') }}</ErrorBadge>
                  <ErrorBadge v-if="p.blocked">{{ t('adminDiscovery.peerTable.blocked') }}</ErrorBadge>
                  <SuccessBadge v-if="p.reputation >= 0">{{ p.reputation }}</SuccessBadge>
                  <ErrorBadge v-else>{{ p.reputation }}</ErrorBadge>
                </div>
              </div>
              <p class="text-xs text-(--text-muted)">
                {{ t('adminDiscovery.peerTable.lastSeen') }}: {{ formatTimestamp(p.lastSeenAt) }}
              </p>
              <div class="flex flex-wrap gap-1">
                <IconButton
                  :icon="['fas', 'arrow-up']"
                  :label="t('adminDiscovery.upvote')"
                  :disabled="peerActionInFlight === p.publicKey"
                  @click="runPeerAction(p, () => discovery.upvoteDiscoveryPeer(p.publicKey))"
                />
                <IconButton
                  :icon="['fas', 'arrow-down']"
                  :label="t('adminDiscovery.downvote')"
                  :disabled="peerActionInFlight === p.publicKey"
                  @click="runPeerAction(p, () => discovery.downvoteDiscoveryPeer(p.publicKey))"
                />
                <IconButton
                  v-if="!p.blocked"
                  :icon="['fas', 'ban']"
                  :label="t('adminDiscovery.block')"
                  :disabled="peerActionInFlight === p.publicKey"
                  @click="runPeerAction(p, () => discovery.blockDiscoveryPeer(p.publicKey))"
                />
                <IconButton
                  v-else
                  :icon="['fas', 'check']"
                  :label="t('adminDiscovery.unblock')"
                  :disabled="peerActionInFlight === p.publicKey"
                  @click="runPeerAction(p, () => discovery.unblockDiscoveryPeer(p.publicKey))"
                />
                <IconButton
                  :icon="['fas', 'satellite-dish']"
                  :label="t('adminDiscovery.pingNow')"
                  :disabled="peerActionInFlight === p.publicKey"
                  @click="runPeerAction(p, () => discovery.pingDiscoveryPeerNow(p.publicKey))"
                />
                <DeleteButton
                  :disabled="peerActionInFlight === p.publicKey"
                  @click="deletePeer(p)"
                />
              </div>
            </div>
          </div>
        </NeutralContainer>

        <!-- Blocklist -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminDiscovery.blocklist') }}</SubHeader>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <TextInput v-model="blocklistValue" :placeholder="t('adminDiscovery.blocklistValue')"/>
            <SelectInput v-model="blocklistKind">
              <option value="BASE_URL">{{ t('adminDiscovery.blocklistKindBaseUrl') }}</option>
              <option value="PUBLIC_KEY">{{ t('adminDiscovery.blocklistKindPublicKey') }}</option>
            </SelectInput>
            <TextInput v-model="blocklistNote" :placeholder="t('adminDiscovery.blocklistNote')"/>
          </div>
          <PrimaryButton :disabled="!blocklistValue.trim()" @click="addToBlocklist">
            {{ t('adminDiscovery.blocklistAdd') }}
          </PrimaryButton>
          <EmptyState v-if="blocklist.length === 0">{{ t('adminDiscovery.blocklistEmpty') }}</EmptyState>
          <ul v-else class="divide-y divide-bg-light-accent dark:divide-bg-dark-accent">
            <li v-for="entry in blocklist" :key="entry.value" class="flex items-center justify-between gap-3 py-2">
              <div class="min-w-0">
                <p class="text-sm font-medium break-all">{{ entry.value }}</p>
                <p class="text-xs text-(--text-muted)">
                  <span>{{
                    entry.kind === 'BASE_URL'
                      ? t('adminDiscovery.blocklistKindBaseUrl')
                      : t('adminDiscovery.blocklistKindPublicKey')
                  }}</span>
                  <span v-if="entry.note"> · {{ entry.note }}</span>
                </p>
              </div>
              <ErrorButton @click="removeFromBlocklist(entry)">
                {{ t('adminDiscovery.blocklistRemove') }}
              </ErrorButton>
            </li>
          </ul>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
