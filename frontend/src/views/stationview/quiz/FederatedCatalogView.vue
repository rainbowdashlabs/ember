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
import type {FederatedCatalogDetail} from '@/api/quiz'
import {federation, quiz} from '@/api'
import {useConfigPanel} from '@/composables/useConfigPanel'

const props = defineProps<{
    stationUid: string
    catalogId: number
}>()

const {t} = useI18n()
const router = useRouter()

const {config: detail, loading, error, reload} = useConfigPanel<FederatedCatalogDetail | null>({
    initial: null,
    fetch: () => quiz.getFederatedCatalog(props.stationUid, props.catalogId),
})

const questionCountByCategory = computed(() => {
    const counts = new Map<number | null, number>()
    for (const question of detail.value?.questions ?? []) {
        counts.set(question.categoryId, (counts.get(question.categoryId) ?? 0) + 1)
    }
    return counts
})

async function copyToStation() {
    try {
        await federation.copyQuizCatalog(props.catalogId)
        router.push({name: 'quiz-catalogs'})
    } catch {
        error.value = t('common.error')
    }
}

watch(() => [props.stationUid, props.catalogId], () => reload())
</script>

<template>
    <ViewContent
        :title="t('pages.federated-quiz-catalog.title')"
        :subtitle="t('pages.federated-quiz-catalog.subtitle')"
    >
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Spinner v-if="loading" size="lg"/>

        <template v-else-if="detail">
            <div class="flex flex-wrap items-center gap-2 mb-4">
                <SecondaryButton @click="router.push({name: 'quiz-catalogs'})">
                    <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                    {{ t('common.back') }}
                </SecondaryButton>
                <PageHeader class="flex-1 !mb-0">{{ detail.catalog.name }}</PageHeader>
                <PrimaryButton @click="copyToStation">
                    <font-awesome-icon :icon="['fas', 'copy']"/>
                    {{ t('federation.copyToStation') }}
                </PrimaryButton>
            </div>

            <StationBadge :station-name="t('federation.partnerStation')" class="mb-2"/>
            <MutedText v-if="detail.catalog.description" tag="p" size="sm" class="mb-4">
                {{ detail.catalog.description }}
            </MutedText>

            <NeutralContainer class="mb-4">
                <div class="flex items-center justify-between">
                    <SubHeader class="!mb-0">{{ t('quiz.questions.title') }}</SubHeader>
                    <span class="text-sm">{{ detail.questions.length }}</span>
                </div>
            </NeutralContainer>

            <SubHeader class="mb-2">{{ t('quiz.categories.title') }}</SubHeader>
            <p v-if="detail.categories.length === 0" class="text-[var(--text-muted)]">
                {{ t('quiz.categories.noCategories') }}
            </p>
            <div v-else class="space-y-2">
                <NeutralContainer v-for="category in detail.categories" :key="category.id">
                    <div class="flex items-center justify-between gap-4">
                        <div class="min-w-0">
                            <span class="font-medium">{{ category.name }}</span>
                            <MutedText v-if="category.description" tag="p" size="sm" class="truncate">
                                {{ category.description }}
                            </MutedText>
                        </div>
                        <span class="text-sm text-[var(--text-muted)] shrink-0">
                            {{ questionCountByCategory.get(category.id) ?? 0 }}
                        </span>
                    </div>
                </NeutralContainer>
            </div>
        </template>
    </ViewContent>
</template>
