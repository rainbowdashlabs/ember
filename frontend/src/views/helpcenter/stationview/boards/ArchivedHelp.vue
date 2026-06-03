/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
</script>

<template>
    <HelpArticle title="Archiv" subtitle="Abgeschlossene Tickets einsehen">
        <HelpSection title="Was sind archivierte Tickets?">
            <p>Tickets, die in der letzten Spalte länger als die eingestellte Anzahl Tage liegen, werden automatisch archiviert. Archivierte Tickets sind weiterhin einsehbar, erscheinen aber nicht mehr auf dem Board.</p>
        </HelpSection>

        <HelpSection title="Tickets wiederherstellen">
            <p>Archivierte Tickets können wieder aktiviert werden. Dabei wird das Ticket zurück in die letzte Spalte des Boards verschoben.</p>
        </HelpSection>

        <HelpTip>
            Die Anzahl der Tage bis zur Archivierung wird in den Board-Einstellungen festgelegt.
        </HelpTip>
    </HelpArticle>
</template>
