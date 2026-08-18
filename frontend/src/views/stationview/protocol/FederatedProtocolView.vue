/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {ProtocolDetailResponse} from '@/api/protocol'
import {federation, protocol} from '@/api'
import {useConfigPanel} from '@/composables/useConfigPanel'

const props = defineProps<{
    stationUid: string
    protocolId: number
}>()

const {t} = useI18n()
const router = useRouter()

const {config: detail, loading, error, reload} = useConfigPanel<ProtocolDetailResponse | null>({
    initial: null,
    fetch: () => protocol.getFederatedProtocol(props.stationUid, props.protocolId),
})

const itemsBySection = computed(() => {
    const grouped = new Map<number, typeof items>()
    const items = detail.value?.items ?? []
    for (const item of items) {
        const existing = grouped.get(item.sectionId)
        if (existing) existing.push(item)
        else grouped.set(item.sectionId, [item])
    }
    return grouped
})

async function copyToStation() {
    try {
        await federation.copyProtocol(props.protocolId)
        router.push({name: 'protocol-list'})
    } catch {
        error.value = t('common.error')
    }
}

watch(() => [props.stationUid, props.protocolId], () => reload())
</script>

<template>
    <ViewContent
        :title="t('pages.federated-protocol.title')"
        :subtitle="t('pages.federated-protocol.subtitle')"
    >
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Spinner v-if="loading" size="lg"/>

        <template v-else-if="detail">
            <div class="flex flex-wrap items-center gap-2 mb-4">
                <SecondaryButton @click="router.push({name: 'protocol-list'})">
                    <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                    {{ t('common.back') }}
                </SecondaryButton>
                <PageHeader class="flex-1 !mb-0">{{ detail.protocol.name }}</PageHeader>
                <PrimaryButton @click="copyToStation">
                    <font-awesome-icon :icon="['fas', 'copy']"/>
                    {{ t('federation.copyToStation') }}
                </PrimaryButton>
            </div>

            <StationBadge :station-name="t('federation.partnerStation')" class="mb-2"/>
            <MutedText v-if="detail.protocol.description" tag="p" size="sm" class="mb-4">
                {{ detail.protocol.description }}
            </MutedText>

            <p v-if="detail.sections.length === 0" class="text-[var(--text-muted)]">
                {{ t('protocol.noSections') }}
            </p>
            <div v-else class="space-y-3">
                <NeutralContainer v-for="section in detail.sections" :key="section.id">
                    <SubHeader class="!mb-1">{{ section.name }}</SubHeader>
                    <MutedText v-if="section.description" tag="p" size="sm" class="mb-2">
                        {{ section.description }}
                    </MutedText>
                    <ul class="space-y-1">
                        <li
                            v-for="item in itemsBySection.get(section.id) ?? []"
                            :key="item.id"
                            class="flex items-center justify-between gap-4 text-sm"
                        >
                            <span class="min-w-0 truncate">{{ item.label }}</span>
                            <span class="text-[var(--text-muted)] shrink-0">{{ item.points }}</span>
                        </li>
                    </ul>
                </NeutralContainer>
            </div>
        </template>
    </ViewContent>
</template>
