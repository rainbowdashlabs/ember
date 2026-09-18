# Änderungsprotokoll

## v26.18.1

### Neue Funktionen

- **Eine Partnerwache kann eigene Plätze bekommen.** Ein mit einer anderen Wache geteilter Termin kann Plätze für sie zurücklegen, mit oder ohne Obergrenze, und diese Wache wählt dann selbst, wer von ihren Leuten sie einnimmt, statt auf eine Bestätigung zu warten. Ist nichts zurückgelegt, bestätigt die ausrichtende Wache wie bisher jeden Gast selbst.

### Verbesserungen

- **Eine Abmeldung von einem Termin fragt nach und lässt sich zurücknehmen.** Jeder Knopf, der einen Platz zurückgibt, stellt jetzt vorher eine kurze Frage, auch der, der einen ganzen Haushalt auf einmal abmeldet. Fünf Minuten lang lässt sich die Abmeldung über die eingeblendete Meldung wieder zurücknehmen, und zurück kommt der Platz, der belegt war, und nicht eine neue Anmeldung am Ende der Warteschlange. Mit gedrückter Umschalttaste entfällt die Frage, wie an anderen Stellen auch.
- **Zu spät zur Anmeldung sagt jetzt, wer weiterhelfen kann.** Eine erneute Anmeldung nach dem Anmeldeschluss endete in einer nichtssagenden Fehlermeldung. Jetzt steht dort, dass die Anmeldung geschlossen ist und dass die Terminleitung noch jemanden aufnehmen kann.
- **Ein Gast einer Partnerwache wird behandelt wie ein eigenes Mitglied.** Seine Anmeldung wird sofort angenommen, wenn der Termin keine Bestätigung verlangt, statt auf eine zu warten, um die nie jemand gebeten wurde, und sie wird abgelehnt, wenn der Termin keine Anmeldungen annimmt, abgesagt wurde oder die Anmeldung geschlossen ist. Eine Abmeldung bleibt vermerkt, damit die ausrichtende Wache erkennt, wer sich abgemeldet hat und wer nie geantwortet hat.
- **Die Dateien eines Termins werden gezeigt, nicht nur angeboten.** Ein Druck darauf öffnet die Datei an Ort und Stelle: Herauszufinden, welches von vier Blättern die Karte ist, heißt nicht mehr, alle vier herunterzuladen. Das Speichern bleibt eine eigene Schaltfläche.
- **Dateien zeigen, was in ihnen steckt.** Bilder und die erste Seite eines PDFs stehen jetzt neben der Datei, in der Liste eines Termins wie in der Mediathek, statt einer Reihe gleicher Symbole.
- **Auswahlmöglichkeiten an Terminen und Wartelisten werden zeilenweise erfasst.** Beide fragten die ganze Liste noch in einem einzigen Feld ab, das eine trennte bei Zeilenumbrüchen, das andere bei Kommas: Eine Auswahl mit Komma wurde so unbemerkt zu zweien. Sie nutzen jetzt denselben Editor wie überall sonst, mit einer Zeile je Auswahl, die sich umsortieren lässt.
- **Die Löschen-Schaltfläche an den Feldern eines Termins sitzt an einer festen Stelle.** Sie teilte sich eine Zeile, die umbrach, und konnte dadurch mitten im Bereich zwischen anderen Einstellungen landen; jetzt steht sie oben rechts an ihrem Feld.
- **Die Neuigkeitenliste liest sich wieder als Liste.** Ein langer Beitrag füllt nicht mehr die ganze Seite: Beiträge werden auf wenige Zeilen gekürzt und laden zum Weiterlesen ein, den Rest gibt es auf der Seite des Beitrags.

### Fehlerbehebungen

- **Neuigkeiten von den Machern von Ember trugen in der Liste kein Zeichen.** Das Ember-Logo erschien erst im geöffneten Beitrag, nicht in der Liste, aus der er geöffnet wurde: Dort war ein solcher Beitrag von einem an der Station geschriebenen nicht zu unterscheiden.
- **Das Löschen eines Kontos meldete Probleme, die keine waren.** Beim Entfernen eines Kontos wurden mehrere Datenarten aufgeführt, die nicht aufgeräumt werden konnten, obwohl die Datenbank sie längst entfernt hatte und nichts zurückblieb. Die Liste des Aufzuräumenden wird jetzt gegen die Datenbank selbst geprüft und kann nicht mehr unbemerkt auseinanderlaufen.
- **Wer seinen Platz zurückgab, konnte ganz aus der Liste verschwinden.** Ein noch nicht bestätigter Platz wurde beim Zurückgeben gelöscht: Die Anmeldeliste war dann einfach um einen Eintrag kürzer und nichts sagte, wer abgesprungen war, obwohl die Benachrichtigung darüber verschickt wurde. Jeder zurückgegebene Platz bleibt jetzt als zurückgezogen in der Liste, und eine erneute Anmeldung funktioniert wie zuvor.
- **Eine am späten Abend geöffnete Liste galt dem Vortag.** Wurde eine Anwesenheit aus einem wiederkehrenden Termin gestartet, las sie das Datum von der Uhr des Servers statt von der der Station: In den letzten Stunden vor Mitternacht wurde die Liste damit für den vorherigen Termin geöffnet und trug dessen Zeiten. Der Tag richtet sich jetzt durchgehend nach der Station.

## v26.18.0

### Neue Funktionen

- **Eine Problemmeldung kann ein Bild der Seite mitbringen.** Auf Knopfdruck fragt der Browser, was geteilt werden soll, und bietet diesen Tab zuerst an, sodass kein Dialog des Betriebssystems nötig ist; oder du wählst ein Bild, das du selbst gemacht hast. Zieh über alles, was niemand sehen soll, und es wird übermalt, bevor die Meldung rausgeht. Der Dialog tritt während der Aufnahme zur Seite, damit die Seite im Bild ist und nicht der Dialog. Von allein wird nie etwas aufgenommen, Passwortfelder sind abgedeckt, bevor das Bild überhaupt erscheint, und eine Meldung ohne Bild ist wie bisher.
- **Ein Mitglied kann so genannt werden, wie es alle tatsächlich nennen.** Wer im Register Maximilian heißt und in der ganzen Wache Max gerufen wird, hinterlegt einen Spitznamen im Profil, und von da an steht auf dem Brett, in den Kommentaren, bei den Anmeldungen, in den Benachrichtigungen und in den Mails Max. In Mitgliederlisten steht `Maximilian "Max" Hoffmann`, damit niemand raten muss, wer gemeint ist. Das Mitglied setzt seinen eigenen im Profil, wer sich um es kümmert, darf einen für es setzen, und wer die Mitglieder der Wache pflegt, darf einen im Profil des Mitglieds in der Verwaltung richtigstellen. Sonst niemand, und wer ihn geschrieben hat, wird in jedem Fall festgehalten. Eine Wache, die lieber durchgehend Registernamen führt, schaltet das Ganze ab, und jeder hinterlegte Spitzname bleibt erhalten.
- **Auf einem Dokument steht weiterhin der Name aus dem Register.** Anwesenheitsliste, Prüfprotokoll, Bestands- und Bewegungsexporte und die Datenauskunft nennen Maximilian Hoffmann, was immer die Bildschirme sagen. Wer die Wache verlässt, behält in den alten Einträgen den Namen, unter dem die Wache ihn kannte.
- **Eine Anwesenheit kann ohne passende Vorlage begonnen werden.** Neben den Vorlagen steht jetzt eine Leere Anwesenheit, die in einem zweiten Schritt fragt, welche Mitgliedstypen und welche Gruppen eingetragen werden, und danach die gewohnte vorausgefüllte Liste öffnet. Beides ergänzt sich, und eine Wache muss dafür keine leere Vorlage mehr aufbewahren.

### Verbesserungen

- **Eine Vorlage sagt vor der Auswahl, was sie mitbringt.** Jede Kachel auf der Seite für eine neue Anwesenheit nennt jetzt die Gruppen, die sie einträgt, und die Felder, die sie abfragt; die Wahl läuft damit nicht mehr über das Gedächtnis. Eine Vorlage, die niemanden einträgt, sagt auch das.
- **Das Änderungsprotokoll sagt, wann eine Version erschienen ist.** Jeder Eintrag trägt jetzt den Tag seines Erscheinens, mit der genauen Uhrzeit unter dem Mauszeiger, und endet mit einem Link, der seine Änderungen gegenüber der vorherigen Veröffentlichung auf GitHub öffnet.
- **Ein Geburtsdatum kann ohne das Alter dahinter stehen.** Das Feld hat jetzt einen Schalter dafür, eingeschaltet wie bisher, damit eine Wache, die das Alter zusätzlich als eigene Frage stellt, es nicht zweimal in einer Zeile stehen hat.
- **Ein berechnetes Feld bietet nur noch die Einstellungen an, die es erreichen.** Seine Antwort schreibt niemand, also fragt es nicht mehr, ob eine Antwort erwartet wird, ob sie geschrieben werden darf, ob eine Änderung gemeldet werden soll oder womit sie beginnen soll.
- **Eine Namensliste bleibt eine Namensliste.** Was bei jemandem offen ist, steht nicht mehr unter jeder Zeile aufgeklappt. Eine Zeile sagt, wie viele Vorgänge es sind, ein Geburtstag steht weiterhin daneben, und ein Druck öffnet alles. Eine Wache mit vierzig Leuten und je einem Tausch hatte aus der Anwesenheitsliste eine Seite voller Besorgungen gemacht.
- **Ein Tausch kann abgebrochen werden, während das Mitglied davorsteht.** Wer die Kontrolle führt, kann ihn von der Liste aus löschen; es wird vorher gefragt und das Teil genannt. Dort stehen nur Tausche, bei denen noch nichts übergeben wurde, es gibt also nie etwas zurückzunehmen.
- **Das Bild einer Meldung kann an einen Beacon weitergehen, nachdem jemand hier es angesehen hat.** Eine Meldung mit Bild wartet in der Verwaltungsliste, bis sie weitergegeben wird; dort lässt sich vorher mehr übermalen oder das Bild aus der Sendung nehmen. Ein Schalter in den Beacon-Einstellungen schickt sie stattdessen sofort raus, und ein weggelassenes Bild wird einer Meldung nie nachgereicht.
- **Problemmeldungen werden aufgeräumt.** Dreißig Tage nachdem eine Meldung als erledigt markiert wurde, wird sie mit ihrem Bild gelöscht. Bisher blieben sie für immer liegen.

### Fehlerbehebungen

- **Ein Foto auszuwählen ließ die Seite stehen.** Beim Auswählen eines Profilbilds reagierte die ganze Seite nicht mehr, während das Bild auf eine sendbare Größe gebracht wurde, ohne dass etwas davon zu sehen war, und zwar lange genug, dass Leute noch einmal drückten und fragten, ob es geklappt hat. Die Wartemarkierung erscheint jetzt vor der Arbeit, und die Arbeit selbst geht an einen anderen Thread, wo der Browser einen hat.
- **Es wurde eine Profiländerung gemeldet, die niemand gemacht hat.** Ein Profil zu öffnen und zu speichern zählte jede unbeantwortete Frage, die unbeantwortet blieb, als Änderung, weil eine nie beantwortete Frage und eine mit leerem Feld beantwortete unterschiedlich gespeichert und als verschieden gelesen wurden. Ein Alter, das sich aus einem Geburtsdatum selbst errechnet, wurde genauso gemeldet, obwohl es niemand schreiben kann. Beides wird nicht mehr aufgezeichnet und niemandem mehr zur Bestätigung vorgelegt.
- **Wer für ein Mitglied verantwortlich ist, kam nicht an dessen Dokumente.** Die Einverständniserklärung, der ärztliche Vermerk und alles andere, was die Wache für ein Kind aufbewahrt, wurde genau der Person verweigert, die es unterschreibt, und es gab auch keinen Bildschirm dafür. Sie stehen jetzt im Profil unter den eigenen Dokumenten, zum Lesen und nicht zum Hinzufügen, und was vor dem Mitglied verborgen ist, ist auch vor ihnen verborgen.
- **Jemand konnte einen Tag vor dem Geburtstag ein Jahr älter werden.** Auf einem Gerät mit einer Zeitzone hinter UTC lagen das Alter hinter dem Geburtsdatum und das Alter in einer eigenen Spalte beide einen Tag vor sich selbst. Ein Geburtsdatum wird jetzt als der Tag gelesen, den es nennt, egal wo der Lesende sitzt.
- **Ein aus einem Datum berechnetes Alter konnte eine alte Zahl zeigen.** Das Profil eines Mitglieds und das Formular, auf dem seine Antworten gegeben werden, zeigten, was einmal in das Feld geschrieben worden war, statt der Zahl, die sich ergibt, und das änderte sich nie wieder. Es wird jetzt überall berechnet, wo es steht, und angezeigt, ohne zum Überschreiben angeboten zu werden.
- **Ein Mitglied wurde beim Öffnen des eigenen Vorgangs vier Mal abgewiesen.** Die Seite bot das Formular zur Wahl des Ersatzteils allen an, die den Vorgang sehen konnten, und dieses Formular fragt dafür das gesamte Inventar und alle Mitglieder der Wache ab. Das Ersatzteil wird jetzt nur noch dort angeboten, wo das Lager auch gelesen werden darf; sonst steht eine Zeile, dass die Wache es einträgt.
- **Ein Mitglied konnte einen Schritt abbrechen, der ihm nicht gehörte.** Jeder offene Vorgang bot die Schaltfläche zum Ablehnen allen an, die ihn ansahen, gleich auf welche Partei der Schritt wartete. Der Bereich wird jetzt nur noch der Partei gezeigt, die an der Reihe ist, und wem das Übergehen erlaubt ist.
- **Ein Termin am späten Abend zeigte keine seiner Anmeldungen.** Die Seite ermittelte den Tag des Termins nach der Uhr des Lesers, während alles, was zu ihm gehört, nach der Uhr der Station abgelegt wird: ein Termin, der in der Zeitzone des Lesers nach Mitternacht endet, oder aus einem anderen Land als dem der Station betrachtet, zeigte deshalb eine leere Anmeldeliste, aus der sich weder Checkliste noch Umfrage starten ließ. Der Tag wird jetzt durchgehend nach der Uhr der Station gelesen.
- **Das Umbenennen eines Datums leerte jedes daraus berechnete Alter.** Ein Altersfeld hielt sich am Namen der Frage fest, aus der es zählt, sodass eine Umbenennung die Spalte leer zurückließ, ohne zu sagen warum. Es hält sich jetzt an der Frage selbst fest, und bestehende Altersfelder werden übernommen, wie sie sind.

### Änderungen

- **Eine Person wird auf eine Art geschrieben.** Ember hat an etwa hundertsechzig Stellen selbst entschieden, wie ein Name zu schreiben ist, weshalb eine Liste und eine Auswahl sich über dasselbe Mitglied uneinig sein konnten. Jetzt gibt es eine Stelle, die die Frage beantwortet, und eine Art zu fragen, in Java wie in der Datenbank, und einen Test, der beide auf dieselbe Antwort festnagelt.

## v26.17.3

### Fehlerbehebungen

- **Ein Termin wurde in der Uhr des Servers geschrieben statt in der der Wache.** Ein Abend von 09:00 bis 14:00 kam im Benachrichtigungs-Feed als 07:00 bis 12:00 an, weil die Maschine, die den Feed baut, UTC führt und die Zeitzone der Wache nie gefragt wurde. Zeiten stehen jetzt dort, wo der Termin stattfindet, und eine Wache ohne eingetragene Zeitzone liest weiterhin in UTC. Auch die Zeit in einem exportierten Selbstcheck folgt der Wache, und eine Kalenderdatei einer Wache mit unlesbarer Zeitzone fällt nicht mehr auf die des Servers zurück.
- **Die Erinnerung an einen Termin kurz nach Mitternacht kam einen Tag zu früh.** Auf welchen Tag ein Termin fällt und welcher Tag gerade ist, wurden beide in der Uhr des Servers bestimmt, sodass halb eins in der Wache als der Abend davor zählte und jede Erinnerung dafür einen Tag danebenlag.

## v26.17.2

### Verbesserungen

- **Ein Beacon erfährt jetzt, was ein Fehler überhaupt war.** Ein Fehler kam als Logger-Name, Stufe und Anzahl an, und bei einer Warnung ohne Exception ist das alles, was es gibt, sodass mehrere verschiedene Fehlschläge derselben Klasse als eine Zeile ankamen, die keinen davon benannte. Was dabei geloggt wurde, reist jetzt mit, ohne Mailadressen.
- **Eine weitergegebene Meldung kommt mit dem Bildschirm an, über den sie geschrieben wurde.** Browser, Fenstergröße, die Rechte der schreibenden Person und die letzten Anfragen der Seite reisen mit, jede Adresse ohne ihre Parameter, so wie die Seite selbst schon vorher. Wer sie geschrieben hat, bleibt weiterhin in der Wache. „Der Knopf tut nichts" benennt für sich genommen keinen Fehler.

### Fehlerbehebungen

- **Ein Link auf eine andere Seite dieser Instanz war kein Link.** Alles, was als Pfad geschrieben ist, und so ist alles innerhalb von Ember geschrieben, verlor auf dem Weg zur Anzeige seine Adresse und kam als unterstrichener Text an, dem niemand folgen konnte. Der Link in dem Eintrag, den Ember nach einer Aktualisierung schreibt, war einer davon. Links auf andere Seiten waren nie betroffen.
- **Ein Systemeintrag bot einer Wache drei Schaltflächen, die nicht funktionieren konnten.** Bearbeiten, löschen und nachsehen, wer ihn gelesen hat, sind Rechte einer Wache an ihren eigenen Einträgen, und ein Eintrag der Instanz gehört zu keiner Wache, also antwortete jede davon mit „nicht gefunden". Sie werden nicht mehr angeboten, und ein solcher Eintrag trägt jetzt das Ember-Logo statt der Initialen eines Namens, den niemand hat.
- **Eine Benachrichtigung schrieb das Datum so, wie eine Datenbank es schreibt.** Die Erinnerung an einen Termin nannte als Tag 2026-09-19 statt 19.09.2026, ebenso jede andere Benachrichtigung mit einem Tag darin: neue Termine, eine noch fehlende Antwort, ein zurückzugebender Selbstcheck.

## v26.17.1

### Fehlerbehebungen

- **An ein Beacon gemeldet kam nie etwas an.** Jeder Fehler und jede Meldung wurde vor dem Speichern als unsigniert abgewiesen, weil eine Zustellung den Schlüssel nicht mitbrachte, an dem ihre Signatur geprüft wird. Die Abweisung stand auch in keinem Log, sodass Betreiber ein Beacon mit Zahlen darauf und sonst nichts sahen, selbst auf einer Instanz, die an sich selbst meldet. Zustellungen bringen den Schlüssel jetzt mit, und ein Beacon, das eine abweist oder nicht erreichbar ist, steht im Log.
- **Eine Meldung ließ sich nicht von Hand weitergeben.** Meldungen erreichten ein Beacon nur beim Schreiben, alles vor dem Einschalten des Schalters blieb also liegen, ohne Möglichkeit es zu senden. Die Liste bietet jetzt, was das Fehlerprotokoll schon immer bietet: erst sehen, was genau hinausgeht, dann senden.

## v26.17.0

### Neue Funktionen

- **Ein Termin kann Dateien mitgeben.** Der Laufzettel, das Formular zum Mitbringen oder der Plan für den Abend wird aus der Mediathek gewählt oder dort hochgeladen und steht auf der Terminseite zum Herunterladen bereit. Jede Datei ist entweder für alle da, die den Termin sehen dürfen, Partnerwachen eingeschlossen, oder bleibt bei denen, die den Termin durchführen.
- **Das Änderungsprotokoll steht auf Deutsch und kommt mit der Instanz.** Die Seite holt die Einträge nicht mehr aus dem Browser heraus bei GitHub, sondern von der eigenen Instanz. Sie bleibt damit auch ohne Verbindung nach außen lesbar, und niemand muss dafür eine Adresse bei GitHub hinterlassen.
- **Nach einer Aktualisierung steht in den Neuigkeiten, was sich geändert hat.** Wurde die Instanz auf eine neue Version gehoben, schreibt Ember einmalig einen Eintrag für die Verwaltung, mit den Änderungen genau dieser Version und einem Link auf das vollständige Änderungsprotokoll.

### Änderungen

- **Ein neues Recht für die internen Daten eines Termins.** Es öffnet das benötigte Material und die Dateien, die der Termin zurückhält, ohne etwas ändern zu dürfen. Wer Termine bearbeiten darf, hat es bereits, es muss also nichts neu vergeben werden; gib es den Leuten, die Abende durchführen, aber kein Inventar führen.
- **Eine Profilfrage wird einmal geschrieben und denen gestellt, die sie beantworten sollen.** Mitglieder → Konfiguration hat keinen Reiter je Mitgliedsart mehr: auf der einen Seite stehen die Fragen, jede einmal geschrieben, und daneben steht zur gewählten Frage, wer sie gestellt bekommt, beliebig viele Mitgliedsarten und beliebig viele Gruppen, auf demselben Bildschirm, den ein Verband für seine eigenen Fragen nutzt. Ob eine Antwort erwartet wird, wie breit das Feld gezeichnet wird und ob diese Zielgruppe hineinschreiben darf, kann sich je Zielgruppe unterscheiden, und die Reihenfolge jedes Formulars gehört diesem Formular.
- **Eine neue Frage wird niemandem gestellt, bis du sagst, wem.** Sie ist aufgeschrieben und erreicht kein Profil, solange keine Zielgruppe genannt ist, was die Liste an der Zeile sagt, statt es bemerkt werden zu lassen. Anwärter sind jetzt eine eigene Art, eine Wache kann sie also weniger fragen als ein Mitglied.
- **Ein Formular wird dort angeordnet, wo es gelesen wird.** Die Vorschau neben den Fragen ist das Formular selbst: zieh ein Feld dorthin, wo es hingehört, zieh an seiner rechten Kante, um seine Breite zu setzen, und setz einen Abstand hinein, der das Nachfolgende weiterschiebt, damit zwei Fragen unter zwei anderen stehen.
- **Mehrere Fragen lassen sich in einem Zug an eine Zielgruppe stellen.** Hak sie in der Liste an, wähl eine Mitgliedsart oder eine Gruppe, und jede einzelne davon wird dieser Zielgruppe auf einmal gestellt.

### Fehlerbehebungen

- **Dieselbe Frage konnte zweimal auf einem Profil stehen.** Hatte eine Wache dieselbe Frage einmal für die eine und einmal für die andere Mitgliedsart aufgeschrieben, begegnete sie jemandem, der als beides zählt, zweimal, und die beiden Fassungen sammelten verschiedene Antworten auf das, was sich als eine Frage liest. Fragen gleichen Namens und gleicher Art werden bei der Aktualisierung zu einer zusammengeführt, wobei die ausgefüllte Antwort erhalten bleibt, wenn nur eine der beiden eine hatte; der Stand der Fragen und ihrer Antworten von vor der Zusammenführung wird zuvor in eine Datei im Datenverzeichnis geschrieben.
- **Problemmeldungen wurden nie an ein Leuchtfeuer weitergegeben.** Das Einschalten von "Problemmeldungen automatisch weitergeben" merkte sich die Wahl und änderte nichts: eine geschriebene Meldung lag auf der Instanz und ging nicht weiter, ein Leuchtfeuer sammelte also Fehler und Zahlen, aber kein einziges Wort von jemandem. Meldungen werden jetzt weitergegeben, sobald sie geschrieben sind, und die Abfrage in der Adresse der Seite bleibt zurück.
- **Ein Leuchtfeuer wies alles ab, was ihm gemeldet wurde.** Meldete eine Instanz nicht zufällig an sich selbst, wurde jede unterschriebene Sendung als an ein anderes Leuchtfeuer gerichtet abgewiesen, der Betrieb sah also ein leeres Leuchtfeuer ohne Hinweis darauf, warum. Ein Leuchtfeuer wägt eine Sendung jetzt gegen die eigene Adresse ab, und das ist die aus `api.baseUrl`.
- **Was einem Termin fehlt, bot eine Schaltfläche an, die den Drückenden abwies.** Das benötigte Material beim Verband anzufragen wird jetzt nur noch denen angeboten, die überhaupt Material anfragen dürfen; alle anderen sehen weiterhin, was fehlt.
- **Ein später ausgefüllter Bogen trug unter den vergangenen das falsche Datum.** Ein Abend, der Tage oder Wochen später eingetragen wurde, stand unter dem Datum der Eingabe statt unter dem des Abends und sortierte sich zwischen die Bögen jener Woche, sodass ein Abend im Juli ganz oben mit September stand. Vergangene Bögen tragen jetzt ihr eigenes Datum und laufen vom jüngsten Abend abwärts.
