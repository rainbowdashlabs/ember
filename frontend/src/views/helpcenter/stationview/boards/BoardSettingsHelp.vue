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
    <HelpArticle title="Board-Einstellungen" subtitle="Spalten, Felder und Zugriff konfigurieren">
        <HelpSection title="Allgemein">
            <p>Hier kannst du den Namen, die Beschreibung und das Archivierungs-Verhalten des Boards ändern. „Tage bis zur Archivierung" legt fest, wie lange Tickets in der letzten Spalte bleiben, bevor sie archiviert werden.</p>
        </HelpSection>

        <HelpSection title="Spalten verwalten">
            <p>Lege die Spalten des Boards fest. Die Reihenfolge bestimmt den Arbeitsfluss von links nach rechts. Jede Spalte kann einen Namen und eine Farbe haben.</p>
            <p class="mt-2">Tickets können nur von einer Spalte zur nächsten oder zurück verschoben werden.</p>
        </HelpSection>

        <HelpSection title="Backlog">
            <p>Aktiviere den Backlog, um Tickets sammeln zu können, bevor sie eingeplant werden. Tickets im Backlog erscheinen nicht auf dem Board selbst.</p>
        </HelpSection>

        <HelpSection title="Benutzerdefinierte Felder">
            <p>Erstelle zusätzliche Felder, um Tickets mit weiteren Informationen zu versehen. Verfügbare Feldtypen sind Text, Zahl, Datum und Auswahl.</p>
        </HelpSection>

        <HelpSection title="Labels">
            <p>Erstelle Labels mit Namen und Farben, um Tickets zu kategorisieren. Labels helfen, Tickets schnell zu filtern und zu unterscheiden.</p>
        </HelpSection>

        <HelpSection title="Zugriffsrechte">
            <p>Lege fest, wer das Board sehen und bearbeiten kann. Du kannst den Zugriff nach Mitgliedstyp, Gruppe oder Tag einschränken:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Lesezugriff:</strong> Wer darf das Board und seine Tickets sehen.</li>
                <li><strong>Bearbeitungszugriff:</strong> Wer darf Tickets erstellen, bearbeiten und kommentieren.</li>
            </ul>
            <p class="mt-2">Ohne Einschränkung haben alle Mitglieder der Station Zugriff.</p>
        </HelpSection>

        <HelpSection title="Föderation">
            <p>Teile ein Board mit Partnerstationen. Für jeden Partner kannst du festlegen:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Nur Lesen:</strong> Die Partnerstationen können das Board sehen, aber nichts bearbeiten.</li>
                <li><strong>Vollzugriff:</strong> Die Partnerstationen können Tickets erstellen, bearbeiten und kommentieren.</li>
            </ul>
        </HelpSection>

        <HelpTip>
            Änderungen an Spalten wirken sich nur auf neue Tickets aus. Bestehende Tickets bleiben in ihrer aktuellen Spalte, bis sie verschoben werden.
        </HelpTip>
    </HelpArticle>
</template>
