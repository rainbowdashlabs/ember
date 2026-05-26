/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {feedToken} from '@/api'
import type {FeedTokenResponse} from '@/api/feedToken'
import {buildFeedUrl} from '@/api/feedToken'

const {t} = useI18n()

const token = ref<FeedTokenResponse | null>(null)
const loading = ref(true)
const error = ref('')
const copied = ref('')

async function loadToken() {
  loading.value = true
  try {
    token.value = await feedToken.getFeedToken()
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function createToken() {
  error.value = ''
  try {
    token.value = await feedToken.createFeedToken()
  } catch { error.value = t('common.error') }
}

async function regenerateToken() {
  error.value = ''
  try {
    token.value = await feedToken.regenerateFeedToken()
  } catch { error.value = t('common.error') }
}

async function revokeToken() {
  error.value = ''
  try {
    await feedToken.revokeFeedToken()
    token.value = null
  } catch { error.value = t('common.error') }
}

async function copyUrl(url: string) {
  await navigator.clipboard.writeText(url)
  copied.value = url
  setTimeout(() => { copied.value = '' }, 2000)
}

onMounted(loadToken)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('userSettings.feedTitle') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('userSettings.feedHint') }}</p>

    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading">
      <template v-if="token">
        <div class="space-y-3">
          <div v-for="feed in [
            {label: t('userSettings.feedIcal'), type: 'ical' as const, icon: ['fas', 'calendar-days']},
            {label: t('userSettings.feedRss'), type: 'rss' as const, icon: ['fas', 'rss']},
            {label: t('userSettings.feedAtom'), type: 'atom' as const, icon: ['fas', 'rss']},
          ]" :key="feed.type" class="space-y-1">
            <FieldLabel>{{ feed.label }}</FieldLabel>
            <div class="flex items-center gap-2">
              <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-xs break-all select-all">{{ buildFeedUrl(token.token, feed.type) }}</code>
              <SecondaryButton @click="copyUrl(buildFeedUrl(token.token, feed.type))">
                <font-awesome-icon :icon="copied === buildFeedUrl(token.token, feed.type) ? ['fas', 'check'] : ['fas', 'copy']"/>
              </SecondaryButton>
            </div>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <SecondaryButton :icon="['fas', 'rotate']" @click="regenerateToken">{{ t('userSettings.feedRegenerate') }}</SecondaryButton>
          <DeleteButton @click="revokeToken"/>
        </div>
      </template>

      <template v-else>
        <PrimaryButton :icon="['fas', 'rss']" @click="createToken">{{ t('userSettings.feedCreate') }}</PrimaryButton>
      </template>
    </template>
  </NeutralContainer>
</template>
