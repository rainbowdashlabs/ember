/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

const {t} = useI18n()
</script>

<template>
  <tbody>
    <TRow>
      <Td class="font-medium">Helm</Td>
      <Td muted>INV-0001</Td>
      <Td muted>M</Td>
      <Td><span class="text-primary font-medium">Max Mustermann</span></Td>
      <Td align="right">
        <div class="flex items-center justify-end gap-0.5">
          <EditButton />
          <DeleteButton />
        </div>
      </Td>
    </TRow>
    <TRow>
      <Td class="font-medium">Helm</Td>
      <Td muted>INV-0002</Td>
      <Td muted>L</Td>
      <Td><span class="text-(--text-muted)">&ndash;</span></Td>
      <Td align="right">
        <div class="flex items-center justify-end gap-0.5">
          <EditButton />
          <DeleteButton />
        </div>
      </Td>
    </TRow>
    <TRow class="opacity-60">
      <Td class="font-medium">
        Helm <span class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} (01.05.2026)</span>
      </Td>
      <Td muted>INV-0003</Td>
      <Td muted>S</Td>
      <Td><span class="text-primary font-medium">Erika Musterfrau</span></Td>
      <Td align="right">
        <div class="flex items-center justify-end gap-0.5">
          <EditButton />
          <DeleteButton />
        </div>
      </Td>
    </TRow>
  </tbody>
</template>
