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
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {StationPermission} from '@/api/generated/schema'
import { STATION_MEMBER_SOURCE, type RosterMember } from './listview/useMemberData'
import { useConfirmAction } from '@/composables/useConfirmAction'
import type {Failure} from '@/util/failure'
import { useSession } from '@/composables/useSession'
import { useMemberListConfig, type MemberListPort } from './listview/useMemberListConfig'
import { stationMembers } from '@/api'
import BulkGenerateModal from '@/components/documents/bulk/BulkGenerateModal.vue'

const { t } = useI18n()
const { hasPermission, canEditMemberAccounts } = useSession()

/** A station lists its own roll and reaches its own member screens. */
const port: MemberListPort = {
  source: STATION_MEMBER_SOURCE,
  routes: {detail: 'members-detail', edit: 'members-edit'},
  canExport: computed(() => hasPermission(StationPermission.MEMBER_EXPORT)),
  canEdit: computed(() => canEditMemberAccounts()),
  canGenerateDocuments: computed(() => hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)),
  exportFileName: 'mitglieder',
  tableId: 'members',
}

const config = useMemberListConfig(port)

const resendSuccess = ref('')
const resending = ref(false)

/** Why the last setup mail could not be sent, shown on the page once the question has closed. */
const resendFailure = ref<Failure | null>(null)

const {
  show: confirmingResend,
  target: resendTarget,
  request: requestResend,
  confirm: confirmResendSetup,
} = useConfirmAction<RosterMember>({
  onConfirm: async member => {
    resending.value = true
    try {
      await stationMembers.resendSetupMail(member.id)
    } finally {
      resending.value = false
    }
  },
  onSuccess: () => {
    resendSuccess.value = t('membersList.resendSuccess')
  },
  failure: resendFailure,
})

/** Asks before sending the setup mail again, unless shift is held. */
function openResendSetup(member: RosterMember) {
  resendFailure.value = null
  requestResend(member)
}

/** The members chosen in the list, handed to a run that generates a document for each. */
const documentMembers = ref<number[] | null>(null)
const generatingDocuments = computed({
  get: () => documentMembers.value !== null,
  set: (open: boolean) => {
    if (!open) documentMembers.value = null
  },
})
</script>

<template>
  <ViewContent
      :title="t('pages.members-list.title')"
      :subtitle="t('pages.members-list.subtitle')"
  >
    <MemberListPanel :config="config" linked-tabs @resend-setup="openResendSetup" @generate-documents="(ids) => documentMembers = ids"/>
    <BulkGenerateModal v-if="documentMembers" v-model="generatingDocuments" :member-ids="documentMembers"/>

    <ConfirmDeleteModal
        v-model="confirmingResend"
        :message="t('membersList.resendConfirm', {name: resendTarget?.name ?? ''})"
        :confirm-label="t('membersList.resendAction')"
        :confirm-icon="['fas', 'paper-plane']"
        :busy="resending"
        neutral
        @confirm="confirmResendSetup"
    />

    <FailureAlert :failure="resendFailure" class="mt-4"/>

    <Alert v-if="resendSuccess" variant="success" class="mt-4">
      {{ resendSuccess }}
      <a
          class="ml-2 underline cursor-pointer"
          role="button"
          tabindex="0"
          @click="resendSuccess = ''"
          @keydown.enter.prevent="resendSuccess = ''"
          @keydown.space.prevent="resendSuccess = ''"
      >{{ t('common.close') }}</a>
    </Alert>
  </ViewContent>
</template>
