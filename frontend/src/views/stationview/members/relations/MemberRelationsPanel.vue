/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RelationSection from './RelationSection.vue'
import type { ProfileQuestion } from '@/api/profileFields'
import type { MemberWithName } from '@/api/generated/schema'

/**
 * Who a member is linked to, both ways round, on whichever page has them open.
 *
 * <p>The two ends are the same link and are shown the same way: the guardians looking after this
 * member, and the members this one looks after. Which of the two appears follows from the kind of
 * member, because asking a child who they look after says nothing and neither does asking a
 * guardian who looks after them.
 */
const props = defineProps<{
  /** Whether this member is the kind that can be given a guardian. */
  showGuardians: boolean
  /** Whether this member is the kind that looks after others. */
  showManaged: boolean
  managers: MemberWithName[]
  managedMembers: MemberWithName[]
  availableManagers: MemberWithName[]
  availableManaged: MemberWithName[]
  fields: ProfileQuestion[]
  canEdit: boolean
  memberDisplayName: (m: MemberWithName) => string
  getManagerFields: (id: number) => ProfileQuestion[]
  getManagerFieldValue: (mgrId: number, fieldId: number) => unknown
}>()

const emit = defineEmits<{
  linkManager: [id: number]
  removeManager: [id: number]
  reorderManagers: [fromIndex: number, toIndex: number]
  createManager: [data: { firstName: string; lastName: string; email: string; sendSetupMail: boolean }]
  linkManaged: [id: number]
  removeManaged: [id: number]
}>()

const { t } = useI18n()
const router = useRouter()

const guardianLabels = computed(() => ({
  title: t('memberDetail.managers'),
  empty: t('memberDetail.noManagers'),
  link: t('memberDetail.linkManager'),
  select: t('memberDetail.selectManager'),
  selectPlaceholder: t('memberDetail.selectManagerPlaceholder'),
  assign: t('memberDetail.assign'),
  create: t('memberDetail.createManager'),
  createTitle: t('memberDetail.createManagerTitle'),
  createSubmit: t('memberDetail.createManagerSubmit'),
}))

const managedLabels = computed(() => ({
  title: t('memberDetail.managedMembers'),
  empty: t('memberDetail.noManagedMembers'),
  link: t('memberDetail.linkManaged'),
  select: t('memberDetail.selectManaged'),
  selectPlaceholder: t('memberDetail.selectManagedPlaceholder'),
  assign: t('memberDetail.assign'),
}))

function openMember(id: number) {
  router.push({ name: 'members-edit', params: { id } })
}

const nothingToShow = computed(() => !props.showGuardians && !props.showManaged)
</script>

<template>
  <div class="space-y-6">
    <RelationSection
        v-if="showGuardians"
        :labels="guardianLabels"
        :people="managers"
        :available="availableManagers"
        :fields="fields"
        :readonly="!canEdit"
        allow-create
        orderable
        row-testid="guardian-row"
        :display-name="memberDisplayName"
        :fields-for="getManagerFields"
        :field-value="getManagerFieldValue"
        @link="emit('linkManager', $event)"
        @remove="emit('removeManager', $event)"
        @reorder="(from, to) => emit('reorderManagers', from, to)"
        @create="emit('createManager', $event)"
        @edit="openMember"
    />

    <RelationSection
        v-if="showManaged"
        :labels="managedLabels"
        :people="managedMembers"
        :available="availableManaged"
        :fields="fields"
        :readonly="!canEdit"
        row-testid="managed-member-row"
        :display-name="memberDisplayName"
        :fields-for="getManagerFields"
        :field-value="getManagerFieldValue"
        @link="emit('linkManaged', $event)"
        @remove="emit('removeManaged', $event)"
        @edit="openMember"
    />

    <NeutralContainer v-if="nothingToShow" class="space-y-3">
      <MutedText tag="div" size="sm">{{ t('memberDetail.noRelations') }}</MutedText>
    </NeutralContainer>
  </div>
</template>
