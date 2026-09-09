/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ProblemEntryCard from './adminproblemsview/ProblemEntryCard.vue'
import {beacon, problems} from '@/api'
import BeaconPreviewModal from './adminproblemsview/BeaconPreviewModal.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type {BeaconStatus} from '@/api/beacon'
import type {ProblemEntry} from '@/api/problems'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const showAcknowledged = ref(false)
const levelFilter = ref<string | null>(null)
const expandedId = ref<number | null>(null)

const {config: entries, loading, error, reload: loadData} = useConfigPanel<ProblemEntry[]>({
    initial: [],
    fetch: () => problems.listProblems(showAcknowledged.value),
})

const errorCount = computed(() => entries.value.filter(e => e.level === 'ERROR' && !e.acknowledged).length)
const warnCount = computed(() => entries.value.filter(e => e.level === 'WARN' && !e.acknowledged).length)

const visibleEntries = computed(() =>
    levelFilter.value == null ? entries.value : entries.value.filter(e => e.level === levelFilter.value))

function toggleLevelFilter(level: string) {
    levelFilter.value = levelFilter.value === level ? null : level
}

async function ack(id: number) {
    await problems.acknowledge(id)
    if (showAcknowledged.value) {
        const entry = entries.value.find(e => e.id === id)
        if (entry) entry.acknowledged = true
    } else {
        entries.value = entries.value.filter(e => e.id !== id)
    }
}

async function ackAll() {
    await problems.acknowledgeAll()
    if (showAcknowledged.value) {
        for (const entry of entries.value) entry.acknowledged = true
    } else {
        entries.value = []
    }
}

function toggleExpand(id: number) {
    expandedId.value = expandedId.value === id ? null : id
}

/**
 * The beacon half of this screen, which only appears where an instance actually reports to one.
 *
 * <p>Whether it does is configuration rather than a stored setting, so this asks and shows; it never
 * offers a switch that would not survive a restart.
 */
const beaconStatus = ref<BeaconStatus | null>(null)
const selected = ref<Set<number>>(new Set())
const previewId = ref<number | null>(null)
const showPreview = ref(false)
const sendResult = ref('')

onMounted(async () => {
    try {
        beaconStatus.value = await beacon.getStatus()
    } catch {
        beaconStatus.value = null
    }
})

function toggleSelected(id: number) {
    const next = new Set(selected.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    selected.value = next
}

function openPreview(id: number) {
    previewId.value = id
    showPreview.value = true
}

async function sendSelected() {
    if (selected.value.size === 0) return
    const queued = await beacon.sendProblems([...selected.value])
    sendResult.value = t('beacon.queued', {count: queued})
    selected.value = new Set()
}
</script>

<template>
    <ViewContent :title="t('pages.admin-problems.title')" :subtitle="t('pages.admin-problems.subtitle')">
        <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-2">
                <SelectionToggleButton :selected="showAcknowledged" @toggle="showAcknowledged = !showAcknowledged; loadData()">
                    {{ t('adminProblems.showAcknowledged') }}
                </SelectionToggleButton>
                <SelectionToggleButton :selected="levelFilter === 'ERROR'" @toggle="toggleLevelFilter('ERROR')">
                    {{ t('adminProblems.errors') }}
                </SelectionToggleButton>
                <SelectionToggleButton :selected="levelFilter === 'WARN'" @toggle="toggleLevelFilter('WARN')">
                    {{ t('adminProblems.warnings') }}
                </SelectionToggleButton>
                <SecondaryButton :icon="['fas', 'check-double']" v-if="entries.some(e => !e.acknowledged)" @click="ackAll">
                    {{ t('adminProblems.acknowledgeAll') }}
                </SecondaryButton>
                <SecondaryButton
                    v-if="beaconStatus?.enabled && selected.size > 0"
                    :icon="['fas', 'tower-broadcast']"
                    data-testid="beacon-send-selected"
                    @click="sendSelected"
                >
                    {{ t('beacon.sendSelected', {count: selected.size}) }}
                </SecondaryButton>
            </div>
        </div>

        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Alert v-if="sendResult" variant="success" class="mb-4">{{ sendResult }}</Alert>

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
            <div v-for="entry in visibleEntries" :key="entry.id" class="flex items-start gap-2">
                <CheckboxInput
                    v-if="beaconStatus?.enabled"
                    :model-value="selected.has(entry.id)"
                    class="mt-4"
                    :data-testid="`beacon-pick-${entry.id}`"
                    @update:model-value="toggleSelected(entry.id)"
                />
                <ProblemEntryCard
                    class="flex-1"
                    :entry="entry"
                    :expanded="expandedId === entry.id"
                    :can-send="beaconStatus?.enabled === true"
                    @toggle="toggleExpand"
                    @ack="ack"
                    @send="openPreview"
                />
            </div>
        </div>

        <BeaconPreviewModal v-model:open="showPreview" :problem-id="previewId" @sent="sendResult = t('beacon.queued', {count: 1})"/>
    </ViewContent>
</template>
