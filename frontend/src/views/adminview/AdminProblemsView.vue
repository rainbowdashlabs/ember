/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ProblemEntryCard from './adminproblemsview/ProblemEntryCard.vue'
import {problems} from '@/api'
import type {ProblemEntry} from '@/api/problems'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const showAcknowledged = ref(false)
const expandedId = ref<number | null>(null)

const {config: entries, loading, error, reload: loadData} = useConfigPanel<ProblemEntry[]>({
    initial: [],
    fetch: () => problems.listProblems(showAcknowledged.value),
})

const errorCount = computed(() => entries.value.filter(e => e.level === 'ERROR' && !e.acknowledged).length)
const warnCount = computed(() => entries.value.filter(e => e.level === 'WARN' && !e.acknowledged).length)

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
                <SecondaryButton :icon="['fas', 'check-double']" v-if="entries.some(e => !e.acknowledged)" @click="ackAll">
                    {{ t('adminProblems.acknowledgeAll') }}
                </SecondaryButton>
            </div>
        </div>

        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

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
            <ProblemEntryCard
                v-for="entry in entries"
                :key="entry.id"
                :entry="entry"
                :expanded="expandedId === entry.id"
                @toggle="toggleExpand"
                @ack="ack"
            />
        </div>
    </ViewContent>
</template>
