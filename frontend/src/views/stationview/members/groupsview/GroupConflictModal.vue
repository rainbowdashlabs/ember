/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BulletList from '@/components/typography/BulletList.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {BINDING_EXCLUDES_MEMBERS, type GroupConflict} from '@/util/groupRules'

/**
 * The members a change to a group was refused for, each opening their own page.
 *
 * <p>Where the group's new binding leaves them out, the manager can take them out of the group and
 * save in one go. Where they would be in two groups of a set, there is no safe way to choose for them,
 * so the list is all there is: the manager opens each member and decides.
 */
const props = defineProps<{
  conflict: GroupConflict | null
}>()

const emit = defineEmits<{
  close: []
  removeAndSave: []
}>()

const {t} = useI18n()

const canRemove = computed(() => props.conflict?.code === BINDING_EXCLUDES_MEMBERS)
</script>

<template>
  <Modal :model-value="conflict !== null" @update:model-value="open => { if (!open) emit('close') }">
    <div class="space-y-4">
      <SubHeader>{{ canRemove ? t('memberGroups.conflicts.bindingTitle') : t('memberGroups.conflicts.setTitle') }}</SubHeader>
      <p class="text-sm">{{ canRemove ? t('memberGroups.conflicts.bindingText') : t('memberGroups.conflicts.setText') }}</p>
      <BulletList>
        <li v-for="entry in conflict?.conflicts ?? []" :key="entry.memberId">
          <NuxtLink :to="{name: 'members-edit', params: {id: entry.memberId}}" class="text-primary hover:underline">
            {{ entry.memberName }}
          </NuxtLink>
          <span class="text-(--text-muted)">: {{ entry.groups.join(', ') }}</span>
        </li>
      </BulletList>
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('close')">{{ canRemove ? t('common.cancel') : t('common.close') }}</SecondaryButton>
        <ErrorButton v-if="canRemove" @click="emit('removeAndSave')">{{ t('memberGroups.conflicts.removeAndSave') }}</ErrorButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
