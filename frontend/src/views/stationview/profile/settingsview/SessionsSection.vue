/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AppIcon from '@/components/display/AppIcon.vue'
import {BROWSER_NAMES, browserOf, SYSTEM_NAMES, systemOf} from '@/util/userAgent'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {ActiveSession} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

defineProps<{
  sessions: ActiveSession[]
}>()

const emit = defineEmits<{
  invalidate: [id: number]
  invalidateAll: []
  /** Stops pushes to the device of a sign-in and keeps the sign-in. */
  removePush: [session: ActiveSession]
}>()

const {t} = useI18n()

function osLabel(ua: string | null): string {
  const os = systemOf(ua)
  return os === 'unknown' ? t('userSettings.unknownDevice') : SYSTEM_NAMES[os]
}

function browserLabel(ua: string | null): string {
  const browser = browserOf(ua)
  return browser === 'unknown' ? t('userSettings.unknownBrowser') : BROWSER_NAMES[browser]
}

function osIcon(ua: string | null): string[] {
  const os = systemOf(ua)
  const icons: Record<string, [string, string]> = {
    windows: ['fab', 'windows'],
    mac: ['fab', 'apple'],
    linux: ['fab', 'linux'],
    android: ['fab', 'android'],
    ios: ['fab', 'apple'],
    unknown: ['fas', 'desktop'],
  }
  return icons[os] ?? ['fas', 'desktop']
}

function browserIcon(ua: string | null): string[] {
  const browser = browserOf(ua)
  const icons: Record<string, [string, string]> = {
    chrome: ['fab', 'chrome'],
    firefox: ['fab', 'firefox-browser'],
    safari: ['fab', 'safari'],
    edge: ['fab', 'edge'],
    opera: ['fab', 'opera'],
    unknown: ['fas', 'globe'],
  }
  return icons[browser] ?? ['fas', 'globe']
}


function timeAgo(dateStr?: string): string {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return t('userSettings.justNow')
  if (minutes < 60) return t('userSettings.minutesAgo', {n: minutes})
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('userSettings.hoursAgo', {n: hours})
  const days = Math.floor(hours / 24)
  return t('userSettings.daysAgo', {n: days})
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('userSettings.sessions') }}</SubHeader>
      <ErrorButton :icon="['fas', 'trash']" @click="emit('invalidateAll')">
        {{ t('userSettings.invalidateAll') }}
      </ErrorButton>
    </div>

    <div class="space-y-2">
      <NeutralContainer v-for="sess in sessions" :key="sess.id" class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div class="flex items-center gap-2">
            <AppIcon :icon="osIcon(sess.userAgent)" class="h-5 w-5 text-(--text-muted)"/>
            <AppIcon :icon="browserIcon(sess.userAgent)" class="h-5 w-5 text-(--text-muted)"/>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium">{{ browserLabel(sess.userAgent) }}</span>
              <span class="text-xs text-(--text-muted)">{{ osLabel(sess.userAgent) }}</span>
              <span v-if="sess.isCurrent" class="text-xs font-semibold text-primary">{{ t('userSettings.currentSession') }}</span>
              <span v-if="sess.pushDeviceId != null" class="text-xs font-semibold text-(--text-muted)"
                    data-testid="session-push-badge">
                <AppIcon :icon="['fas', 'bell']" class="mr-0.5"/>{{ t('userSettings.sessionPush') }}
              </span>
            </div>
            <p class="text-xs text-(--text-muted)">
              {{ t('userSettings.lastActive') }}: {{ timeAgo(sess.lastUsedAt) }}
              <span class="ml-2">{{ t('userSettings.created') }}: {{ sess.createdAt ? formatDateTime(sess.createdAt) : '–' }}</span>
              <span v-if="sess.location" class="ml-2">
                <AppIcon :icon="['fas', 'location-dot']" class="mr-0.5"/>{{ sess.location }}
              </span>
              <span v-else class="ml-2">
                <AppIcon :icon="['fas', 'location-dot']" class="mr-0.5"/>{{ t('userSettings.unknownLocation') }}
              </span>
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton v-if="sess.pushDeviceId != null" :icon="['fas', 'bell-slash']"
                           data-testid="session-remove-push" @click="emit('removePush', sess)">
            {{ t('userSettings.sessionRemovePush') }}
          </SecondaryButton>
          <DeleteButton v-if="!sess.isCurrent" @click="emit('invalidate', sess.id)"/>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
