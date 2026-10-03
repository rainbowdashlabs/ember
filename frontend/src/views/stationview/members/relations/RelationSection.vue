/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DragList from '@/components/input/DragList.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SingleSelectDropdown from '@/components/input/select/SingleSelectDropdown.vue'
import SetupMailChoice from '@/components/input/toggle/SetupMailChoice.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RelationRow from './RelationRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { useI18n } from 'vue-i18n'
import type { ProfileQuestion } from '@/api/profileFields'
import type { MemberWithName } from '@/api/generated/schema'

/**
 * The wording one section carries. Both halves of the relation read the same way and only the
 * words differ, so they are handed in rather than chosen here: this component knows it lists
 * people who are linked to somebody, not which side of the link it is showing.
 *
 * <p>The three creation strings are only read where inviting somebody is offered at all.
 */
export interface RelationLabels {
  title: string
  empty: string
  link: string
  select: string
  selectPlaceholder: string
  assign: string
  create?: string
  createTitle?: string
  createSubmit?: string
  firstName?: string
  lastName?: string
  email?: string
}

const props = withDefaults(defineProps<{
  labels: RelationLabels
  people: MemberWithName[]
  available: MemberWithName[]
  fields: ProfileQuestion[]
  readonly?: boolean
  /** Whether somebody who is not a member yet can be invited straight into this relation. */
  allowCreate?: boolean
  /**
   * Whether the order of the people means something and the reader may set it, which is the case
   * for guardians: the first one is guardian 1 on every document.
   */
  orderable?: boolean
  rowTestid?: string
  displayName: (m: MemberWithName) => string
  fieldsFor: (id: number) => ProfileQuestion[]
  fieldValue: (id: number, fieldId: number) => unknown
}>(), {
  readonly: false,
  allowCreate: false,
  orderable: false,
  rowTestid: undefined,
})

const emit = defineEmits<{
  link: [id: number]
  remove: [id: number]
  edit: [id: number]
  reorder: [fromIndex: number, toIndex: number]
  create: [data: { firstName: string; lastName: string; email: string; sendSetupMail: boolean }]
}>()

function placeOf(index: number): string | undefined {
  return props.people.length > 1 ? t('memberDetail.guardianPlace', {place: index + 1}) : undefined
}

const { t } = useI18n()

const showLink = ref(false)
const selectedId = ref('')

const showCreate = ref(false)
const newFirstName = ref('')
const newLastName = ref('')
const newEmail = ref('')
const sendSetupMail = ref(true)

const options = computed(() =>
    props.available.map(m => ({value: String(m.id), label: props.displayName(m)})),
)

function doLink() {
  if (!selectedId.value) return
  emit('link', Number(selectedId.value))
  showLink.value = false
  selectedId.value = ''
}

function doCreate() {
  if (!newFirstName.value || !newLastName.value || !newEmail.value) return
  emit('create', {
    firstName: newFirstName.value,
    lastName: newLastName.value,
    email: newEmail.value,
    sendSetupMail: sendSetupMail.value,
  })
  showCreate.value = false
  newFirstName.value = ''
  newLastName.value = ''
  newEmail.value = ''
  sendSetupMail.value = true
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader class="text-sm">{{ labels.title }}</SubHeader>
      <ButtonRow v-if="!readonly" align="end">
        <SecondaryButton :icon="['fas', 'link']" @click="showLink = !showLink">
          {{ labels.link }}
        </SecondaryButton>
        <SecondaryButton v-if="allowCreate" :icon="['fas', 'plus']" @click="showCreate = !showCreate">
          {{ labels.create }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <MutedText v-if="people.length === 0" tag="div" size="sm" class="py-2">
      {{ labels.empty }}
    </MutedText>

    <DragList
        v-if="orderable"
        :items="people"
        :key-fn="person => person.id"
        :disabled="readonly"
        class="space-y-3"
        @reorder="(from, to) => emit('reorder', from, to)"
    >
      <template #default="{item: person, index}">
        <RelationRow
            :person="person"
            :readonly="readonly"
            :place="placeOf(index)"
            :row-testid="rowTestid"
            :display-name="displayName"
            :fields="fieldsFor(person.id)"
            :field-value="fieldId => fieldValue(person.id, fieldId)"
            @edit="emit('edit', $event)"
            @remove="emit('remove', $event)"
        />
      </template>
    </DragList>
    <div v-else class="space-y-3">
      <RelationRow
          v-for="person in people"
          :key="person.id"
          :person="person"
          :readonly="readonly"
          :row-testid="rowTestid"
          :display-name="displayName"
          :fields="fieldsFor(person.id)"
          :field-value="fieldId => fieldValue(person.id, fieldId)"
          @edit="emit('edit', $event)"
          @remove="emit('remove', $event)"
      />
    </div>

    <div v-if="!readonly && showLink" class="space-y-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <FieldLabel>{{ labels.select }}</FieldLabel>
      <div class="flex gap-2">
        <SingleSelectDropdown
            :options="options"
            :model-value="selectedId"
            :placeholder="labels.selectPlaceholder"
            searchable
            clearable
            @update:model-value="selectedId = $event"
        />
        <PrimaryButton :disabled="!selectedId" @click="doLink">
          {{ labels.assign }}
        </PrimaryButton>
      </div>
    </div>

    <div v-if="!readonly && allowCreate && showCreate" class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <FieldLabel>{{ labels.createTitle }}</FieldLabel>
      <div class="grid gap-3 sm:grid-cols-3">
        <TextInput v-model="newFirstName" :placeholder="labels.firstName ?? t('memberDetail.firstName')" />
        <TextInput v-model="newLastName" :placeholder="labels.lastName ?? t('memberDetail.lastName')" />
        <TextInput v-model="newEmail" :placeholder="labels.email ?? t('memberDetail.email')" />
      </div>
      <SetupMailChoice v-model="sendSetupMail"/>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="!newFirstName || !newLastName || !newEmail" @click="doCreate">
        {{ labels.createSubmit }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
