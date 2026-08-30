/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {youtubePlaylistEmbedUrl} from '@/util/youtube'

/**
 * The explanatory videos, played from the playlist they live in.
 *
 * <p>One player rather than a list of links: the playlist carries its own order and its own list of
 * what is in it, so whoever opens this page can start at the first video and keep going without
 * coming back here between them.
 *
 * <p>Played from the no-cookie host, the same one the wiki uses for a video somebody pastes in, so
 * this page sets nothing on the reader's machine until they press play.
 */
const PLAYLIST_ID = 'PLVWolXdhJiEg'

const {t} = useI18n()

const embedUrl = youtubePlaylistEmbedUrl(PLAYLIST_ID)
const playlistUrl = `https://www.youtube.com/playlist?list=${PLAYLIST_ID}`
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.videos.title')" :subtitle="t('helpCenter.basics.videos.subtitle')">
    <HelpSection :title="t('helpCenter.basics.videos.whatIs')">
      <p>{{ t('helpCenter.basics.videos.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.videos.watch')">
      <NeutralContainer class="p-0">
        <div class="relative pb-[56.25%] h-0">
          <iframe
              :src="embedUrl"
              :title="t('helpCenter.basics.videos.title')"
              class="absolute top-0 left-0 w-full h-full rounded"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowfullscreen
          />
        </div>
      </NeutralContainer>
      <p class="mt-3">
        <a :href="playlistUrl" target="_blank" rel="noopener"
           class="text-(--primary) underline">{{ t('helpCenter.basics.videos.openOnYoutube') }}</a>
      </p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.videos.tip') }}</HelpTip>
  </HelpArticle>
</template>
