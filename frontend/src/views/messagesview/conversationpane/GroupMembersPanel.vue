/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SidePanel from '@/components/feedback/SidePanel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {ConversationResponse, ReachablePersonResponse} from '@/api/generated/schema'
import {addPeople, removePerson, renameGroup} from '@/api/messages'
import {describeFailure, type Failure} from '@/util/failure'
import PersonPicker from '../PersonPicker.vue'
import GroupMemberRow from './GroupMemberRow.vue'

/**
 * The people of a group, at the right. Everybody in it adds people they could write to themselves;
 * the owner also renames the group and removes people.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  conversation: ConversationResponse
  readerUid: string | null
  onChanged: () => Promise<void>
}>()

const {t} = useI18n()
const name = ref('')
const adding = ref<ReachablePersonResponse[]>([])
const failure = ref<Failure | null>(null)

const owner = computed(() => props.conversation.ownerUid !== null && props.conversation.ownerUid === props.readerUid)
const memberUids = computed(() => props.conversation.members.map(member => member.uid))

watch(open, isOpen => {
  if (!isOpen) return
  name.value = props.conversation.name ?? ''
  adding.value = []
  failure.value = null
})

async function act(change: () => Promise<void>) {
  failure.value = null
  try {
    await change()
    await props.onChanged()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function rename() {
  await act(() => renameGroup(props.conversation.uid, name.value.trim() || null))
}

async function add() {
  await act(() => addPeople(props.conversation.uid, adding.value.map(person => person.uid)))
  adding.value = []
}
</script>

<template>
  <SidePanel v-model="open">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('messages.members.title') }}</SubHeader>
      <FailureAlert :failure="failure"/>
      <ul class="divide-y divide-(--border)">
        <GroupMemberRow v-for="member in conversation.members" :key="member.uid" :member="member"
                        :owner="member.uid === conversation.ownerUid"
                        :removable="owner && member.uid !== readerUid"
                        @remove="act(() => removePerson(conversation.uid, member.uid))"/>
      </ul>
      <div v-if="owner" class="space-y-2">
        <FieldLabel>{{ t('messages.members.rename') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('messages.newConversation.groupName')"/>
        <SaveButton :action="rename"/>
      </div>
      <div class="space-y-2">
        <FieldLabel>{{ t('messages.members.add') }}</FieldLabel>
        <PersonPicker v-model="adding" :exclude="memberUids"/>
        <PrimaryButton :disabled="adding.length === 0" :icon="['fas', 'user-plus']" @click="add">
          {{ t('messages.members.addChosen') }}
        </PrimaryButton>
      </div>
    </div>
  </SidePanel>
</template>
