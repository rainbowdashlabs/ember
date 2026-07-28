/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'

defineProps<{
  memberId: number
}>()
</script>

<template>
  <NeutralContainer>
    <NoteEditor :entity-type="'MEMBER'" :entity-id="memberId"/>
  </NeutralContainer>
</template>
