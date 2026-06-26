/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center mb-4 gap-3">
    <IconButton :icon="['fas', 'inbox']" :label="t('boards.showBacklog')" />
    <div class="flex items-center">
      <div class="cursor-pointer rounded-full ring-2 ring-primary">
        <div class="h-8 w-8 rounded-full bg-primary/15 text-primary font-bold flex items-center justify-center text-xs">
          <font-awesome-icon :icon="['fas', 'users']" />
        </div>
      </div>
      <div class="cursor-pointer rounded-full opacity-70 -ml-1.5">
        <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Max M." size="md" />
      </div>
      <div class="cursor-pointer rounded-full opacity-70 -ml-1.5">
        <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Erika F." size="md" />
      </div>
    </div>
  </div>
</template>
