/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import { getRequirements } from '@/api/requirements'
import type { RequirementsResponse } from '@/api/requirements'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const data = ref<RequirementsResponse | null>(null)

const hasRequirements = computed(() =>
    data.value != null && (data.value.profileIncomplete || data.value.forcedForms.length > 0 || data.value.forcedQuizzes.length > 0)
)

function redirectAway() {
    const redirect = route.query.redirect as string | undefined
    router.replace(redirect || {name: 'dashboard-overview'})
}

onMounted(async () => {
    try {
        data.value = await getRequirements()
    } catch {
        redirectAway()
        return
    }
    loading.value = false
    if (!hasRequirements.value) {
        redirectAway()
    }
})
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <template v-else-if="hasRequirements">
            <SectionHeader class="mb-2">{{ t('requirements.title') }}</SectionHeader>
            <p class="text-sm text-(--text-muted) mb-6">{{ t('requirements.subtitle') }}</p>

            <div class="space-y-4 max-w-xl">
                <NeutralContainer v-if="data!.profileIncomplete">
                    <div class="flex items-center gap-3">
                        <font-awesome-icon :icon="['fas', 'user']" class="text-primary text-xl" />
                        <div class="flex-1">
                            <SubHeader>{{ t('requirements.profileTitle') }}</SubHeader>
                            <p class="text-sm text-(--text-muted)">{{ t('requirements.profileText') }}</p>
                        </div>
                        <PrimaryButton @click="router.push('/station/profile')">{{ t('requirements.fillOut') }}</PrimaryButton>
                    </div>
                </NeutralContainer>

                <NeutralContainer v-for="form in data!.forcedForms" :key="`form-${form.id}`">
                    <div class="flex items-center gap-3">
                        <font-awesome-icon :icon="['fas', 'square-poll-vertical']" class="text-primary text-xl" />
                        <div class="flex-1">
                            <SubHeader>{{ form.title }}</SubHeader>
                            <p class="text-sm text-(--text-muted)">{{ t('requirements.formText') }}</p>
                        </div>
                        <PrimaryButton @click="router.push(`/station/forms/${form.id}/fill`)">{{ t('requirements.fillOut') }}</PrimaryButton>
                    </div>
                </NeutralContainer>

                <NeutralContainer v-for="quiz in data!.forcedQuizzes" :key="`quiz-${quiz.id}`">
                    <div class="flex items-center gap-3">
                        <font-awesome-icon :icon="['fas', 'graduation-cap']" class="text-primary text-xl" />
                        <div class="flex-1">
                            <SubHeader>{{ quiz.title }}</SubHeader>
                            <p class="text-sm text-(--text-muted)">{{ t('requirements.quizText') }}</p>
                        </div>
                        <PrimaryButton @click="router.push(`/station/quiz/tests/${quiz.id}`)">{{ t('requirements.startQuiz') }}</PrimaryButton>
                    </div>
                </NeutralContainer>
            </div>
        </template>
    </ViewContent>
</template>
