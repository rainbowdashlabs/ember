/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MemberTableModal from '@/components/membertable/MemberTableModal.vue'
import {memberTable as api} from '@/api'
import type {MemberTableColumn, MemberTableHeader} from '@/api/memberTable'

const {t} = useI18n()

const props = defineProps<{
  eventId: number
  /** The single evening the table is about, because a registration belongs to one. */
  effectiveDate: string | null
}>()

const showTable = ref(false)
const offered = ref<MemberTableHeader[]>([])

/**
 * What may go on the table, asked for when the button is pressed.
 *
 * <p>Asked of the server rather than worked out here: the station's questions and the appointment's
 * both come back already cut to what this reader may read, so the picker cannot offer a column the
 * table would then drop.
 */
async function open() {
  const columns = await api.listRegistrationColumns(props.eventId)
  offered.value = [
    ...columns.member,
    ...columns.questions.map(question => ({
      label: question.label,
      kind: 'REGISTRATION_FIELD' as const,
      key: null,
      fieldId: question.fieldId,
    })),
  ]
  showTable.value = true
}

function day(): string {
  return props.effectiveDate ?? new Date().toISOString().slice(0, 10)
}

function draw(columns: MemberTableColumn[]) {
  return api.drawRegistrationTable(props.eventId, day(), columns)
}

function download(columns: MemberTableColumn[], format: 'csv' | 'pdf') {
  return api.exportRegistrationTable(props.eventId, day(), columns, format)
}
</script>

<template>
  <SecondaryButton :icon="['fas', 'table-list']" compact data-testid="open-registration-table" @click="open">
    {{ t('memberTable.open') }}
  </SecondaryButton>

  <MemberTableModal
      v-model="showTable"
      :offered="offered"
      :draw="draw"
      :download="download"
      file-name="anmeldungen"
  />
</template>
