# Änderungsprotokoll

## v26.17.4

### Verbesserungen

- **Das Änderungsprotokoll sagt, wann eine Version erschienen ist.** Jeder Eintrag trägt jetzt den Tag seines Erscheinens, mit der genauen Uhrzeit unter dem Mauszeiger, und endet mit einem Link, der seine Änderungen gegenüber der vorherigen Veröffentlichung auf GitHub öffnet.

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
