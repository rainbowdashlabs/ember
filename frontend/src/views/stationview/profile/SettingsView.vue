/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { userSettings } from '@/api'

const { t } = useI18n()

const notifyNews = ref(true)
const notifyNewEvents = ref(true)
const notifyEventStatus = ref(true)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)

async function loadData() {
  loading.value = true
  try {
    const settings = await userSettings.getSettings()
    notifyNews.value = settings.notifyNews
    notifyNewEvents.value = settings.notifyNewEvents
    notifyEventStatus.value = settings.notifyEventStatus
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  saved.value = false
  error.value = ''
  try {
    const result = await userSettings.updateSettings({
      notifyNews: notifyNews.value,
      notifyNewEvents: notifyNewEvents.value,
      notifyEventStatus: notifyEventStatus.value,
    })
    notifyNews.value = result.notifyNews
    notifyNewEvents.value = result.notifyNewEvents
    notifyEventStatus.value = result.notifyEventStatus
    saved.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('userSettings.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('userSettings.saved') }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <h3 class="font-semibold text-sm">{{ t('userSettings.notifications') }}</h3>
          <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <div>
                <span class="text-sm font-medium">{{ t('userSettings.notifyNews') }}</span>
                <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyNewsHint') }}</p>
              </div>
              <ToggleInput v-model="notifyNews" @update:model-value="save" />
            </div>

            <div class="flex items-center justify-between">
              <div>
                <span class="text-sm font-medium">{{ t('userSettings.notifyNewEvents') }}</span>
                <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyNewEventsHint') }}</p>
              </div>
              <ToggleInput v-model="notifyNewEvents" @update:model-value="save" />
            </div>

            <div class="flex items-center justify-between">
              <div>
                <span class="text-sm font-medium">{{ t('userSettings.notifyEventStatus') }}</span>
                <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyEventStatusHint') }}</p>
              </div>
              <ToggleInput v-model="notifyEventStatus" @update:model-value="save" />
            </div>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
