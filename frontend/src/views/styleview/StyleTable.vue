/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import DiffView from '@/components/display/DiffView.vue'

const samplePatch = `--- a/notes.md
+++ b/notes.md
@@ -1,5 +1,6 @@
 # Meeting Notes
-Date: 2026-05-20
+Date: 2026-05-26
+Status: Final

-Action items are pending.
+All action items completed.
 Next meeting in two weeks.`
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Table</SectionHeader>
    <NeutralContainer :padded="false" class="overflow-x-auto">
      <table class="w-full text-sm">
        <THead>
          <Th>Name</Th>
          <Th>PermissionGrant</Th>
          <Th align="right">Score</Th>
        </THead>
        <tbody>
          <TRow><Td>Alice</Td><Td>Admin</Td><Td align="right">95</Td></TRow>
          <TRow><Td>Bob</Td><Td muted>Member</Td><Td align="right">72</Td></TRow>
          <TRow><Td>Charlie</Td><Td muted>Guest</Td><Td align="right" muted>—</Td></TRow>
        </tbody>
      </table>
    </NeutralContainer>
  </section>

  <section>
    <SectionHeader>Diff View</SectionHeader>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <NeutralContainer class="!p-0 overflow-hidden">
        <p class="px-3 py-2 text-sm font-medium border-b border-(--border)">Compact (no line numbers)</p>
        <DiffView :patch="samplePatch" compact/>
      </NeutralContainer>
      <NeutralContainer class="!p-0 overflow-hidden">
        <p class="px-3 py-2 text-sm font-medium border-b border-(--border)">With line numbers</p>
        <DiffView :patch="samplePatch" show-line-numbers/>
      </NeutralContainer>
    </div>
  </section>
</template>
