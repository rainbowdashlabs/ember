/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import MemberRelationsPanel from '../relations/MemberRelationsPanel.vue'
import { useMemberManagers } from '../relations/useMemberManagers'
import { useManagedMembers } from '../relations/useManagedMembers'
import { useMemberProfileFields } from '../detailview/useMemberProfileFields'
import { memberDisplayName } from '../listview/useMemberData'
import { StationUserType, type StationMember } from '@/api/types'
import { profileFields, stationMembers } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const props = defineProps<{
  memberId: number
  userType: string
  allMembers: StationMember[]
}>()

const memberId = toRef(props, 'memberId')
const allMembers = ref<StationMember[]>([...props.allMembers])
const error = ref('')

const userType = toRef(props, 'userType')
const { fields, fieldsForUserType, setValues } = useMemberProfileFields(userType)

const {
  managers,
  availableManagers,
  getManagerFields,
  getManagerFieldValue,
  loadDetails: loadManagerDetails,
  linkManager,
  removeManager,
  createManager,
} = useMemberManagers(memberId, allMembers, fieldsForUserType, error)

const {managedMembers, availableManaged, linkManaged, removeManaged} =
    useManagedMembers(memberId, allMembers, error)

const showGuardians = computed(() =>
    props.userType === StationUserType.MEMBER || props.userType === StationUserType.TRIAL)

const showManaged = computed(() => props.userType === StationUserType.GUARDIAN)

const { loading } = useAsyncLoader(async () => {
  const [allFields, mgrs, managed, values] = await Promise.all([
    profileFields.getMemberFields(memberId.value),
    stationMembers.getManagers(memberId.value),
    stationMembers.getManaged(memberId.value),
    profileFields.getMergedValues(memberId.value),
  ])
  fields.value = allFields
  managers.value = mgrs
  managedMembers.value = managed
  setValues(values)
  await loadManagerDetails(mgrs)
})
</script>

<template>
  <div class="space-y-6">
    <Spinner v-if="loading" size="md"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <MemberRelationsPanel
        v-if="!loading"
        :show-guardians="showGuardians"
        :show-managed="showManaged"
        :managers="managers"
        :managed-members="managedMembers"
        :available-managers="availableManagers"
        :available-managed="availableManaged"
        :fields="fields"
        :can-edit="true"
        :member-display-name="memberDisplayName"
        :get-manager-fields="getManagerFields"
        :get-manager-field-value="getManagerFieldValue"
        @link-manager="linkManager"
        @remove-manager="removeManager"
        @create-manager="createManager"
        @link-managed="linkManaged"
        @remove-managed="removeManaged"
    />
  </div>
</template>
