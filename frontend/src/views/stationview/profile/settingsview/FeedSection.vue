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
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {feedToken} from '@/api'
import type {FeedPreset, FeedTokenResponse} from '@/api/feedToken'
import {buildFeedUrl} from '@/api/feedToken'

const {t} = useI18n()

const token = ref<FeedTokenResponse | null>(null)
const loading = ref(true)
const error = ref('')
const copied = ref('')
const regenerateConfirmOpen = ref(false)
const revokeConfirmOpen = ref(false)

// Preset selection persists across reloads via localStorage. No backend round-trip — the
// preset only affects the URL we hand to the user; the backend honours the query params
// (verbose / images) we encode in it.
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

async function confirmRegenerate() {
  regenerateConfirmOpen.value = false
  error.value = ''
  try {
    token.value = await feedToken.regenerateFeedToken()
  } catch { error.value = t('common.error') }
}

async function confirmRevoke() {
  revokeConfirmOpen.value = false
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
        <!-- Verbosity preset: rewrites the URL we hand to the user with the verbose/images
             query params the backend honours. No backend state. -->
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

        <!-- Two featured feeds, two different jobs:
             - iCal feeds the calendar (events, deadlines, location).
             - Atom feeds the notification stream (news, registrations, lending, …).
             RSS is an emergency fallback for readers that don't speak Atom — collapsed. -->
        <div class="space-y-3">
          <!-- iCal: featured (calendar integration). -->
          <div class="space-y-1 rounded-theme border border-primary/40 bg-primary/5 p-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <font-awesome-icon :icon="['fas', 'calendar-days']" class="text-primary"/>
                <FieldLabel>{{ t('userSettings.feedIcal') }}</FieldLabel>
              </div>
              <HelpCenterHint :to="{name: 'help-profile-ical-feed'}">
                {{ t('userSettings.feedHelp') }}
              </HelpCenterHint>
            </div>
            <MutedText tag="div" size="sm">{{ t('userSettings.feedIcalHint') }}</MutedText>
            <div class="flex items-center gap-2">
              <code class="flex-1 rounded bg-bg-light dark:bg-bg-dark px-3 py-2 text-xs break-all select-all">{{ buildFeedUrl(token.token, 'ical', preset) }}</code>
              <SecondaryButton @click="copyUrl(buildFeedUrl(token.token, 'ical', preset))">
                <font-awesome-icon :icon="copied === buildFeedUrl(token.token, 'ical', preset) ? ['fas', 'check'] : ['fas', 'copy']"/>
              </SecondaryButton>
            </div>
          </div>

          <!-- Atom: featured (notification stream, recommended over RSS). -->
          <div class="space-y-1 rounded-theme border border-primary/40 bg-primary/5 p-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <font-awesome-icon :icon="['fas', 'rss']" class="text-primary"/>
                <FieldLabel>{{ t('userSettings.feedAtom') }}</FieldLabel>
                <PrimaryBadge>{{ t('userSettings.feedAtomRecommended') }}</PrimaryBadge>
              </div>
              <HelpCenterHint :to="{name: 'help-profile-rss-feed'}">
                {{ t('userSettings.feedHelp') }}
              </HelpCenterHint>
            </div>
            <MutedText tag="div" size="sm">{{ t('userSettings.feedAtomHint') }}</MutedText>
            <div class="flex items-center gap-2">
              <code class="flex-1 rounded bg-bg-light dark:bg-bg-dark px-3 py-2 text-xs break-all select-all">{{ buildFeedUrl(token.token, 'atom', preset) }}</code>
              <SecondaryButton @click="copyUrl(buildFeedUrl(token.token, 'atom', preset))">
                <font-awesome-icon :icon="copied === buildFeedUrl(token.token, 'atom', preset) ? ['fas', 'check'] : ['fas', 'copy']"/>
              </SecondaryButton>
            </div>
          </div>

          <!-- RSS fallback: collapsed by default. -->
          <details class="space-y-1">
            <summary class="cursor-pointer text-sm text-(--text-muted) hover:text-(--text) transition-colors">
              {{ t('userSettings.feedRssFallback') }}
            </summary>
            <div class="space-y-1 pt-2">
              <MutedText tag="div" size="sm">{{ t('userSettings.feedRssHint') }}</MutedText>
              <div class="flex items-center gap-2">
                <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-xs break-all select-all">{{ buildFeedUrl(token.token, 'rss', preset) }}</code>
                <SecondaryButton @click="copyUrl(buildFeedUrl(token.token, 'rss', preset))">
                  <font-awesome-icon :icon="copied === buildFeedUrl(token.token, 'rss', preset) ? ['fas', 'check'] : ['fas', 'copy']"/>
                </SecondaryButton>
              </div>
            </div>
          </details>
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

    <!-- Rotating the token immediately breaks every subscribed reader (Thunderbird, Apple
         Calendar, …) — confirm before discarding the existing token. -->
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
