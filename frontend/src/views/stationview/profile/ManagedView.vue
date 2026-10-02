/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember} from '@/components/input/select/memberOption'
import ProfileFieldsLayout from '@/components/profilefields/ProfileFieldsLayout.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ManagedMember} from '@/api/generated/schema'
import { managedMembers } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useProfileAnswers } from '@/composables/useProfileAnswers'
import { guardianAnswers } from '@/composables/profileAnswerPorts'
import ManagedAccessPanel from './managedview/ManagedAccessPanel.vue'

const { t } = useI18n()

const members = ref<ManagedMember[]>([])
const selectedMemberId = ref<string>('')
const loadingProfile = ref(false)

/**
 * The answers of the member in the guardian's care. A question the station keeps to its own member
 * management, or one that works itself out from another, is read here and not written: the port
 * says which.
 */
const answers = useProfileAnswers(guardianAnswers)
const {fields, valueOf, update} = answers

const memberOptions = computed(() => members.value.map(fromMember))

const route = useRoute()

/**
 * Opens on the member the address names, where it names one in this reader's care. A reminder about
 * a date that runs out leads here and would otherwise leave the guardian to pick the member again.
 */
function openNamedMember() {
  const named = String(route.query.member ?? '')
  if (!named || !members.value.some(member => String(member.id) === named)) return
  selectedMemberId.value = named
  void loadMemberProfile()
}

const { loading, failure } = useAsyncLoader(async () => {
  members.value = await managedMembers.listManaged()
  openNamedMember()
})

async function loadMemberProfile() {
  if (!selectedMemberId.value) return
  loadingProfile.value = true
  failure.value = null
  try {
    await answers.load(Number(selectedMemberId.value))
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    loadingProfile.value = false
  }
}

async function saveProfile() {
  if (!selectedMemberId.value) return
  failure.value = null
  try {
    await answers.save(Number(selectedMemberId.value))
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.profile-managed.title')"
      :subtitle="t('pages.profile-managed.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure"/>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profileManaged.title') }}</SectionHeader>

          <EmptyState v-if="members.length === 0" compact>{{ t('profileManaged.noManaged') }}</EmptyState>

          <div v-else class="space-y-1">
            <FieldLabel>{{ t('profileManaged.selectMember') }}</FieldLabel>
            <MemberSelectInput v-model="selectedMemberId" data-onboarding="managed.member-select"
                               :members="memberOptions"
                               :placeholder="t('profileManaged.selectMemberPlaceholder')"
                               @change="loadMemberProfile"/>
          </div>
        </NeutralContainer>

        <ManagedAccessPanel v-if="selectedMemberId" :member-id="Number(selectedMemberId)"/>

        <Spinner v-if="loadingProfile" size="md" />

        <NeutralContainer v-if="selectedMemberId && !loadingProfile && fields.length > 0" class="space-y-4">
          <SectionHeader>{{ t('profileManaged.fields') }}</SectionHeader>

          <ProfileFieldsLayout
            :fields="fields"
            :get-value="valueOf"
            @update="update"
          />

          <SaveButton data-onboarding="managed.fields.save" :action="saveProfile"/>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
