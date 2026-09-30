/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SingleFieldModal from '@/components/feedback/SingleFieldModal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberGroupSet} from '@/api/groupSets'

/**
 * The sets of groups beside the groups: each one a name, whose groups a member can be in only one of.
 * Which groups belong to a set is chosen on the group itself.
 */
defineProps<{
  sets: MemberGroupSet[]
}>()

const emit = defineEmits<{
  create: [name: string]
  rename: [id: number, name: string]
  delete: [id: number]
}>()

const {t} = useI18n()

const naming = ref(false)
const renaming = ref<MemberGroupSet | null>(null)
const name = ref('')
const deleting = ref<MemberGroupSet | null>(null)
const confirmDelete = ref(false)

function openCreate() {
  renaming.value = null
  name.value = ''
  naming.value = true
}

function openRename(set: MemberGroupSet) {
  renaming.value = set
  name.value = set.name
  naming.value = true
}

function save() {
  if (renaming.value) emit('rename', renaming.value.id, name.value)
  else emit('create', name.value)
  naming.value = false
}

function askDelete(set: MemberGroupSet) {
  deleting.value = set
  confirmDelete.value = true
}

function removeSet() {
  if (deleting.value) emit('delete', deleting.value.id)
  confirmDelete.value = false
}
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ t('memberGroups.sets.title') }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="openCreate">{{ t('memberGroups.sets.create') }}</SecondaryButton>
    </div>
    <MutedText tag="p" size="sm">{{ t('memberGroups.sets.hint') }}</MutedText>
    <MutedText v-if="sets.length === 0" tag="p" size="sm">{{ t('memberGroups.sets.empty') }}</MutedText>
    <NeutralContainer v-for="set in sets" :key="set.id" class="flex items-center justify-between gap-2">
      <span class="font-medium">{{ set.name }}</span>
      <div class="flex items-center gap-2">
        <EditButton @click="openRename(set)"/>
        <DeleteButton @click="askDelete(set)"/>
      </div>
    </NeutralContainer>

    <SingleFieldModal
        v-model:show="naming"
        v-model:value="name"
        :title="renaming ? t('memberGroups.sets.renameTitle') : t('memberGroups.sets.createTitle')"
        :placeholder="t('memberGroups.sets.namePlaceholder')"
        :confirm-label="t('memberGroups.save')"
        @confirm="save"
    />
    <ConfirmDeleteModal v-model="confirmDelete" :message="t('memberGroups.sets.deleteConfirm', {name: deleting?.name})"
                        @confirm="removeSet"/>
  </div>
</template>
