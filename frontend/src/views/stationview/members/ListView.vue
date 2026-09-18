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
import ButtonRow from '@/components/button/ButtonRow.vue'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, type StationMember} from '@/api/types'
import { STATION_MEMBER_SOURCE } from './listview/useMemberData'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useSession } from '@/composables/useSession'
import { useMemberListConfig, type MemberListPort } from './listview/useMemberListConfig'
import { stationMembers, memberTable } from '@/api'
import type { MemberTableColumn, MemberTableHeader } from '@/api/memberTable'
import MemberTableModal from '@/components/membertable/MemberTableModal.vue'

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

/**
 * The register as a table of chosen columns.
 *
 * <p>Which people is this screen's answer, because it is already showing them and the reader was
 * already allowed to see them. Which columns is not: the server works that out from the reader's own
 * permissions, so a question they may not read never reaches the sheet however it is asked for.
 */
const showTable = ref(false)
const tableColumns = ref<MemberTableHeader[]>([])

async function openTable() {
  tableColumns.value = await memberTable.listColumns()
  showTable.value = true
}

function shownMemberIds(): number[] {
  return config.sortedMembers.value.map(member => member.id)
}

function drawTable(columns: MemberTableColumn[]) {
  return memberTable.drawMemberTable(shownMemberIds(), columns)
}

function downloadTable(columns: MemberTableColumn[], format: 'csv' | 'pdf') {
  return memberTable.exportMemberTable(shownMemberIds(), columns, format)
}

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
    <ButtonRow v-if="config.canExport.value" align="end" class="mb-2">
      <SecondaryButton :icon="['fas', 'table-list']" compact data-testid="open-member-table" @click="openTable">
        {{ t('memberTable.open') }}
      </SecondaryButton>
    </ButtonRow>

    <MemberListPanel :config="config" @resend-setup="openResendSetup"/>

    <MemberTableModal
        v-model="showTable"
        :offered="tableColumns"
        :draw="drawTable"
        :download="downloadTable"
        file-name="mitglieder"
    />

    <Modal v-if="resendTarget" model-value @update:model-value="(v) => { if (!v) resendTarget = null }">
      <div class="space-y-4">
        <p>{{ t('membersList.resendConfirm', {name: resendTarget?.name ?? ''}) }}</p>
        <Alert v-if="resendError" variant="error">{{ resendError }}</Alert>
        <ButtonRow align="end">
          <SecondaryButton :disabled="resending" @click="resendTarget = null">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']" :disabled="resending" @click="confirmResendSetup">
            {{ t('membersList.resendAction') }}
          </PrimaryButton>
        </ButtonRow>
      </div>
    </Modal>

    <Alert v-if="resendSuccess" variant="success" class="mt-4">
      {{ resendSuccess }}
      <a class="ml-2 underline cursor-pointer" @click="resendSuccess = ''">{{ t('common.close') }}</a>
    </Alert>
  </ViewContent>
</template>
