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
  <DummyToolbarRow label="Struktur:">
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'heading']" class="w-3 h-3" /><span class="text-[10px] font-bold">1</span>
    </KeyBadge> <span class="text-[10px] font-bold">2</span> <span class="text-[10px] font-bold">3</span> Überschriften
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'list-ul']" class="w-3 h-3" /> Aufzählung
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'list-ol']" class="w-3 h-3" /> Nummeriert
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'quote-left']" class="w-3 h-3" /> Zitat
    </KeyBadge>
  </DummyToolbarRow>
</template>
