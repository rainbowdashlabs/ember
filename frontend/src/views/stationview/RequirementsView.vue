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
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {getRequirements, type RequirementsResponse} from '@/api/requirements'
import {usableRedirect} from '@/util/redirect'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const data = ref<RequirementsResponse | null>(null)

/**
 * What holds the reader here until it is dealt with.
 *
 * <p>A self-check is deliberately not one of them. It is offered below with everything else, and a
 * task due in four weeks must not meet a member with a wall every time they sign in.
 */
const blocking = computed(() =>
    data.value != null && (data.value.profileIncomplete || data.value.forcedForms.length > 0 || data.value.forcedQuizzes.length > 0)
)

const selfChecks = computed(() => data.value?.selfChecks ?? [])

const hasAnything = computed(() => blocking.value || selfChecks.value.length > 0)

/** Whether the reader was sent here on their way somewhere else rather than coming here themselves. */
const sentHere = computed(() => typeof route.query.redirect === 'string')

function redirectAway() {
    const redirect = route.query.redirect
    const target = typeof redirect === 'string' ? redirect : null
    router.replace(usableRedirect(target) ? target : '/station/dashboard/overview')
}

onMounted(async () => {
    try {
        data.value = await getRequirements()
    } catch {
        redirectAway()
        return
    }
    loading.value = false
    if (!blocking.value && (sentHere.value || !hasAnything.value)) {
        redirectAway()
    }
})
</script>

<template>
    <ViewContent
        :title="t('pages.station-requirements.title')"
        :subtitle="t('pages.station-requirements.subtitle')"
    >
        <Spinner v-if="loading" />
        <template v-else-if="hasAnything">
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
                        <PrimaryButton @click="router.push({name: 'quiz-test-take', params: {id: quiz.id}})">{{ t('requirements.startQuiz') }}</PrimaryButton>
                    </div>
                </NeutralContainer>

                <NeutralContainer v-for="selfCheck in selfChecks" :key="`self-check-${selfCheck.id}`" data-testid="requirement-self-check">
                    <div class="flex items-center gap-3">
                        <font-awesome-icon :icon="['fas', 'shirt']" class="text-primary text-xl" />
                        <div class="flex-1">
                            <SubHeader>{{ t('requirements.selfCheckTitle') }}</SubHeader>
                            <p class="text-sm text-(--text-muted)">{{ t('requirements.selfCheckText') }}</p>
                        </div>
                        <PrimaryButton @click="router.push({name: 'inventory-self-check', params: {id: selfCheck.id}})">
                            {{ t('requirements.answerSelfCheck') }}
                        </PrimaryButton>
                    </div>
                </NeutralContainer>
            </div>
        </template>
    </ViewContent>
</template>
