/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {auth} from '@/api'
import client from '@/api/client'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'
import {useTheme} from '@/composables/useTheme'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import PrideText from '@/components/display/PrideText.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {usePride} from '@/composables/usePride'
import { emberLogo } from '@/composables/useEmberLogo'

const {t} = useI18n()
const router = useRouter()
const {loaded, load, fullName, clear, sessionInfo} = useSession()
const {prideActive, prideVariant} = usePride()
const logo = emberLogo()
const isDemo = ref(false)

onMounted(async () => {
  const token = getItem('session_token')
  if (token && !loaded.value) {
    load()
  }
  try {
    const res = await client.get<{ demo?: boolean }>('/public/config')
    isDemo.value = res.data.demo ?? false
  } catch { /* ignore */ }
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
  useTheme().resetToInstanceDefaults()
  await router.push({name: 'login'})
}
</script>

<template>
  <header
      class="flex h-14 items-center justify-between border-b border-bg-light-accent dark:border-bg-dark-accent px-4">
    <router-link class="flex items-center gap-2 text-lg font-bold text-primary no-underline hover:no-underline" to="/">
      <LayeredEmberLogo :layers="logo.layers" :active-layers="logo.activeLayers" size="h-7 w-7" :pixel-size="64" />
      <PrideText :active="prideActive" :variant="prideVariant">Ember</PrideText>
    </router-link>

    <div v-if="loaded && fullName()" class="flex items-center gap-3">
      <router-link to="/station">
        <PrimaryButton>
          {{ t('header.stationPanel') }}
        </PrimaryButton>
      </router-link>
      <UserAvatar :identity="sessionInfo?.member?.uid ? { stationUid: sessionInfo.stationId ?? '', memberUid: sessionInfo.member.uid } : undefined" :name="fullName()" size="sm" class="hidden sm:flex"/>
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
        {{ isDemo ? t('landing.tryNow') : t('header.login') }}
      </PrimaryButton>
    </router-link>
  </header>
</template>
