/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ProfileFieldsLayout, {type LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import MemberDocumentsPanel from './clustermemberdetailview/MemberDocumentsPanel.vue'
import {clusterMembers} from '@/api'
import {ClusterPermission} from '@/api/clusters'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import {useProfileAnswers} from '@/composables/useProfileAnswers'
import {associationManagerAnswers, associationSnapshot} from '@/composables/profileAnswerPorts'

/**
 * One person at one of the association's stations, and everything asked of them.
 *
 * <p>The station's own questions and the association's are laid out together, each keeping the origin
 * it arrived with so the answer goes back to the right table. A question the association marked
 * readable but not writable at the station is still writable here: this screen is the association's,
 * and it is the party that put the lock there.
 */
const {t} = useI18n()
const route = useRoute()
const {hasClusterPermission} = useSession()

const memberId = computed(() => Number(route.params.id))
const editable = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_MANAGER))

const name = ref('')
const answers = useProfileAnswers(associationManagerAnswers)
const {fields, dirty, valueOf} = answers
const saved = ref(false)

const {loading, failure, reload} = useAsyncLoader(async () => {
  const profile = await clusterMembers.getManagedMemberProfile(memberId.value)
  answers.apply(associationSnapshot(profile))
  name.value = profile.name
  saved.value = false
})

/**
 * The person by name at the head of the page, because "Mitglied" stands above every one of them and
 * is what the tab, the history and a bookmark carry. The plain word stands until the profile has
 * arrived, and where it could not be fetched at all; the line underneath says what the page is for.
 */
const pageTitle = computed(() => name.value || t('pages.cluster-member-detail.title'))

function onUpdate(field: LaidOutField, value: string) {
  answers.update(field, value)
  saved.value = false
}

/**
 * Stores the answers, each with the table its question lives in.
 *
 * <p>Fetching the profile again afterwards answers for itself, in the alert the loader owns, so a
 * profile that could not be read back is never reported as answers that were refused.
 */
const {running: saving, failure: saveFailure, run: save} = useAsyncAction(async () => {
  await answers.save(memberId.value)
  await reload()
  saved.value = true
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-member-detail.subtitle')" :title="pageTitle">
    <div class="space-y-4">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <FailureAlert :failure="saveFailure"/>
      <Alert v-if="saved" variant="success">{{ t('clusterMemberDetail.saved') }}</Alert>

      <NeutralContainer v-if="!loading && name" class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <SectionHeader>{{ t('clusterMemberDetail.fieldsTitle') }}</SectionHeader>
          <SecondaryBadge>{{ t('clusterMemberDetail.fieldCount', {count: fields.length}) }}</SecondaryBadge>
        </div>

        <p class="text-sm text-(--text-muted)">{{ t('clusterMemberDetail.hint') }}</p>

        <fieldset :disabled="!editable" class="contents">
          <ProfileFieldsLayout :fields="fields" :get-value="valueOf" can-edit-readonly @update="onUpdate"/>
        </fieldset>

        <PrimaryButton v-if="editable" :disabled="saving || !dirty" @click="save">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </NeutralContainer>

      <MemberDocumentsPanel v-if="!loading && name" :can-upload="editable" :member-id="memberId"/>
    </div>
  </ViewContent>
</template>
