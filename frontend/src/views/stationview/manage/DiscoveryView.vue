/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DiscoveryGrid from '@/components/discovery/DiscoveryGrid.vue'
import {discovery, stationManage, federation} from '@/api'
import type {DiscoveryEntry} from '@/api/discovery'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded, canManageFederation} = useSession()

// -- Discovery list --
const stations = ref<DiscoveryEntry[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

// -- Settings --
const settingsLoading = ref(true)
const discoveryVisibility = ref('NONE')
const discoveryDescription = ref('')
const discoveryShowKb = ref(false)
const publicKbMode = ref('OFF')
const stationId = ref('')
const stationName = ref('')
const saving = ref(false)

const publicKbEnabled = computed(() => publicKbMode.value !== 'OFF')
const publicKbUrl = computed(() => {
  if (!stationId.value) return ''
  return `${window.location.origin}/public/kb/${stationId.value}`
})

async function loadAll() {
  loading.value = true
  settingsLoading.value = true
  error.value = ''
  try {
    const [stationsList, partners] = await Promise.all([
      discovery.listDiscoverable(),
      federation.listPartners(),
    ])
    // Override alreadyFederated using the partner list (more reliable than public endpoint detection)
    const partnerUids = new Set(partners.map(p => p.partner.partnerStationId))
    stations.value = stationsList.map(s => ({
      ...s,
      alreadyFederated: s.alreadyFederated || partnerUids.has(s.stationUid),
    }))
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
  if (canManageFederation()) {
    try {
      const info = await stationManage.getStationInfo()
      stationId.value = info.id
      stationName.value = info.name ?? ''
      discoveryVisibility.value = info.discoveryVisibility ?? 'NONE'
      discoveryDescription.value = info.discoveryDescription ?? ''
      discoveryShowKb.value = info.discoveryShowKb ?? false
      publicKbMode.value = info.publicKbMode ?? 'OFF'
    } catch { /* ignore */ }
  }
  settingsLoading.value = false
}

async function handleConnect(station: DiscoveryEntry) {
  try {
    await discovery.requestFederation(station.stationUid)
    success.value = t('discovery.requestSent')
    setTimeout(() => { success.value = '' }, 3000)
    await loadAll()
  } catch {
    error.value = t('discovery.requestError')
  }
}

async function saveSettings() {
  saving.value = true
  try {
    await stationManage.updateStationName({
      name: stationName.value,
      discoveryVisibility: discoveryVisibility.value,
      discoveryDescription: discoveryDescription.value || null,
      discoveryShowKb: discoveryShowKb.value,
      publicKbMode: publicKbMode.value,
    })
    success.value = t('stationManage.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

function togglePublicKb() {
  publicKbMode.value = publicKbEnabled.value ? 'OFF' : 'ALLOW_ALL'
}

onMounted(() => { if (loaded.value) loadAll() })
watch(loaded, (v) => { if (v) loadAll() })
</script>

<template>
  <ViewContent>
    <SectionHeader class="mb-4">{{ t('discovery.title') }}</SectionHeader>
    <MutedText tag="p" class="mb-6">{{ t('discovery.subtitle') }}</MutedText>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <!-- Station list -->
    <Spinner v-if="loading"/>
    <EmptyState v-else-if="stations.length === 0">{{ t('discovery.empty') }}</EmptyState>
    <DiscoveryGrid v-else :stations="stations" :can-connect="canManageFederation()" :show-invite="false" class="mb-8" @connect="handleConnect"/>

    <!-- Settings (managers only) -->
    <template v-if="canManageFederation() && !settingsLoading">
      <SectionHeader class="mb-4">{{ t('discovery.settings.title') }}</SectionHeader>

      <div class="space-y-4 max-w-xl">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('discovery.settings.visibility') }}</SubHeader>
          <SelectInput v-model="discoveryVisibility">
            <option value="NONE">{{ t('discovery.settings.visibilityNone') }}</option>
            <option value="INSTANCE">{{ t('discovery.settings.visibilityInstance') }}</option>
            <option value="PUBLIC">{{ t('discovery.settings.visibilityPublic') }}</option>
          </SelectInput>

          <div v-if="discoveryVisibility !== 'NONE'" class="space-y-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('discovery.settings.description') }}</FieldLabel>
              <TextInput v-model="discoveryDescription" :placeholder="t('discovery.settings.descriptionPlaceholder')"/>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium">{{ t('discovery.settings.showKb') }}</span>
              <ToggleInput v-model="discoveryShowKb"/>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('stationManage.publicKb.title') }}</SubHeader>
          <MutedText size="sm">{{ t('stationManage.publicKb.hint') }}</MutedText>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('stationManage.publicKb.enabled') }}</span>
            <ToggleInput :model-value="publicKbEnabled" @update:model-value="togglePublicKb"/>
          </div>
          <div v-if="publicKbEnabled" class="space-y-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('stationManage.publicKb.mode') }}</FieldLabel>
              <SelectInput v-model="publicKbMode">
                <option value="ALLOW_ALL">{{ t('stationManage.publicKb.modeAllowAll') }}</option>
                <option value="DENY_ALL">{{ t('stationManage.publicKb.modeDenyAll') }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('stationManage.publicKb.publicUrl') }}</FieldLabel>
              <code class="block rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">{{ publicKbUrl }}</code>
            </div>
          </div>
        </NeutralContainer>

        <PrimaryButton :disabled="saving" @click="saveSettings">
          {{ saving ? t('common.loading') : t('stationManage.save') }}
        </PrimaryButton>
      </div>
    </template>
  </ViewContent>
</template>
