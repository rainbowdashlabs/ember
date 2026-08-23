/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NewMemberFields from './NewMemberFields.vue'
import {clusterMembers} from '@/api'
import type {ManagedStation} from '@/api/clusterMembers'
import {StationUserType, type StationUserTypeName} from '@/api/types'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Taking somebody on from the association's list.
 *
 * <p>The station comes first, because a member belongs to a station and the association is standing in for
 * one: everything after that question is what the station's own screen asks. What the association may set
 * afterwards, it sets on the person's own page, which is where the association's questions are answered.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  stations: ManagedStation[]
}>()

const emit = defineEmits<{
  created: []
}>()

const {t} = useI18n()

const stationUid = ref('')
const firstName = ref('')
const lastName = ref('')
const email = ref('')
const canLogin = ref(true)
const userType = ref<StationUserTypeName>(StationUserType.MEMBER)

const canSave = computed(() =>
    stationUid.value !== ''
    && firstName.value.trim().length > 0
    && lastName.value.trim().length > 0
    && (!canLogin.value || email.value.trim().length > 0))

watch(open, isOpen => {
  if (isOpen) return
  stationUid.value = ''
  firstName.value = ''
  lastName.value = ''
  email.value = ''
  canLogin.value = true
  userType.value = StationUserType.MEMBER
})

const {running, error, run: save} = useAsyncAction(async () => {
  await clusterMembers.createManagedMember(stationUid.value, {
    firstName: firstName.value.trim(),
    lastName: lastName.value.trim(),
    email: canLogin.value ? email.value.trim() : undefined,
    userType: userType.value,
  })
  open.value = false
  emit('created')
})
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4" data-testid="cluster-member-create-modal">
      <SubHeader>{{ t('clusterMemberManagement.create.title') }}</SubHeader>
      <MutedText size="sm">{{ t('clusterMemberManagement.create.hint') }}</MutedText>

      <NewMemberFields
          v-model:station-uid="stationUid"
          v-model:first-name="firstName"
          v-model:last-name="lastName"
          v-model:email="email"
          v-model:can-login="canLogin"
          v-model:user-type="userType"
          :stations="props.stations"
      />

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div class="flex justify-end gap-2">
        <SecondaryButton :disabled="running" @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="running || !canSave" data-testid="cluster-member-create-save" @click="save">
          {{ t('clusterMemberManagement.create.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
