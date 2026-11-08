/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {moveWithin} from '@/util/reorder'
import FieldEditor from './FieldEditor.vue'
import {blankDraft, type WaitingListFieldDraft} from './waitingListFieldDraft'

/**
 * The fields of a waiting list, each edited where it stands, as the questions of an appointment are.
 *
 * <p>They were a list of names with a dialog behind each and a pair of arrows that wrote every move
 * at once. Now the list is the editor: a field is written in its row and dragged to another place,
 * and the whole list is saved at once.
 */
const fields = defineModel<WaitingListFieldDraft[]>('fields', {required: true})

const props = defineProps<{
  listName: string
  /** Writes every field as it stands; the button shows saving and saved. */
  saveFields: () => Promise<void>
}>()

const emit = defineEmits<{
  delete: [index: number]
}>()

const {t} = useI18n()

const canSave = computed(() => fields.value.every(field => field.name.trim() !== ''))

function addField() {
  fields.value = [...fields.value, blankDraft()]
}

function updateField(index: number, field: WaitingListFieldDraft) {
  fields.value = fields.value.map((existing, i) => (i === index ? field : existing))
}

function moveField(fromIndex: number, toIndex: number) {
  fields.value = moveWithin(fields.value, fromIndex, toIndex)
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="waiting-list-fields">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('waitingList.fields') }} - {{ listName }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="addField">
        {{ t('waitingList.addField') }}
      </SecondaryButton>
    </div>

    <p class="text-sm text-(--text-muted)">{{ t('waitingList.fieldsHint') }}</p>

    <EmptyState v-if="fields.length === 0" compact>{{ t('waitingList.noFields') }}</EmptyState>

    <DragList :items="fields" :key-fn="(field, index) => field.id ?? `new-${index}`" @reorder="moveField">
      <template #default="{index}">
        <div class="mb-2" data-testid="waiting-list-field-row">
          <FieldEditor
              :model-value="fields[index]!"
              @update:model-value="updateField(index, $event)"
              @remove="emit('delete', index)"
          />
        </div>
      </template>
    </DragList>

    <SaveButton :action="props.saveFields" :disabled="!canSave">
      {{ t('waitingList.saveFields') }}
    </SaveButton>
  </NeutralContainer>
</template>
