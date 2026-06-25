/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DummyToolbarFormatting from '@/views/helpcenter/stationview/knowledge/editorhelp/DummyToolbarFormatting.vue'
import DummyToolbarStructure from '@/views/helpcenter/stationview/knowledge/editorhelp/DummyToolbarStructure.vue'
import DummyToolbarMedia from '@/views/helpcenter/stationview/knowledge/editorhelp/DummyToolbarMedia.vue'
</script>

<template>
  <NeutralContainer>
    <div class="space-y-2 text-sm">
      <DummyToolbarFormatting />
      <DummyToolbarStructure />
      <DummyToolbarMedia />
    </div>
  </NeutralContainer>
</template>
