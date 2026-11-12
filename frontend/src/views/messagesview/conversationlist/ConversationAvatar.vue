/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import type {MessagePersonResponse} from '@/api/generated/schema'
import {personName} from '@/util/conversationTitle'

/**
 * Who a conversation is with, as a picture: the other person's avatar, or two of a group's stacked.
 */
const props = defineProps<{
  people: readonly MessagePersonResponse[]
}>()

const shown = computed(() => props.people.slice(0, 2))
</script>

<template>
  <div class="relative h-9 w-9 shrink-0">
    <UserAvatar v-if="shown.length <= 1" :identity="shown[0] ? {accountUid: shown[0].uid} : null"
                :name="shown[0] ? personName(shown[0]) : undefined" class="h-9 w-9"/>
    <template v-else>
      <UserAvatar v-for="(person, index) in shown" :key="person.uid" :identity="{accountUid: person.uid}"
                  :name="personName(person)" size="sm"
                  :class="index === 0 ? 'absolute left-0 top-0' : 'absolute bottom-0 right-0 ring-2 ring-(--bg)'"/>
    </template>
  </div>
</template>
