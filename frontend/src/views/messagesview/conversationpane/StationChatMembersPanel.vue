/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidePanel from '@/components/feedback/SidePanel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import PersonCardButton from '@/components/avatar/PersonCardButton.vue'
import type {ConversationResponse} from '@/api/generated/schema'
import {personName} from '@/util/conversationTitle'
import ColoredName from '@/components/avatar/ColoredName.vue'

/**
 * The people of a station chat, at the right, to look at only: who is in it follows the audience the
 * station gave it, so nobody adds or removes anybody here. A guardian who is in it for somebody they
 * look after is marked with that person's name. Names carry their group color and tag, as the
 * station shows them.
 */
const open = defineModel<boolean>({required: true})

defineProps<{
  conversation: ConversationResponse
}>()

const {t} = useI18n()

</script>

<template>
  <SidePanel v-model="open">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('messages.members.title') }}</SubHeader>
      <MutedText tag="p" data-testid="station-chat-follows">{{ t('messages.station.follows') }}</MutedText>
      <ul class="divide-y divide-(--border)">
        <li v-for="member in conversation.members" :key="member.uid" class="py-2"
            :data-testid="`station-chat-member-${member.uid}`">
          <PersonCardButton :account-uid="member.uid" :name="personName(member)" class="min-w-0">
            <span class="flex min-w-0 items-center gap-3">
              <UserAvatar :identity="{accountUid: member.uid}" :name="personName(member)" size="sm"/>
              <ColoredName class="text-sm" testid="chat-person-name"
                           :name-color="member.member?.nameColor" :display-tag="member.member?.displayTag">
                {{ personName(member) }}
              </ColoredName>
            </span>
          </PersonCardButton>
          <MutedText v-if="member.guardianFor.length > 0" tag="p" class="pl-10">
            {{ t('messages.station.guardianFor', {names: member.guardianFor.join(', ')}) }}
          </MutedText>
        </li>
      </ul>
    </div>
  </SidePanel>
</template>
