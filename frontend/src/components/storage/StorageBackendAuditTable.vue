/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {AuditEntry} from '@/api/storageBackend'

defineProps<{
    entries: AuditEntry[]
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="!entries.length" class="text-sm">
        <MutedText tag="p" size="sm">{{ t('stationStorageBackend.audit.empty') }}</MutedText>
    </div>
    <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
            <thead>
                <tr class="border-b border-[var(--border)]">
                    <th class="text-left p-2">{{ t('stationStorageBackend.audit.timestamp') }}</th>
                    <th class="text-left p-2">{{ t('stationStorageBackend.audit.action') }}</th>
                    <th class="text-left p-2">{{ t('stationStorageBackend.audit.outcome') }}</th>
                    <th class="text-left p-2">{{ t('stationStorageBackend.audit.detail') }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="entry in entries" :key="entry.id" class="border-b border-[var(--border)]">
                    <td class="p-2 font-mono text-xs">{{ entry.ts }}</td>
                    <td class="p-2">{{ entry.action }}</td>
                    <td class="p-2">{{ entry.outcome }}</td>
                    <td class="p-2 text-xs text-[var(--text-muted)]">
                        {{ entry.error ?? '' }}
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</template>
