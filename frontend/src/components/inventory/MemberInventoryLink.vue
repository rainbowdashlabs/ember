/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MemberName from '@/components/avatar/MemberName.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import type {MemberIdentity} from '@/api/types'

/**
 * A member's name in the inventory area, which leads to what they are holding.
 *
 * <p>Every list here names people for one reason: to ask what else is on them. Written as plain text
 * that question costs a trip through the member list, and each screen that solved it wrote its own
 * link, so some of them did not.
 *
 * <p>A name that leads somewhere is painted like everything else that leads somewhere, and a name
 * carrying its group's colour keeps that colour: the group is the one thing about a person a list is
 * allowed to say in colour, and it outranks the link.
 */
const props = defineProps<{
  identity?: MemberIdentity | null
  /** The member this name belongs to, which is what the link needs. Absent means plain text. */
  memberId?: number | null
}>()

const routes = useInventoryRoutes()
</script>

<template>
  <router-link
      v-if="props.memberId && routes.member"
      :to="{name: routes.member, params: {memberId: props.memberId}}"
      class="inline-block font-medium hover:underline"
      @click.stop
  >
    <MemberName :identity="props.identity"/>
  </router-link>
  <MemberName v-else :identity="props.identity"/>
</template>
