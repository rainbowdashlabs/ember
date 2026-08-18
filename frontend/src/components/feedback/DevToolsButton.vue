/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useSession} from '@/composables/useSession'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

const {t} = useI18n()
const {sessionInfo} = useSession()

const isDev = import.meta.env.DEV
const open = ref(false)

const accountName = computed(() => {
  const a = sessionInfo.value?.account
  if (!a) return ''
  return [a.firstName, a.lastName].filter(Boolean).join(' ').trim()
})

const sortedPermissions = computed(() => [...(sessionInfo.value?.permissions ?? [])].sort())

function copyJson() {
  if (!sessionInfo.value) return
  navigator.clipboard.writeText(JSON.stringify(sessionInfo.value, null, 2)).catch(() => { /* ignore */ })
}
</script>

<template>
  <template v-if="isDev">
    <button
        type="button"
        class="fixed bottom-4 right-16 z-40 h-10 w-10 rounded-full bg-secondary text-secondary-text shadow-lg hover:bg-secondary/80 transition-colors flex items-center justify-center"
        :title="t('devTools.button')"
        @click="open = !open"
    >
      <font-awesome-icon :icon="['fas', open ? 'xmark' : 'code']" class="h-4 w-4"/>
    </button>

    <div
        v-if="open"
        class="fixed bottom-16 right-4 z-40 w-80 max-h-[70vh] overflow-y-auto rounded-lg border border-(--border) bg-(--bg) shadow-xl p-4 text-sm space-y-4"
    >
      <div class="flex items-center justify-between">
        <div class="font-semibold">{{ t('devTools.title') }}</div>
        <MutedIconButton
            :icon="['fas', 'copy']"
            :label="t('devTools.copyJson')"
            hover="text"
            @click="copyJson"
        />
      </div>

      <div v-if="!sessionInfo" class="text-(--text-muted)">{{ t('devTools.noSession') }}</div>

      <template v-else>
        <section class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.account') }}</div>
          <div class="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1">
            <span class="text-(--text-muted)">{{ t('devTools.name') }}</span>
            <span>{{ accountName || '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.email') }}</span>
            <span class="break-all">{{ sessionInfo.account?.email || '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.accountId') }}</span>
            <span>{{ sessionInfo.account?.id ?? '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.instanceUserType') }}</span>
            <span>{{ sessionInfo.instanceUserType || '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.profileComplete') }}</span>
            <span>{{ sessionInfo.profileComplete ? '✓' : '✗' }}</span>
          </div>
        </section>

        <section class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.station') }}</div>
          <div class="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1">
            <span class="text-(--text-muted)">{{ t('devTools.stationId') }}</span>
            <span class="break-all">{{ sessionInfo.stationId || '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.memberId') }}</span>
            <span>{{ sessionInfo.member?.id ?? '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.memberUid') }}</span>
            <span class="break-all">{{ sessionInfo.member?.uid || '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.userType') }}</span>
            <span>{{ sessionInfo.userType || '-' }}</span>
            <span class="text-(--text-muted)">{{ t('devTools.publicKbMode') }}</span>
            <span>{{ sessionInfo.publicKbMode || '-' }}</span>
          </div>
        </section>

        <section class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.permissions') }} ({{ sortedPermissions.length }})</div>
          <div v-if="sortedPermissions.length === 0" class="text-(--text-muted)">-</div>
          <div v-else class="flex flex-wrap gap-1">
            <span
                v-for="p in sortedPermissions"
                :key="p"
                class="px-2 py-0.5 rounded bg-bg-light-accent dark:bg-bg-dark-accent text-xs font-mono"
            >{{ p }}</span>
          </div>
        </section>

        <section class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.groups') }} ({{ sessionInfo.groups?.length ?? 0 }})</div>
          <div v-if="!sessionInfo.groups?.length" class="text-(--text-muted)">-</div>
          <ul v-else class="space-y-0.5">
            <li v-for="g in sessionInfo.groups" :key="g.id" class="font-mono text-xs">
              #{{ g.id }} {{ g.name || '-' }}
            </li>
          </ul>
        </section>

        <section class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.tags') }} ({{ sessionInfo.tags?.length ?? 0 }})</div>
          <div v-if="!sessionInfo.tags?.length" class="text-(--text-muted)">-</div>
          <ul v-else class="space-y-0.5">
            <li v-for="tag in sessionInfo.tags" :key="tag.id" class="font-mono text-xs">
              #{{ tag.id }} {{ tag.name }}
            </li>
          </ul>
        </section>

        <section v-if="sessionInfo.managedMembers?.length" class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.managedMembers') }} ({{ sessionInfo.managedMembers.length }})</div>
          <ul class="space-y-0.5">
            <li v-for="m in sessionInfo.managedMembers" :key="m.id" class="font-mono text-xs">
              #{{ m.id }} {{ m.name || m.email || '-' }}
            </li>
          </ul>
        </section>

        <section v-if="sessionInfo.disabledModules?.length" class="space-y-1">
          <div class="font-semibold text-xs uppercase tracking-wide text-(--text-muted)">{{ t('devTools.disabledModules') }}</div>
          <div class="flex flex-wrap gap-1">
            <span
                v-for="m in sessionInfo.disabledModules"
                :key="m"
                class="px-2 py-0.5 rounded bg-bg-light-accent dark:bg-bg-dark-accent text-xs font-mono"
            >{{ m }}</span>
          </div>
        </section>
      </template>
    </div>
  </template>
</template>
