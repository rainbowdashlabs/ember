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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {stationManage} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded} = useSession()

const loading = ref(true)
const error = ref('')
const success = ref('')
const saving = ref(false)

const discoveryVisibility = ref('NONE')
const discoveryDescription = ref('')
const publicKbMode = ref('OFF')
const publicCalendarEnabled = ref(false)
const stationId = ref('')
const stationName = ref('')

const publicKbEnabled = computed(() => publicKbMode.value !== 'OFF')
const publicKbUrl = computed(() => {
  if (!stationId.value) return ''
  return `${window.location.origin}/public/station/${stationId.value}/knowledge`
})
const publicCalendarUrl = computed(() => {
  if (!stationId.value) return ''
  return `${window.location.origin}/public/station/${stationId.value}/calendar`
})

function togglePublicKb() {
  publicKbMode.value = publicKbEnabled.value ? 'OFF' : 'ALLOW_ALL'
}

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const info = await stationManage.getStationInfo()
    stationId.value = info.id
    stationName.value = info.name ?? ''
    discoveryVisibility.value = info.discoveryVisibility ?? 'NONE'
    discoveryDescription.value = info.discoveryDescription ?? ''
    publicKbMode.value = info.publicKbMode ?? 'OFF'
    publicCalendarEnabled.value = info.publicCalendarEnabled ?? false
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await stationManage.updateStationName({
      name: stationName.value,
      discoveryVisibility: discoveryVisibility.value,
      discoveryDescription: discoveryDescription.value || null,
      publicKbMode: publicKbMode.value,
      publicCalendarEnabled: publicCalendarEnabled.value,
    })
    success.value = t('stationManage.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

onMounted(() => { if (loaded.value) loadSettings() })
watch(loaded, (v) => { if (v) loadSettings() })
</script>

<template>
  <ViewContent>
    <SectionHeader class="mb-4">{{ t('discovery.settings.title') }}</SectionHeader>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <Spinner v-if="loading" />

    <template v-if="!loading">
      <div class="space-y-4 max-w-xl">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('discovery.settings.visibility') }}</SubHeader>
          <SelectInput v-model="discoveryVisibility" class="w-full">
            <option value="NONE">{{ t('discovery.settings.visibilityNone') }}</option>
            <option value="INSTANCE">{{ t('discovery.settings.visibilityInstance') }}</option>
            <option value="PUBLIC">{{ t('discovery.settings.visibilityPublic') }}</option>
          </SelectInput>

          <div v-if="discoveryVisibility !== 'NONE'" class="space-y-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('discovery.settings.description') }}</FieldLabel>
              <TextInput v-model="discoveryDescription" :placeholder="t('discovery.settings.descriptionPlaceholder')"/>
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
              <SelectInput v-model="publicKbMode" class="w-full">
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

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('stationManage.publicCalendar.title') }}</SubHeader>
          <MutedText size="sm">{{ t('stationManage.publicCalendar.hint') }}</MutedText>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('stationManage.publicCalendar.enabled') }}</span>
            <ToggleInput v-model="publicCalendarEnabled"/>
          </div>
          <div v-if="publicCalendarEnabled" class="space-y-1">
            <FieldLabel>{{ t('stationManage.publicCalendar.publicUrl') }}</FieldLabel>
            <code class="block rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">{{ publicCalendarUrl }}</code>
            <MutedText size="sm">{{ t('stationManage.publicCalendar.categoriesHint') }}</MutedText>
          </div>
        </NeutralContainer>

        <PrimaryButton :disabled="saving" @click="saveSettings">
          {{ saving ? t('common.loading') : t('stationManage.save') }}
        </PrimaryButton>
      </div>
    </template>
  </ViewContent>
</template>
