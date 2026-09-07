/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import AppLink from '@/components/navigation/AppLink.vue'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'
import {getUpdateStatus} from '@/api/system'

const {t} = useI18n()
const {hasPermission} = useSession()

const latestVersion = ref('')

/**
 * Asked for only by whoever administers a station, because that is who acts on it, and because the
 * endpoint answers nobody else. A failure leaves the notice absent rather than showing an error:
 * an instance that cannot reach GitHub is not a problem the reader of a footer can do anything
 * about.
 */
onMounted(async () => {
  if (!hasPermission(StationPermission.STATION_ADMINISTRATOR)) return
  try {
    const status = await getUpdateStatus()
    if (status.updateAvailable && status.latestVersion) latestVersion.value = status.latestVersion
  } catch { /* an instance that cannot ask simply says nothing */ }
})
</script>

<template>
  <div
      v-if="latestVersion"
      class="flex items-center gap-2 rounded-theme border border-primary bg-primary/10 px-3 py-2 text-sm"
      data-testid="update-notice"
  >
    <font-awesome-icon :icon="['fas', 'circle-up']" class="text-primary"/>
    <span>{{ t('footer.updateAvailable', {version: latestVersion}) }}</span>
    <AppLink
        :icon="['fab', 'github']"
        external
        href="https://github.com/rainbowdashlabs/ember/releases/latest"
    >
      {{ t('footer.updateLink') }}
    </AppLink>
  </div>
</template>
