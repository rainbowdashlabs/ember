/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember} from '@/components/input/select/memberOption'
import ProfileFieldsLayout, {type LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import {valueFields} from '@/components/profilefields/fieldLayout'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import { managedMembers } from '@/api'
import type { ManagedMember } from '@/api/managedMembers'
import { decodeProfileValues, getFieldValue, setFieldValue } from '@/util/profileFields'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import ManagedAccessPanel from './managedview/ManagedAccessPanel.vue'

const { t } = useI18n()

const members = ref<ManagedMember[]>([])
const fields = ref<ProfileField[]>([])
const selectedMemberId = ref<string>('')
const values = ref<Map<number, string>>(new Map())
const loadingProfile = ref(false)

const memberOptions = computed(() => members.value.map(fromMember))

/**
 * Whether the guardian may write this answer.
 *
 * <p>A question the station keeps to its own member management is one of them to read here, and a
 * question that works itself out from another is nobody's to write. This screen used to look for
 * both in the question's settings, where only the second of them lives, so a question marked for
 * the member management alone was offered to every guardian.
 */
function readonlyHere(field: ProfileField): boolean {
  return !!field.readonly || !!parseFieldConfig(field.config).computed
}

function valueOf(field: LaidOutField): string {
  return getValue(field.id)
}

function getValue(fieldId: number): string {
  return getFieldValue(values, fieldId)
}

function setValue(fieldId: number, val: string) {
  setFieldValue(values, fieldId, val)
}

const { loading, failure } = useAsyncLoader(async () => {
  members.value = await managedMembers.listManaged()
})

async function loadMemberProfile() {
  if (!selectedMemberId.value) return
  loadingProfile.value = true
  failure.value = null
  try {
    const memberId = Number(selectedMemberId.value)
    const profile = await managedMembers.getProfile(memberId)
    fields.value = profile.fields
    values.value = decodeProfileValues(profile.values)
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
    const entries = valueFields(fields.value)
      .filter(f => !readonlyHere(f))
      .map(f => ({ fieldId: f.id, value: JSON.stringify(getValue(f.id)) }))
    await managedMembers.setProfile(Number(selectedMemberId.value), entries)
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

          <EmptyState compact v-if="members.length === 0">{{ t('profileManaged.noManaged') }}</EmptyState>

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
            @update="(field, value) => setValue(field.id, value)"
          />

          <SaveButton data-onboarding="managed.fields.save" :action="saveProfile"/>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
