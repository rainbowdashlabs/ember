/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EntryHeaderInfo from './entrydetailview/EntryHeaderInfo.vue'
import EntryCoreFieldsCard from './entrydetailview/EntryCoreFieldsCard.vue'
import EntryCustomFieldsCard from './entrydetailview/EntryCustomFieldsCard.vue'
import type { GuardianInput, WaitingListEntryWithScore, WaitingListField } from '@/api/waitingList'
import { waitingList } from '@/api'
import { setFieldValue as writeFieldValue } from '@/util/profileFields'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { instantToLocalInput } from '@/util/format'
import { describeFailure, FailureKind } from '@/util/failure'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))
const entryId = computed(() => Number(route.params.entryId))

const entry = ref<WaitingListEntryWithScore | null>(null)
const fields = ref<WaitingListField[]>([])

const editFirstname = ref('')
const editLastname = ref('')
const editGuardians = ref<GuardianInput[]>([])
const editNotes = ref('')
const editValues = ref<Map<number, string>>(new Map())
const editingCreatedAt = ref(false)
const editCreatedAtValue = ref('')

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [entries, fieldData] = await Promise.all([
    waitingList.listEntries(listId.value),
    waitingList.listFields(listId.value),
  ])
  const found = entries.find(e => e.entry.id === entryId.value)
  if (!found) {
    failure.value = {
      kind: FailureKind.GONE,
      message: t('waitingList.entryNotFound'),
      guidance: t(`failure.${FailureKind.GONE}.guidance`),
      reportable: false,
    }
    return
  }
  entry.value = found
  fields.value = fieldData

  editFirstname.value = found.entry.firstname
  editLastname.value = found.entry.lastname
  editGuardians.value = found.guardians.length > 0
    ? found.guardians.map(g => ({ firstname: g.firstname, lastname: g.lastname, email: g.email, phone: g.phone }))
    : [{ firstname: '', lastname: '', email: found.entry.email || '', phone: '' }]
  editNotes.value = found.entry.notes ?? ''

  const valMap = new Map<number, string>()
  for (const v of found.values) {
    valMap.set(v.fieldId, v.value == null ? '' : String(v.value))
  }
  editValues.value = valMap
})

function setFieldValue(fieldId: number, value: string) {
  writeFieldValue(editValues, fieldId, value)
}

function addGuardian() {
  editGuardians.value = [...editGuardians.value, { firstname: '', lastname: '', email: '', phone: '' }]
}

function removeGuardian(index: number) {
  editGuardians.value = editGuardians.value.filter((_, i) => i !== index)
}

const canSave = computed(() =>
  editFirstname.value.trim() && editGuardians.value.some(g => g.email.trim()),
)

async function save() {
  if (!canSave.value) return
  failure.value = null
  try {
    const values: Record<number, unknown> = {}
    for (const [fieldId, value] of editValues.value) {
      values[fieldId] = value
    }
    await waitingList.updateEntry(listId.value, entryId.value, {
      firstname: editFirstname.value.trim(),
      lastname: editLastname.value.trim(),
      guardians: editGuardians.value.map(g => ({ firstname: g.firstname.trim(), lastname: g.lastname.trim(), email: g.email.trim(), phone: g.phone.trim() })),
      notes: editNotes.value.trim(),
      values,
    })
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
}

function startEditCreatedAt() {
  if (!entry.value) return
  editCreatedAtValue.value = instantToLocalInput(entry.value.entry.createdAt)
  editingCreatedAt.value = true
}

async function saveCreatedAt() {
  if (!entry.value || !editCreatedAtValue.value) return
  failure.value = null
  try {
    await waitingList.updateCreatedAt(listId.value, entryId.value, new Date(editCreatedAtValue.value).toISOString())
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  editingCreatedAt.value = false
  await reload()
}

const entryFullName = computed(() => {
  if (!entry.value) return ''
  const e = entry.value.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
})

/**
 * Who is waiting, at the head of the page, and nothing besides: what the page does with them is
 * written on the line under it. The static wording stands until the entry has arrived, and where
 * it could not be found at all.
 */
const pageTitle = computed(() => entryFullName.value || t('pages.waiting-list-entry.title'))

function goBack() {
  router.push({ name: 'waiting-list-detail', params: { id: listId.value } })
}

</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.waiting-list-entry.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('waitingList.backToList') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure"/>

      <template v-if="!loading && entry">
        <EntryHeaderInfo
          :entry="entry"
          :entry-full-name="entryFullName"
          v-model:editing-created-at="editingCreatedAt"
          v-model:edit-created-at-value="editCreatedAtValue"
          @start-edit-created-at="startEditCreatedAt"
          @save-created-at="saveCreatedAt"
        />

        <EntryCoreFieldsCard
          v-model:firstname="editFirstname"
          v-model:lastname="editLastname"
          v-model:notes="editNotes"
          :guardians="editGuardians"
          @add-guardian="addGuardian"
          @remove-guardian="removeGuardian"
        />

        <EntryCustomFieldsCard
          :fields="fields"
          :values="editValues"
          @update:value="setFieldValue"
        />

        <div class="flex justify-end">
          <SaveButton :disabled="!canSave" :action="save"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
