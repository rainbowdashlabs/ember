/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import KeyBadge from '@/components/display/KeyBadge.vue'
import DummyToolbarRow from '@/views/helpcenter/stationview/knowledge/editorhelp/DummyToolbarRow.vue'
</script>

<template>
  <DummyToolbarRow label="Medien:">
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'link']" class="w-3 h-3" /> Link
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'table-columns']" class="w-3 h-3" /> Tabelle
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'highlighter']" class="w-3 h-3" /> Hervorhebung
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fab', 'youtube']" class="w-3 h-3" /> YouTube
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'image']" class="w-3 h-3" /> Bild
    </KeyBadge>
  </DummyToolbarRow>
</template>
