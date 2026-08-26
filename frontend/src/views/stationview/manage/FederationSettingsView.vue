/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import DiscoveryPanel from './federationsettingsview/DiscoveryPanel.vue'
import PublicKbPanel from '@/components/knowledge/PublicKbPanel.vue'
import PublicCalendarPanel from './federationsettingsview/PublicCalendarPanel.vue'
import PublicPagesPanel from './federationsettingsview/PublicPagesPanel.vue'
import PublicWaitlistPanel from './federationsettingsview/PublicWaitlistPanel.vue'
import PublicBlogPanel from './federationsettingsview/PublicBlogPanel.vue'
import PublicSlugPanel from './federationsettingsview/PublicSlugPanel.vue'
import {stationManage} from '@/api'
import {useSession} from '@/composables/useSession'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {apiErrorMessage} from '@/util/apiError'

const {t} = useI18n()
const {loaded} = useSession()

const loading = ref(true)
const error = ref('')
const saving = ref(false)
const {message: savedMessage, flash: flashSaved} = useFlashMessage(2000)
const initialized = ref(false)

const discoveryVisibility = ref('NONE')
const discoveryDescription = ref('')
const publicKbMode = ref('OFF')
const publicCalendarEnabled = ref(false)
const publicPagesEnabled = ref(false)
const publicWaitlistEnabled = ref(false)
const publicBlogEnabled = ref(false)
const publicSlug = ref('')
const stationId = ref('')
const stationName = ref('')

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
    publicBlogEnabled.value = info.publicBlogEnabled ?? false
    publicSlug.value = info.publicSlug ?? ''
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
      publicBlogEnabled: publicBlogEnabled.value,
      publicSlug: publicSlug.value || null,
    })
    flashSaved(t('common.saved'))
  } catch (e) {
    const msg = apiErrorMessage(e)
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

watch(
    [discoveryVisibility, discoveryDescription, publicKbMode, publicCalendarEnabled, publicPagesEnabled, publicWaitlistEnabled, publicBlogEnabled, publicSlug],
    debouncedSave,
)

onMounted(() => { if (loaded.value) loadSettings() })
watch(loaded, (v) => { if (v) loadSettings() })
</script>

<template>
  <ViewContent
      :title="t('pages.station-federation-settings.title')"
      :subtitle="t('pages.station-federation-settings.subtitle')"
  >
    <div class="flex items-center justify-end mb-4">
      <Transition name="fade">
        <MutedText v-if="saving" size="sm" class="flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'spinner']" spin class="h-3 w-3"/>
          {{ t('common.saving') }}
        </MutedText>
        <MutedText v-else-if="savedMessage" size="sm" class="flex items-center gap-1 text-success">
          <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3"/>
          {{ savedMessage }}
        </MutedText>
      </Transition>
    </div>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>

    <Spinner v-if="loading" />

    <template v-if="!loading">
      <div class="space-y-4 max-w-xl">
        <DiscoveryPanel data-onboarding="federation.visibility" v-model:visibility="discoveryVisibility" v-model:description="discoveryDescription"/>
        <PublicKbPanel v-model:mode="publicKbMode" :public-url="publicKbUrl"/>
        <PublicCalendarPanel v-model:enabled="publicCalendarEnabled" :public-url="publicCalendarUrl"/>
        <PublicPagesPanel v-model:enabled="publicPagesEnabled" :public-url="publicPagesUrl"/>
        <PublicWaitlistPanel v-model:enabled="publicWaitlistEnabled"/>
        <PublicBlogPanel v-model:enabled="publicBlogEnabled"/>
        <PublicSlugPanel v-model:slug="publicSlug"/>
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
