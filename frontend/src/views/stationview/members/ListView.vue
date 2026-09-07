/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MemberListPanel from './listview/MemberListPanel.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, type StationMember} from '@/api/types'
import { STATION_MEMBER_SOURCE } from './listview/useMemberData'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useSession } from '@/composables/useSession'
import { useMemberListConfig, type MemberListPort } from './listview/useMemberListConfig'
import { stationMembers } from '@/api'

const { t } = useI18n()
const { hasPermission, canEditMemberAccounts } = useSession()

/** A station lists its own roll and reaches its own member screens. */
const port: MemberListPort = {
  source: STATION_MEMBER_SOURCE,
  routes: {detail: 'members-detail', edit: 'members-edit'},
  canExport: computed(() => hasPermission(StationPermission.MEMBER_EXPORT)),
  canEdit: computed(() => canEditMemberAccounts()),
  exportFileName: 'mitglieder',
}

const config = useMemberListConfig(port)

const resendTarget = ref<StationMember | null>(null)
const resendSuccess = ref('')

const {
  running: resending,
  error: resendError,
  run: confirmResendSetup,
  clearError: clearResendError,
} = useAsyncAction(async () => {
  if (!resendTarget.value) return
  await stationMembers.resendSetupMail(resendTarget.value.id)
  resendSuccess.value = t('membersList.resendSuccess')
  resendTarget.value = null
}, {
  formatError: e => {
    const data = (e as {response?: {data?: {title?: string; message?: string}}})?.response?.data
    return data?.title ?? data?.message ?? t('common.error')
  },
})

function openResendSetup(member: StationMember, event: Event) {
  event.stopPropagation()
  resendTarget.value = member
  clearResendError()
}
</script>

<template>
  <ViewContent
      :title="t('pages.members-list.title')"
      :subtitle="t('pages.members-list.subtitle')"
  >
    <MemberListPanel :config="config" @resend-setup="openResendSetup"/>

    <Modal v-if="resendTarget" model-value @update:model-value="(v) => { if (!v) resendTarget = null }">
      <div class="space-y-4">
        <p>{{ t('membersList.resendConfirm', {name: resendTarget?.name ?? ''}) }}</p>
        <Alert v-if="resendError" variant="error">{{ resendError }}</Alert>
        <div class="flex justify-end gap-3">
          <SecondaryButton :disabled="resending" @click="resendTarget = null">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']" :disabled="resending" @click="confirmResendSetup">
            {{ t('membersList.resendAction') }}
          </PrimaryButton>
        </div>
      </div>
    </Modal>

    <Alert v-if="resendSuccess" variant="success" class="mt-4">
      {{ resendSuccess }}
      <a class="ml-2 underline cursor-pointer" @click="resendSuccess = ''">{{ t('common.close') }}</a>
    </Alert>
  </ViewContent>
</template>
