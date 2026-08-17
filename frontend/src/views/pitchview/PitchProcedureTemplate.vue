/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplateItemCard from '@/views/stationview/procedure/proceduretemplateeditview/TemplateItemCard.vue'
import type {PitchProcedureTemplate} from './pitchTypes'

/** The steps of a template, drawn by the application's own template cards. */
const props = defineProps<{
  template: PitchProcedureTemplate
}>()

const dependencies = (id: number) => props.template.dependencies[id] ?? []
</script>

<template>
  <div class="space-y-2">
    <TemplateItemCard
        v-for="(item, index) in template.items" :key="item.id"
        :item="item" :index="index" :can-manage="true"
        :dependencies="dependencies(item.id)" :get-item-by-id="template.getItemById"/>
  </div>
</template>
