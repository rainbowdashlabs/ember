/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {selfChecks} from '@/api'
import type {SelfCheckSummary} from '@/api/selfChecks'
import {formatDate} from '@/util/format'

/**
 * The gear the reader has been asked to answer for, their own and that of anybody in their care.
 *
 * <p>It is offered here and counted in the badge, and it never stands in the doorway: a task due in
 * four weeks is a thing to get round to, not a wall to meet at every sign-in. Each row is named for
 * whom it is about, because a guardian holds several and only the names tell them apart.
 */
const {t} = useI18n()
const router = useRouter()

const tasks = ref<SelfCheckSummary[]>([])

/**
 * Reads the list, and says nothing where it cannot be read.
 *
 * <p>A tile of the dashboard that fails is one tile missing, not a broken page: the reader came here
 * for the seven other things on it.
 */
async function loadData() {
  try {
    tasks.value = await selfChecks.mine()
  } catch {
    tasks.value = []
  }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer v-if="tasks.length > 0" class="flex flex-col max-h-[66vh]" data-testid="self-checks-panel">
    <SectionHeader class="mb-3 shrink-0">
      <font-awesome-icon :icon="['fas', 'shirt']" class="mr-2"/>
      {{ t('dashboard.selfChecks') }}
    </SectionHeader>
    <div class="overflow-y-auto flex-1 space-y-2">
      <NeutralContainer
          v-for="task in tasks"
          :key="task.id"
          :data-testid="`self-check-row-${task.id}`"
          class="flex items-center justify-between gap-2 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
          @click="router.push({name: 'inventory-self-check', params: {id: task.id}})"
      >
        <div>
          <p class="text-sm font-medium">{{ task.memberName }}</p>
          <p class="text-xs text-(--text-muted)">{{ t(`selfCheck.state.${task.state}`) }}</p>
        </div>
        <span v-if="task.dueOn" class="text-xs text-(--text-muted) shrink-0">
          {{ t('selfCheck.dueOn', {date: formatDate(task.dueOn)}) }}
        </span>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
