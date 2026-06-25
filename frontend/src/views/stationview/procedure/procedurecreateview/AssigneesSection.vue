/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberIdentity} from '@/api/types'

const {t} = useI18n()

const assigneePickerValue = defineModel<string>('assigneePickerValue', {required: true})

const props = defineProps<{
  members: MemberCompletion[]
  selectedAssignees: MemberCompletion[]
  selectedAssigneeIds: number[]
}>()

const emit = defineEmits<{
  add: []
  remove: [id: number]
}>()

const availableMembers = computed(() =>
    props.members.filter(m => !props.selectedAssigneeIds.includes(m.id))
)

function toIdentity(m: MemberCompletion): MemberIdentity {
  return {
    stationUid: m.stationUid,
    memberUid: m.memberUid,
    name: m.name,
    nameColor: m.nameColor ?? null,
    displayTag: m.displayTag ?? null,
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('procedures.assignees') }}</SubHeader>
    <div class="flex gap-2">
      <div class="flex-1">
        <MemberSelectInput
            v-model="assigneePickerValue"
            :members="availableMembers"
            :placeholder="t('procedures.selectAssignee')"
        />
      </div>
      <PrimaryButton :disabled="!assigneePickerValue" @click="emit('add')">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/> {{ t('common.add') }}
      </PrimaryButton>
    </div>
    <div v-if="selectedAssignees.length > 0" class="flex flex-wrap gap-2">
      <div v-for="a in selectedAssignees" :key="a.id"
           class="flex items-center gap-1 bg-(--bg-light-accent) dark:bg-(--bg-dark-accent) rounded-full px-3 py-1">
        <MemberName :identity="toIdentity(a)" size="sm"/>
        <IconButton :icon="['fas', 'xmark']" :label="t('common.remove')" class="!p-0 ml-1"
                    @click="emit('remove', a.id)"/>
      </div>
    </div>
    <MutedText v-else size="sm">{{ t('procedures.noAssignees') }}</MutedText>
  </NeutralContainer>
</template>
