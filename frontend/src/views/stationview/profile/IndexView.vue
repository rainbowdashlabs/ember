/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import { useSession } from '@/composables/useSession'
import { useProfileAnswers } from '@/composables/useProfileAnswers'
import { ownProfileAnswers } from '@/composables/profileAnswerPorts'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import AccountCard from './indexview/AccountCard.vue'
import IncompleteFieldsAlert from './indexview/IncompleteFieldsAlert.vue'
import ProfileFieldsForm from './indexview/ProfileFieldsForm.vue'
import ReaderDocuments from '@/components/documents/ReaderDocuments.vue'
import SelfServiceCentreLink from './indexview/SelfServiceCentreLink.vue'

const { t } = useI18n()
const { sessionInfo } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const answers = useProfileAnswers(ownProfileAnswers)
const {fields, valueOf: getValue, update: setValue} = answers

const memberId = computed(() => sessionInfo.value?.member?.id ?? null)

const fullName = computed(() => {
  const account = sessionInfo.value?.account
  if (!account) return ''
  return `${account.firstName ?? ''} ${account.lastName ?? ''}`.trim()
})

const accountEmail = computed(() => sessionInfo.value?.account?.email ?? '')

/**
 * The questions this profile asks, as the server works them out.
 *
 * <p>Which questions reach whom is one rule, and it used to be written twice: once here and once on
 * the server, from where the editing screen reads it. They drifted, and the copy here threw away
 * every question a station asks of one group, so somebody in the instructors' group was never shown
 * what the instructors are asked. Asked for rather than worked out again.
 */
const editableFields = computed(() => fields.value)

const incompleteFields = computed(() => {
  return editableFields.value.filter(f => {
    if (!f.required || f.readonly) return false
    const val = getValue(f)
    return !val || val === '""' || val === '' || val === 'null'
  })
})

const { loading, failure, reload } = useAsyncLoader(async () => {
  if (!memberId.value) return
  await answers.load(memberId.value)
})

async function saveProfile() {
  if (!memberId.value) return
  failure.value = null
  try {
    await answers.save(memberId.value)
    refreshSidebarCounts()
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
}

watch(memberId, (newId) => {
  if (newId) reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.profile.title')"
      :subtitle="t('pages.profile.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure"/>

      <template v-if="!loading && memberId">
        <AccountCard
            :account-uid="sessionInfo?.account?.uid"
            :full-name="fullName"
            :account-email="accountEmail"
        />

        <IncompleteFieldsAlert :incomplete-fields="incompleteFields" />

        <ProfileFieldsForm
            :editable-fields="editableFields"
            :get-value="getValue"
            :save-action="saveProfile"
            @update="setValue"
        />

        <ReaderDocuments :offers="false"/>
        <SelfServiceCentreLink/>
      </template>
    </div>
  </ViewContent>
</template>
