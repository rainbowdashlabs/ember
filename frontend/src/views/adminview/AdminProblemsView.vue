/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ProblemLevelSummary from '@/components/problem/ProblemLevelSummary.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ProblemEntryCard from './adminproblemsview/ProblemEntryCard.vue'
import {useProblemList} from './adminproblemsview/useProblemList'
import {beacon} from '@/api'
import BeaconPreviewModal from '@/components/log/BeaconPreviewModal.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {ProblemLevel, ProblemState, type BeaconStatus} from '@/api/generated/schema'
import {problemTitle, type ProblemAction, type ProblemStateFilter} from '@/api/problems'

const {t} = useI18n()
const list = useProblemList()
const {refusals, state, level, visible, loading, failure, result, selected, deletion} = list
const {show: showDelete, target: deleteTarget, confirm: confirmDelete} = deletion

const expandedId = ref<number | null>(null)

const stateOptions = computed<{value: ProblemStateFilter, label: string}[]>(() => [
    {value: ProblemState.OPEN, label: t('adminProblems.stateOpen')},
    {value: ProblemState.RESOLVED, label: t('adminProblems.stateResolved')},
    {value: ProblemState.MUTED, label: t('adminProblems.stateMuted')},
    {value: 'all', label: t('adminProblems.stateAll')},
])

const levelOptions = computed(() => [
    {value: ProblemLevel.ERROR, label: t('adminProblems.errors')},
    {value: ProblemLevel.WARN, label: t('adminProblems.warnings')},
])

/** What an empty list says: the all clear for open faults is the summary's, the rest is said here. */
const emptyText = computed(() => {
    if (state.value !== ProblemState.OPEN || level.value != null) return t('adminProblems.noneInState')
    return refusals.value ? t('adminProblems.noRefusals') : ''
})

function toggleExpand(id: number) {
    expandedId.value = expandedId.value === id ? null : id
}

function act(action: ProblemAction, id: number) {
    if (action === 'send') openPreview(id)
    else if (action === 'delete') list.requestDelete(id)
    else void list.change(action, id)
}

/**
 * The beacon half of this screen, which only appears where an instance actually reports to one.
 *
 * <p>Whether it does is configuration rather than a stored setting, so this asks and shows; it never
 * offers a switch that would not survive a restart. Refusals never go to the beacon, so their list
 * offers no way to send them.
 */
const beaconStatus = ref<BeaconStatus | null>(null)
const canSend = computed(() => beaconStatus.value?.enabled === true && !refusals.value)
const previewId = ref<number | null>(null)
const showPreview = ref(false)

onMounted(async () => {
    try {
        beaconStatus.value = await beacon.getStatus()
    } catch {
        beaconStatus.value = null
    }
})

function openPreview(id: number) {
    previewId.value = id
    showPreview.value = true
}
</script>

<template>
    <ViewContent :title="t('pages.admin-problems.title')" :subtitle="t('pages.admin-problems.subtitle')">
        <div role="group" :aria-label="t('adminProblems.kindGroup')" class="flex flex-wrap items-center gap-2 mb-2">
            <SelectionToggleButton :selected="!refusals" data-testid="problems-faults" @toggle="list.showKind(false)">
                {{ t('adminProblems.faults') }}
            </SelectionToggleButton>
            <SelectionToggleButton :selected="refusals" data-testid="problems-refusals" @toggle="list.showKind(true)">
                {{ t('adminProblems.refusals') }}
            </SelectionToggleButton>
        </div>
        <div role="group" :aria-label="t('adminProblems.stateGroup')" class="flex flex-wrap items-center gap-2 mb-2">
            <SelectionToggleButton
                v-for="option in stateOptions"
                :key="option.value"
                :selected="state === option.value"
                :data-testid="`problems-state-${option.value}`"
                @toggle="list.showState(option.value)"
            >
                {{ option.label }}
            </SelectionToggleButton>
        </div>
        <div v-if="!refusals" role="group" :aria-label="t('adminProblems.levelGroup')" class="flex flex-wrap items-center gap-2 mb-2">
            <SelectionToggleButton
                v-for="option in levelOptions"
                :key="option.value"
                :selected="level === option.value"
                @toggle="list.toggleLevel(option.value)"
            >
                {{ option.label }}
            </SelectionToggleButton>
        </div>
        <ButtonRow class="mb-4">
            <SecondaryButton v-if="list.canResolveAll.value" :icon="['fas', 'check-double']" data-testid="problems-resolve-all" @click="list.resolveAll">
                {{ t('adminProblems.resolveAll') }}
            </SecondaryButton>
            <SecondaryButton
                v-if="canSend && selected.size > 0"
                :icon="['fas', 'tower-broadcast']"
                data-testid="beacon-send-selected"
                @click="list.sendSelected"
            >
                {{ t('beacon.sendSelected', {count: selected.size}) }}
            </SecondaryButton>
        </ButtonRow>

        <FailureAlert :failure="failure" class="mb-4"/>
        <Alert v-if="result" variant="success" class="mb-4">{{ result }}</Alert>

        <p v-if="refusals" class="text-sm text-(--text-muted) mb-4">{{ t('adminProblems.refusalsHint') }}</p>
        <ProblemLevelSummary
            v-if="!refusals && state === ProblemState.OPEN"
            :empty-label="t('adminProblems.noProblems')"
            :error-count="list.errorCount.value"
            :warn-count="list.warnCount.value"
        />

        <Spinner v-if="loading"/>
        <p v-else-if="visible.length === 0 && emptyText" class="text-sm mb-4" data-testid="problems-empty">{{ emptyText }}</p>
        <div v-else class="space-y-2">
            <div v-for="entry in visible" :key="entry.id" class="flex items-start gap-2" :data-testid="`problem-${entry.id}`">
                <CheckboxInput
                    v-if="canSend"
                    :model-value="selected.has(entry.id)"
                    class="mt-4"
                    :data-testid="`beacon-pick-${entry.id}`"
                    @update:model-value="list.toggleSelected(entry.id)"
                />
                <ProblemEntryCard
                    class="flex-1"
                    :entry="entry"
                    :expanded="expandedId === entry.id"
                    :can-send="canSend"
                    @toggle="toggleExpand"
                    @act="act"
                />
            </div>
        </div>

        <BeaconPreviewModal v-model:open="showPreview" kind="problem" :entry-id="previewId" @sent="result = t('beacon.queued', {count: 1})"/>
        <ConfirmDeleteModal
            v-model="showDelete"
            :message="t('adminProblems.deleteMessage', {title: deleteTarget ? problemTitle(deleteTarget) : ''})"
            @confirm="confirmDelete"
        />
    </ViewContent>
</template>
