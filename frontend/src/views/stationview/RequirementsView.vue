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
import Spinner from '@/components/feedback/Spinner.vue'
import RequirementCard from './requirementsview/RequirementCard.vue'
import {getRequirements, type RegistrationUpdateRequirement, type RequirementsResponse} from '@/api/requirements'
import {usableRedirect} from '@/util/redirect'
import {formatDate} from '@/util/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const data = ref<RequirementsResponse | null>(null)

/**
 * What holds the reader here until it is dealt with.
 *
 * <p>A self-check is deliberately not one of them, and neither is a registration short of an
 * answer. Both are offered below with everything else, and a task due in four weeks must not meet
 * a member with a wall every time they sign in.
 */
const blocking = computed(() =>
    data.value != null && (data.value.profileIncomplete || data.value.forcedForms.length > 0 || data.value.forcedQuizzes.length > 0)
)

const selfChecks = computed(() => data.value?.selfChecks ?? [])

/** Registrations owing an answer to a question their appointment gained after the sign-up. */
const registrationUpdates = computed(() => data.value?.registrationUpdates ?? [])

const hasAnything = computed(() => blocking.value || selfChecks.value.length > 0 || registrationUpdates.value.length > 0)

/** Whether the reader was sent here on their way somewhere else rather than coming here themselves. */
const sentHere = computed(() => typeof route.query.redirect === 'string')

/** Names whose registration it is only where it is not the reader's own. */
function registrationText(update: RegistrationUpdateRequirement): string {
    const date = formatDate(update.eventDate)
    return update.memberName
        ? t('requirements.registrationTextFor', {date, name: update.memberName})
        : t('requirements.registrationText', {date})
}

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
                <RequirementCard
                    v-if="data!.profileIncomplete"
                    icon="user"
                    :title="t('requirements.profileTitle')"
                    :text="t('requirements.profileText')"
                    :action="t('requirements.fillOut')"
                    @open="router.push('/station/profile')"
                />
                <RequirementCard
                    v-for="form in data!.forcedForms"
                    :key="`form-${form.id}`"
                    icon="square-poll-vertical"
                    :title="form.title"
                    :text="t('requirements.formText')"
                    :action="t('requirements.fillOut')"
                    @open="router.push(`/station/forms/${form.id}/fill`)"
                />
                <RequirementCard
                    v-for="quiz in data!.forcedQuizzes"
                    :key="`quiz-${quiz.id}`"
                    icon="graduation-cap"
                    :title="quiz.title"
                    :text="t('requirements.quizText')"
                    :action="t('requirements.startQuiz')"
                    @open="router.push({name: 'quiz-test-take', params: {id: quiz.id}})"
                />
                <RequirementCard
                    v-for="update in registrationUpdates"
                    :key="`registration-${update.registrationId}`"
                    icon="calendar-days"
                    :title="update.eventName"
                    :text="registrationText(update)"
                    :action="t('requirements.updateRegistration')"
                    data-testid="requirement-registration-update"
                    @open="router.push({name: 'event-detail-date', params: {id: update.eventId, date: update.eventDate}})"
                />
                <RequirementCard
                    v-for="selfCheck in selfChecks"
                    :key="`self-check-${selfCheck.id}`"
                    icon="shirt"
                    :title="t('requirements.selfCheckTitle')"
                    :text="t('requirements.selfCheckText')"
                    :action="t('requirements.answerSelfCheck')"
                    data-testid="requirement-self-check"
                    @open="router.push({name: 'inventory-self-check', params: {id: selfCheck.id}})"
                />
            </div>
        </template>
    </ViewContent>
</template>
