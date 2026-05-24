/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import {problems} from '@/api'
import type {ProblemEntry} from '@/api/problems'

const {t} = useI18n()

const entries = ref<ProblemEntry[]>([])
const loading = ref(true)
const error = ref('')
const showAcknowledged = ref(false)
const expandedId = ref<number | null>(null)

const errorCount = computed(() => entries.value.filter(e => e.level === 'ERROR' && !e.acknowledged).length)
const warnCount = computed(() => entries.value.filter(e => e.level === 'WARN' && !e.acknowledged).length)

async function loadData() {
    loading.value = true
    try {
        entries.value = await problems.listProblems(showAcknowledged.value)
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

async function ack(id: number) {
    await problems.acknowledge(id)
    await loadData()
}

async function ackAll() {
    await problems.acknowledgeAll()
    await loadData()
}

function toggleExpand(id: number) {
    expandedId.value = expandedId.value === id ? null : id
}

function shortLogger(logger: string): string {
    const parts = logger.split('.')
    return parts[parts.length - 1]
}

async function copyStacktrace(text: string) {
    await navigator.clipboard.writeText(text)
}

function formatTime(iso: string): string {
    return new Date(iso).toLocaleString('de-DE')
}

onMounted(loadData)
</script>

<template>
    <ViewContent>
        <div class="flex items-center justify-between mb-4">
            <PageHeader class="!mb-0">
                <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-2"/>
                {{ t('adminProblems.title') }}
            </PageHeader>
            <div class="flex items-center gap-2">
                <SelectionToggleButton :selected="showAcknowledged" @toggle="showAcknowledged = !showAcknowledged; loadData()">
                    {{ t('adminProblems.showAcknowledged') }}
                </SelectionToggleButton>
                <SecondaryButton v-if="entries.some(e => !e.acknowledged)" @click="ackAll">
                    <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1"/>
                    {{ t('adminProblems.acknowledgeAll') }}
                </SecondaryButton>
            </div>
        </div>

        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

        <!-- Summary -->
        <div class="flex gap-3 mb-4">
            <ErrorContainer v-if="errorCount > 0" class="flex items-center gap-2 !py-2 !px-3">
                <font-awesome-icon :icon="['fas', 'circle-xmark']"/>
                <span class="font-semibold">{{ errorCount }}</span> {{ t('adminProblems.errors') }}
            </ErrorContainer>
            <InfoContainer v-if="warnCount > 0" class="flex items-center gap-2 !py-2 !px-3">
                <font-awesome-icon :icon="['fas', 'triangle-exclamation']"/>
                <span class="font-semibold">{{ warnCount }}</span> {{ t('adminProblems.warnings') }}
            </InfoContainer>
            <NeutralContainer v-if="errorCount === 0 && warnCount === 0" class="flex items-center gap-2 !py-2 !px-3">
                <font-awesome-icon :icon="['fas', 'circle-check']" class="text-[var(--success)]"/>
                {{ t('adminProblems.noProblems') }}
            </NeutralContainer>
        </div>

        <Spinner v-if="loading"/>

        <div v-else class="space-y-2">
            <component
                :is="entry.level === 'ERROR' ? ErrorContainer : InfoContainer"
                v-for="entry in entries"
                :key="entry.id"
                class="cursor-pointer transition-all"
                :class="{'opacity-50': entry.acknowledged}"
                @click="toggleExpand(entry.id)"
            >
                <div class="flex items-start justify-between gap-3">
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 mb-1">
                            <span class="text-xs font-mono px-1.5 py-0.5 rounded"
                                  :class="entry.level === 'ERROR' ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400' : 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'">
                                {{ entry.level }}
                            </span>
                            <span class="text-xs font-mono text-[var(--text-muted)]">{{ shortLogger(entry.logger) }}</span>
                            <span v-if="entry.count > 1" class="text-xs font-semibold px-1.5 py-0.5 rounded-full bg-[var(--bg-accent)]">
                                {{ entry.count }}x
                            </span>
                        </div>
                        <p class="text-sm font-medium truncate">
                            {{ entry.exceptionClass ? `${entry.exceptionClass}: ${entry.exceptionMessage}` : entry.distinctMessages[0] }}
                        </p>
                        <p class="text-xs text-[var(--text-muted)]">
                            {{ formatTime(entry.firstOccurrence) }}
                            <template v-if="entry.count > 1"> — {{ formatTime(entry.lastOccurrence) }}</template>
                        </p>
                    </div>
                    <div class="flex items-center gap-1 shrink-0">
                        <IconButton
                            v-if="!entry.acknowledged"
                            :icon="['fas', 'check']"
                            :label="t('adminProblems.acknowledge')"
                            @click.stop="ack(entry.id)"
                        />
                        <font-awesome-icon
                            :icon="['fas', expandedId === entry.id ? 'chevron-up' : 'chevron-down']"
                            class="text-xs text-[var(--text-muted)]"
                        />
                    </div>
                </div>

                <!-- Expanded details -->
                <div v-if="expandedId === entry.id" class="mt-3 pt-3 border-t border-[var(--border)]">
                    <!-- Stacktrace -->
                    <div v-if="entry.stacktrace" class="mb-3">
                        <div class="flex items-center gap-2 mb-1">
                            <SectionHeader class="!text-xs !mb-0">Stacktrace</SectionHeader>
                            <IconButton
                                :icon="['fas', 'copy']"
                                :label="t('adminProblems.copyStacktrace')"
                                class="text-[var(--text-muted)] hover:text-primary"
                                @click.stop="copyStacktrace(entry.stacktrace)"
                            />
                        </div>
                        <pre class="text-xs font-mono bg-[var(--bg)] rounded p-2 overflow-x-auto max-h-64 whitespace-pre-wrap">{{ entry.stacktrace }}</pre>
                    </div>

                    <!-- Distinct messages (if more than 1) -->
                    <div v-if="entry.distinctMessages.length > 1">
                        <SectionHeader class="!text-xs !mb-1">{{ t('adminProblems.messages') }} ({{ entry.distinctMessages.length }})</SectionHeader>
                        <ul class="text-xs space-y-1">
                            <li v-for="(msg, idx) in entry.distinctMessages" :key="idx" class="font-mono bg-[var(--bg)] rounded px-2 py-1">
                                {{ msg }}
                            </li>
                        </ul>
                    </div>
                </div>
            </component>
        </div>
    </ViewContent>
</template>
