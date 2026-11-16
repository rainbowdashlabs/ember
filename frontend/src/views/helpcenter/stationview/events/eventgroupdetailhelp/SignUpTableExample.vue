/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RegistrationStatusBadge from '@/views/stationview/events/eventshared/RegistrationStatusBadge.vue'
import {useDataTable} from '@/composables/useDataTable'
import {isSignUpComplete, signUpColumns} from '@/views/stationview/events/eventgroupdetailview/eventGroupTables'
import {RegistrationStatus, type EventGroupSignUp} from '@/api/generated/schema'
import {exampleSignUps} from './exampleSignUps'

/** The sign-up list of the event group page as the help shows it: two sign-ups, one still incomplete. */
const {t} = useI18n()

const table = useDataTable<EventGroupSignUp>({
  id: 'help-event-group-sign-ups',
  rows: computed(() => exampleSignUps(t)),
  columns: computed(() => signUpColumns(t)),
  rowKey: entry => entry.memberId,
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <SubHeader>{{ t('eventGroups.signUps') }}</SubHeader>
      <ButtonRow align="end">
        <TableColumnPicker :table="table"/>
        <SecondaryButton :icon="['fas', 'file-export']">{{ t('eventGroups.exportSignUps') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <RecordTable :table="table" test-id="help-event-group-sign-ups" plain>
      <template #cell-status="{row}">
        <RegistrationStatusBadge v-if="row.status" :status="row.status"/>
      </template>
      <template #cell-complete="{row}">
        <SuccessBadge v-if="isSignUpComplete(row)">{{ t('eventGroups.signUpComplete') }}</SuccessBadge>
        <ErrorBadge v-else>{{ t('eventGroups.short') }}</ErrorBadge>
      </template>
      <template #actions="{row}">
        <ButtonRow align="end">
          <SuccessButton v-if="row.status !== RegistrationStatus.ACCEPTED" compact>{{ t('eventGroups.accept') }}</SuccessButton>
          <ErrorButton compact>{{ t('eventGroups.deny') }}</ErrorButton>
        </ButtonRow>
      </template>
    </RecordTable>
  </NeutralContainer>
</template>
