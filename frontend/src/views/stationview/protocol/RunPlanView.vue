/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import PlanSectionNode from './runplanview/PlanSectionNode.vue'
import PlanUnassignedNotice from './runplanview/PlanUnassignedNotice.vue'
import {useRunPlan} from './runplanview/useRunPlan'

/**
 * The step between starting a run and grading it: who examines which section. A run left without
 * examiners is graded by every tester, as runs always were; once anybody is named, only the named
 * examiners grade it, each their own sections and those nobody was named for.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const runId = computed(() => Number(route.params.id))
const plan = useRunPlan(runId)
const nothingToCopy = ref(false)

const {loading, failure, reload} = useAsyncLoader(() => plan.load(), {autoLoad: false})

const runPage = computed(() => ({name: 'protocol-run-detail', params: {id: runId.value}}))

const {running: saving, failure: saveFailure, run: saveAndOpenRun} = useAsyncAction(async () => {
  await plan.save()
  await router.push(runPage.value)
})

async function copyPrevious() {
  nothingToCopy.value = !(await plan.copyPrevious())
}

const pageTitle = computed(() => plan.run.value
    ? t('pages.protocol-run-plan.titleNamed', {name: plan.run.value.name})
    : t('pages.protocol-run-plan.title'))

watch(loaded, (v) => { if (v) reload() }, {immediate: true})
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.protocol-run-plan.subtitle')">
    <AsyncSection :loading="loading" :failure="failure" :empty="false">
      <div class="space-y-4">
        <MutedText tag="p" size="sm">{{ t('protocol.plan.hint') }}</MutedText>
        <ButtonRow>
          <SecondaryButton :icon="['fas', 'clone']" @click="copyPrevious">{{ t('protocol.plan.copyPrevious') }}</SecondaryButton>
        </ButtonRow>
        <Alert v-if="nothingToCopy" variant="info">{{ t('protocol.plan.nothingToCopy') }}</Alert>
        <PlanUnassignedNotice :sections="plan.unassigned.value"/>

        <PlanSectionNode v-for="section in plan.tree.value.childrenOf(null)" :key="section.id"
                         :section="section" :depth="0" :plan="plan"/>

        <FailureAlert :failure="saveFailure"/>
        <ButtonRow pair align="end">
          <SecondaryButton @click="router.push(runPage)">{{ t('protocol.plan.skip') }}</SecondaryButton>
          <PrimaryButton :disabled="saving" @click="saveAndOpenRun()">{{ t('protocol.plan.save') }}</PrimaryButton>
        </ButtonRow>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
