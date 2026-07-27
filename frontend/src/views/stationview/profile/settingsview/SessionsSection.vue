/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {ActiveSession} from '@/api/types'
import {formatDateTime} from '@/util/format'

defineProps<{
  sessions: ActiveSession[]
}>()

const emit = defineEmits<{
  invalidate: [id: number]
  invalidateAll: []
}>()

const {t} = useI18n()

function parseOS(ua?: string): string {
  if (!ua) return 'unknown'
  if (ua.includes('Windows')) return 'windows'
  if (ua.includes('Mac OS') || ua.includes('Macintosh')) return 'mac'
  if (ua.includes('Android')) return 'android'
  if (ua.includes('iPhone') || ua.includes('iPad') || ua.includes('iOS')) return 'ios'
  if (ua.includes('Linux')) return 'linux'
  return 'unknown'
}

function parseBrowser(ua?: string): string {
  if (!ua) return 'unknown'
  if (ua.includes('Firefox')) return 'firefox'
  if (ua.includes('Edg/') || ua.includes('Edge')) return 'edge'
  if (ua.includes('OPR') || ua.includes('Opera')) return 'opera'
  if (ua.includes('Chrome') && !ua.includes('Edg')) return 'chrome'
  if (ua.includes('Safari') && !ua.includes('Chrome')) return 'safari'
  return 'unknown'
}

function osLabel(ua?: string): string {
  const os = parseOS(ua)
  const labels: Record<string, string> = {
    windows: 'Windows', mac: 'macOS', android: 'Android',
    ios: 'iOS', linux: 'Linux', unknown: t('userSettings.unknownDevice'),
  }
  return labels[os] ?? os
}

function browserLabel(ua?: string): string {
  const browser = parseBrowser(ua)
  const labels: Record<string, string> = {
    firefox: 'Firefox', chrome: 'Chrome', safari: 'Safari',
    edge: 'Edge', opera: 'Opera', unknown: t('userSettings.unknownBrowser'),
  }
  return labels[browser] ?? browser
}

function osIcon(ua?: string): string[] {
  const os = parseOS(ua)
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

function browserIcon(ua?: string): string[] {
  const browser = parseBrowser(ua)
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
            <font-awesome-icon :icon="osIcon(sess.userAgent)" class="h-5 w-5 text-(--text-muted)"/>
            <font-awesome-icon :icon="browserIcon(sess.userAgent)" class="h-5 w-5 text-(--text-muted)"/>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium">{{ browserLabel(sess.userAgent) }}</span>
              <span class="text-xs text-(--text-muted)">{{ osLabel(sess.userAgent) }}</span>
              <span v-if="sess.isCurrent" class="text-xs font-semibold text-primary">{{ t('userSettings.currentSession') }}</span>
            </div>
            <p class="text-xs text-(--text-muted)">
              {{ t('userSettings.lastActive') }}: {{ timeAgo(sess.lastUsedAt) }}
              <span class="ml-2">{{ t('userSettings.created') }}: {{ sess.createdAt ? formatDateTime(sess.createdAt) : '–' }}</span>
              <span v-if="sess.location" class="ml-2">
                <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-0.5"/>{{ sess.location }}
              </span>
              <span v-else class="ml-2">
                <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-0.5"/>{{ t('userSettings.unknownLocation') }}
              </span>
            </p>
          </div>
        </div>
        <DeleteButton v-if="!sess.isCurrent" @click="emit('invalidate', sess.id)"/>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
