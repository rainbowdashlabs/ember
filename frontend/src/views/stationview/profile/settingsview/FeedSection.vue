/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import {feedToken} from '@/api'
import {buildFeedUrl, type FeedPreset, type FeedTokenResponse} from '@/api/feedToken'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useFlashMessage} from '@/composables/useFlashMessage'
import FeedCard from './feedsection/FeedCard.vue'
import RssFallback from './feedsection/RssFallback.vue'

const {t} = useI18n()

const {config: token, loading, error, runWith} = useConfigPanel<FeedTokenResponse | null>({
  initial: null,
  fetch: async () => {
    try { return await feedToken.getFeedToken() } catch { return null }
  },
})

const {message: copied, flash} = useFlashMessage(2000)
const regenerateConfirmOpen = ref(false)
const revokeConfirmOpen = ref(false)

const PRESET_STORAGE_KEY = 'ember.feedPreset'
const validPresets: FeedPreset[] = ['rich', 'compact', 'minimal']
function loadStoredPreset(): FeedPreset {
  if (typeof window === 'undefined') return 'rich'
  const stored = window.localStorage.getItem(PRESET_STORAGE_KEY)
  return (validPresets as string[]).includes(stored ?? '') ? (stored as FeedPreset) : 'rich'
}
const preset = ref<FeedPreset>(loadStoredPreset())
function setPreset(p: FeedPreset) {
  preset.value = p
  if (typeof window !== 'undefined') window.localStorage.setItem(PRESET_STORAGE_KEY, p)
}

async function createToken() {
  await runWith(() => feedToken.createFeedToken())
}

async function confirmRegenerate() {
  regenerateConfirmOpen.value = false
  await runWith(() => feedToken.regenerateFeedToken())
}

async function confirmRevoke() {
  revokeConfirmOpen.value = false
  await runWith(async () => {
    await feedToken.revokeFeedToken()
    return null
  })
}

async function copyUrl(url: string) {
  await navigator.clipboard.writeText(url)
  flash(url)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('userSettings.feedTitle') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('userSettings.feedHint') }}</p>

    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading">
      <template v-if="token">
        <div class="space-y-1">
          <FieldLabel>{{ t('userSettings.feedPresetLabel') }}</FieldLabel>
          <div class="flex flex-wrap items-center gap-2">
            <SelectionToggleButton :selected="preset === 'rich'" size="md" @toggle="setPreset('rich')">
              {{ t('userSettings.feedPresetRich') }}
            </SelectionToggleButton>
            <SelectionToggleButton :selected="preset === 'compact'" size="md" @toggle="setPreset('compact')">
              {{ t('userSettings.feedPresetCompact') }}
            </SelectionToggleButton>
            <SelectionToggleButton :selected="preset === 'minimal'" size="md" @toggle="setPreset('minimal')">
              {{ t('userSettings.feedPresetMinimal') }}
            </SelectionToggleButton>
          </div>
          <MutedText tag="div" size="sm">{{ t('userSettings.feedPresetHint.' + preset) }}</MutedText>
        </div>

        <div class="space-y-3">
          <FeedCard
            :icon="['fas', 'calendar-days']"
            :title="t('userSettings.feedIcal')"
            help-route-name="help-profile-ical-feed"
            :hint="t('userSettings.feedIcalHint')"
            :url="buildFeedUrl(token.token, 'ical', preset)"
            :copied="copied"
            @copy="copyUrl"
          />
          <FeedCard
            :icon="['fas', 'rss']"
            :title="t('userSettings.feedAtom')"
            help-route-name="help-profile-rss-feed"
            :hint="t('userSettings.feedAtomHint')"
            :url="buildFeedUrl(token.token, 'atom', preset)"
            :copied="copied"
            recommended
            :recommended-label="t('userSettings.feedAtomRecommended')"
            @copy="copyUrl"
          />
          <RssFallback
            :label="t('userSettings.feedRssFallback')"
            :hint="t('userSettings.feedRssHint')"
            :url="buildFeedUrl(token.token, 'rss', preset)"
            :copied="copied"
            @copy="copyUrl"
          />
        </div>

        <div class="flex items-center gap-2">
          <SecondaryButton :icon="['fas', 'rotate']" @click="regenerateConfirmOpen = true">{{ t('userSettings.feedRegenerate') }}</SecondaryButton>
          <DeleteButton @click="revokeConfirmOpen = true"/>
        </div>
      </template>

      <template v-else>
        <PrimaryButton :icon="['fas', 'rss']" @click="createToken">{{ t('userSettings.feedCreate') }}</PrimaryButton>
      </template>
    </template>

    <Modal v-model="regenerateConfirmOpen">
      <div class="space-y-4">
        <SubHeader>{{ t('userSettings.feedRegenerateConfirmTitle') }}</SubHeader>
        <p class="text-sm">{{ t('userSettings.feedRegenerateConfirmBody') }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="regenerateConfirmOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :icon="['fas', 'rotate']" @click="confirmRegenerate">{{ t('userSettings.feedRegenerate') }}</ErrorButton>
        </div>
      </div>
    </Modal>

    <Modal v-model="revokeConfirmOpen">
      <div class="space-y-4">
        <SubHeader>{{ t('userSettings.feedRevokeConfirmTitle') }}</SubHeader>
        <p class="text-sm">{{ t('userSettings.feedRevokeConfirmBody') }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="revokeConfirmOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']" @click="confirmRevoke">{{ t('userSettings.feedRevokeConfirm') }}</ErrorButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>
