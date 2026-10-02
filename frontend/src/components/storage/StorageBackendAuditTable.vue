/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {AuditEntryResponse} from '@/api/generated/schema'

/** The storage history of a station, an association or the whole instance, newest first. */
defineProps<{
    entries: AuditEntryResponse[]
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="!entries.length" class="text-sm">
        <MutedText tag="p" size="sm">{{ t('storageBackend.audit.empty') }}</MutedText>
    </div>
    <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
            <thead>
                <tr class="border-b border-[var(--border)]">
                    <th class="text-left p-2">{{ t('storageBackend.audit.timestamp') }}</th>
                    <th class="text-left p-2">{{ t('storageBackend.audit.action') }}</th>
                    <th class="text-left p-2">{{ t('storageBackend.audit.outcome') }}</th>
                    <th class="text-left p-2">{{ t('storageBackend.audit.detail') }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="entry in entries" :key="entry.id" class="border-b border-[var(--border)]"
                    data-testid="storage-audit-row">
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
