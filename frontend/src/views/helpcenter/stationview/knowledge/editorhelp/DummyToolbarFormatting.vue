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
  <DummyToolbarRow label="Formatierung:">
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'bold']" class="w-3 h-3" /> Fett
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'italic']" class="w-3 h-3" /> Kursiv
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'underline']" class="w-3 h-3" /> Unterstrichen
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'strikethrough']" class="w-3 h-3" /> Durchgestrichen
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'code']" class="w-3 h-3" /> Code
    </KeyBadge>
  </DummyToolbarRow>
</template>
