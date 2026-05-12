/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {auth} from '@/api'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {loaded, load, fullName, clear} = useSession()

onMounted(() => {
  const token = getItem('session_token')
  if (token && !loaded.value) {
    load()
  }
})

async function handleLogout() {
  const token = getItem('session_token')
  if (token) {
    try {
      await auth.logout({token})
    } catch {
      // ignore
    }
  }
  clear()
  await router.push({name: 'login'})
}
</script>

<template>
  <header
      class="flex h-14 items-center justify-between border-b border-bg-light-accent dark:border-bg-dark-accent px-4">
    <router-link class="text-lg font-bold text-primary no-underline hover:no-underline" to="/">
      Ember
    </router-link>

    <div v-if="loaded && fullName()" class="flex items-center gap-3">
      <router-link to="/station">
        <PrimaryButton>
          {{ t('header.stationPanel') }}
        </PrimaryButton>
      </router-link>
      <span class="text-sm text-(--text-muted) hidden sm:inline">{{ fullName() }}</span>
      <IconButton
          :icon="['fas', 'right-from-bracket']"
          :label="t('header.logout')"
          class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
          @click="handleLogout"
      />
    </div>

    <router-link v-else to="/login">
      <PrimaryButton>
        {{ t('header.login') }}
      </PrimaryButton>
    </router-link>
  </header>
</template>
