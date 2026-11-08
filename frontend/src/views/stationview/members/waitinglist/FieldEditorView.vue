/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {WaitingList} from '@/api/generated/schema'
import {waitingList} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmAction} from '@/composables/useConfirmAction'
import FieldsList from './fieldeditorview/FieldsList.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {describeFailure} from '@/util/failure'
import {draftOf, requestOf, type WaitingListFieldDraft} from './fieldeditorview/waitingListFieldDraft'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const fields = ref<WaitingListFieldDraft[]>([])

/**
 * The list's name before the word, because every waiting list has its own questions and "Felder"
 * alone is true of all of them. The static wording stands until the list has arrived, and where it
 * could not be fetched at all.
 */
const pageTitle = computed(() => (list.value
  ? t('pages.waiting-list-fields.titleNamed', {name: list.value.name})
  : t('pages.waiting-list-fields.title')))

/** The fields as the list keeps them, in the order they are asked. */
async function loadFields() {
  const loaded = await waitingList.listFields(listId.value)
  fields.value = [...loaded].sort((a, b) => a.position - b.position).map(draftOf)
}

const {loading, failure} = useAsyncLoader(async () => {
  const [listData] = await Promise.all([
    waitingList.getById(listId.value),
    loadFields(),
  ])
  list.value = listData
})

/**
 * Writes every field as it stands in the list, new ones included, each at the place it stands at.
 *
 * <p>Every field is written, because the order is a position on each of them and moving one moves
 * the others along. The list is then read back, so a field written for the first time carries its id
 * and is updated rather than created again on the next save.
 */
async function saveFields() {
  failure.value = null
  try {
    for (const [position, draft] of fields.value.entries()) {
      const request = requestOf(draft, position)
      if (draft.id != null) {
        await waitingList.updateField(listId.value, draft.id, request)
      } else {
        await waitingList.createField(listId.value, request)
      }
    }
    await loadFields()
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
}

const {
  show: showDeleteModal,
  target: deleteTarget,
  request: requestDelete,
  confirm: confirmDelete,
} = useConfirmAction<WaitingListFieldDraft>({
  onConfirm: field => waitingList.deleteField(listId.value, field.id!),
  onSuccess: field => {
    fields.value = fields.value.filter(existing => existing !== field)
  },
  failure,
})

/**
 * Takes a field out of the list. One that was never saved just goes; one with answers behind it
 * loses them, which is asked about first.
 */
function deleteField(index: number) {
  const field = fields.value[index]
  if (!field) return
  if (field.id == null) {
    fields.value = fields.value.filter((_, i) => i !== index)
    return
  }
  requestDelete(field)
}

function goBack() {
  router.push({name: 'waiting-list-detail', params: {id: listId.value}})
}

</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.waiting-list-fields.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('waitingList.backToList') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <FieldsList
          v-if="!loading && list"
          v-model:fields="fields"
          :list-name="list.name"
          :save-fields="saveFields"
          @delete="deleteField"
      />

      <ConfirmDeleteModal
          v-model="showDeleteModal"
          :title="t('waitingList.deleteFieldTitle')"
          :message="t('waitingList.deleteFieldConfirm', {name: deleteTarget?.name})"
          @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
