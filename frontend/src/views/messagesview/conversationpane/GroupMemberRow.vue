/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import IconButton from '@/components/button/IconButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {ConversationMemberResponse} from '@/api/generated/schema'
import {personName} from '@/util/conversationTitle'

/** One person of a group in its member list, with the owner marked and, for the owner, a way to remove them. */
defineProps<{
  member: ConversationMemberResponse
  owner: boolean
  removable: boolean
}>()

defineEmits<{
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <li class="flex items-center gap-3 py-2" :data-testid="`group-member-${member.uid}`">
    <UserAvatar :identity="{accountUid: member.uid}" :name="personName(member)" size="sm"/>
    <span class="min-w-0 flex-1 truncate text-sm">{{ personName(member) }}</span>
    <AppIcon v-if="owner" :icon="['fas', 'crown']" :title="t('messages.members.owner')" class="h-3 w-3 text-primary"/>
    <IconButton v-if="removable" :icon="['fas', 'user-minus']" :label="t('messages.members.remove')"
                class="text-error hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="$emit('remove')"/>
  </li>
</template>
