/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {searchAccounts, type AccountSearchResult} from '@/api/twoFactorAdmin'
import type {Cluster} from '@/api/clusters'

/**
 * One cluster in the instance's list, with the one thing the instance does for it after creating it:
 * naming the person who will run it.
 *
 * The search box lives here rather than on the page because it belongs to one row: an administrator deals
 * with a cluster that has nobody in it once, and never looks at it again.
 */
const props = defineProps<{
  cluster: Cluster
  busy: boolean
}>()

const emit = defineEmits<{
  appoint: [cluster: Cluster, account: AccountSearchResult]
  remove: [cluster: Cluster]
}>()

const {t} = useI18n()

const open = ref(false)
const query = ref('')
const results = ref<AccountSearchResult[]>([])

function toggle() {
  open.value = !open.value
  query.value = ''
  results.value = []
}

async function find() {
  if (query.value.trim().length < 2) {
    results.value = []
    return
  }
  results.value = await searchAccounts(query.value.trim(), 10)
}

function choose(account: AccountSearchResult) {
  open.value = false
  query.value = ''
  results.value = []
  emit('appoint', props.cluster, account)
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between gap-4">
      <div>
        <p class="font-medium">{{ cluster.name }}</p>
        <p v-if="cluster.description" class="text-sm text-(--text-muted)">{{ cluster.description }}</p>
      </div>
      <div class="flex items-center gap-2">
        <SecondaryButton :disabled="busy" @click="toggle">{{ t('adminClusters.appoint') }}</SecondaryButton>
        <DeleteButton :disabled="busy" @click="emit('remove', cluster)"/>
      </div>
    </div>

    <div v-if="open" class="space-y-2">
      <p class="text-sm text-(--text-muted)">{{ t('adminClusters.appointHint') }}</p>
      <TextInput v-model="query" :placeholder="t('adminClusters.appointPlaceholder')" @input="find"/>
      <div v-if="results.length" class="space-y-1">
        <button
            v-for="account in results"
            :key="account.uid"
            class="block w-full rounded px-2 py-1 text-left hover:bg-(--surface-hover)"
            type="button"
            @click="choose(account)"
        >
          {{ account.displayName }} <span class="text-(--text-muted)">{{ account.email }}</span>
        </button>
      </div>
    </div>
  </NeutralContainer>
</template>
