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
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {stationManage} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded} = useSession()

const loading = ref(true)
const error = ref('')
const saving = ref(false)
const saved = ref(false)
const initialized = ref(false)

const discoveryVisibility = ref('NONE')
const discoveryDescription = ref('')
const publicKbMode = ref('OFF')
const publicCalendarEnabled = ref(false)
const publicPagesEnabled = ref(false)
const publicWaitlistEnabled = ref(false)
const publicSlug = ref('')
const stationId = ref('')
const stationName = ref('')

const publicKbEnabled = computed(() => publicKbMode.value !== 'OFF')
const stationIdentifier = computed(() => publicSlug.value || stationId.value)
const publicKbUrl = computed(() => {
  if (!stationIdentifier.value) return ''
  return `${window.location.origin}/public/station/${stationIdentifier.value}/knowledge`
})
const publicCalendarUrl = computed(() => {
  if (!stationIdentifier.value) return ''
  return `${window.location.origin}/public/station/${stationIdentifier.value}/calendar`
})
const publicPagesUrl = computed(() => {
  if (!stationIdentifier.value) return ''
  return `${window.location.origin}/public/station/${stationIdentifier.value}/page`
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
    publicPagesEnabled.value = info.publicPagesEnabled ?? false
    publicWaitlistEnabled.value = info.publicWaitlistEnabled ?? false
    publicSlug.value = info.publicSlug ?? ''
    // Mark initialized after all values are set so watchers don't trigger on load
    setTimeout(() => { initialized.value = true }, 50)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

let saveTimer: ReturnType<typeof setTimeout> | null = null

async function save() {
  saving.value = true
  saved.value = false
  error.value = ''
  try {
    await stationManage.updateStationName({
      name: stationName.value,
      discoveryVisibility: discoveryVisibility.value,
      discoveryDescription: discoveryDescription.value || null,
      publicKbMode: publicKbMode.value,
      publicCalendarEnabled: publicCalendarEnabled.value,
      publicPagesEnabled: publicPagesEnabled.value,
      publicWaitlistEnabled: publicWaitlistEnabled.value,
      publicSlug: publicSlug.value || null,
    })
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } catch (e: any) {
    const msg = e?.response?.data?.message
    if (msg === 'Slug is already in use') {
      error.value = t('stationManage.publicSlug.taken')
    } else {
      error.value = t('common.error')
    }
  } finally {
    saving.value = false
  }
}

function debouncedSave() {
  if (!initialized.value) return
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(save, 600)
}

// Watch all form fields for changes
watch(
    [discoveryVisibility, discoveryDescription, publicKbMode, publicCalendarEnabled, publicPagesEnabled, publicWaitlistEnabled, publicSlug],
    debouncedSave,
)

onMounted(() => { if (loaded.value) loadSettings() })
watch(loaded, (v) => { if (v) loadSettings() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('discovery.settings.title') }}</SectionHeader>
      <Transition name="fade">
        <MutedText v-if="saving" size="sm" class="flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'spinner']" spin class="h-3 w-3"/>
          {{ t('common.saving') }}
        </MutedText>
        <MutedText v-else-if="saved" size="sm" class="flex items-center gap-1 text-success">
          <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3"/>
          {{ t('common.saved') }}
        </MutedText>
      </Transition>
    </div>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>

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

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('stationManage.publicPages.title') }}</SubHeader>
          <MutedText size="sm">{{ t('stationManage.publicPages.hint') }}</MutedText>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('stationManage.publicPages.enabled') }}</span>
            <ToggleInput v-model="publicPagesEnabled"/>
          </div>
          <div v-if="publicPagesEnabled" class="space-y-1">
            <FieldLabel>{{ t('stationManage.publicPages.publicUrl') }}</FieldLabel>
            <code class="block rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">{{ publicPagesUrl }}</code>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('helpCenter.federation.publicWaitlistTitle') }}</SubHeader>
          <MutedText size="sm">{{ t('helpCenter.federation.publicWaitlistText') }}</MutedText>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('waitingList.isPublic') }}</span>
            <ToggleInput v-model="publicWaitlistEnabled"/>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('stationManage.publicSlug.title') }}</SubHeader>
          <MutedText size="sm">{{ t('stationManage.publicSlug.hint') }}</MutedText>
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.publicSlug.slug') }}</FieldLabel>
            <TextInput v-model="publicSlug" :placeholder="t('stationManage.publicSlug.placeholder')"/>
            <MutedText v-if="publicSlug" size="sm">
              {{ `${window.location.origin}/public/station/${publicSlug}` }}
            </MutedText>
          </div>
        </NeutralContainer>
      </div>
    </template>
  </ViewContent>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
