/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DisplayTagBadge from '@/components/badge/DisplayTagBadge.vue'
import type {MemberIdentity} from '@/api/generated/schema'

/**
 * A name the way a station shows it: in the color of the person's group and followed by their tag.
 * Without either it is the plain name. The slot is the name; `after` takes whatever else stands
 * behind the tag, such as the badge of another station. This is the one place a name gets its
 * color, so the member lists and the chats cannot drift apart.
 */
withDefaults(defineProps<{
  nameColor?: string | null
  displayTag?: MemberIdentity['displayTag'] | null
  testid?: string
}>(), {
  nameColor: null,
  displayTag: null,
  testid: 'member-name',
})
</script>

<template>
  <span class="inline-flex min-w-0 items-center gap-1.5">
    <span :data-testid="testid" class="truncate" :style="nameColor ? {color: nameColor} : {}"><slot/></span>
    <DisplayTagBadge v-if="displayTag" :tag="displayTag"/>
    <slot name="after"/>
  </span>
</template>
