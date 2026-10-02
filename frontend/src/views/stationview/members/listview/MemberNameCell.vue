/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberSetupIndicator from './MemberSetupIndicator.vue'
import {useMemberRowExtras} from './memberRowExtras'
import type {RosterMember} from './useMemberData'

/**
 * A member's name in the list, with whatever the list has to say beside it: a note, a profile that
 * leaves a required question open, and an account nobody has set up yet.
 */
const props = defineProps<{
  member: RosterMember
  canEdit: boolean
}>()

const emit = defineEmits<{
  resendSetup: []
}>()

const {t} = useI18n()
const extras = useMemberRowExtras()
const rowNote = computed(() => extras.note(props.member.id))
</script>

<template>
  <div class="flex items-center gap-2">
    <MemberName :identity="member.identity" size="sm" class="font-medium"/>
    <MutedText v-if="rowNote" data-testid="member-note" size="sm">{{ rowNote }}</MutedText>
    <ErrorBadge v-if="member.profileComplete === false" data-testid="member-incomplete" class="ml-1.5 text-[10px]">{{ t('membersList.incomplete') }}</ErrorBadge>
    <MemberSetupIndicator :member="member" :can-edit="canEdit" @resend-setup="emit('resendSetup')"/>
  </div>
</template>
