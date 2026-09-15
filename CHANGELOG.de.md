# Änderungsprotokoll

## v26.17.0

### Neue Funktionen

- **Ein Termin kann Dateien mitgeben.** Der Laufzettel, das Formular zum Mitbringen oder der Plan für den Abend wird aus der Mediathek gewählt oder dort hochgeladen und steht auf der Terminseite zum Herunterladen bereit. Jede Datei ist entweder für alle da, die den Termin sehen dürfen, Partnerwachen eingeschlossen, oder bleibt bei denen, die den Termin durchführen.
- **Das Änderungsprotokoll steht auf Deutsch und kommt mit der Instanz.** Die Seite holt die Einträge nicht mehr aus dem Browser heraus bei GitHub, sondern von der eigenen Instanz. Sie bleibt damit auch ohne Verbindung nach außen lesbar, und niemand muss dafür eine Adresse bei GitHub hinterlassen.
- **Nach einer Aktualisierung steht in den Neuigkeiten, was sich geändert hat.** Wurde die Instanz auf eine neue Version gehoben, schreibt Ember einmalig einen Eintrag für die Verwaltung, mit den Änderungen genau dieser Version und einem Link auf das vollständige Änderungsprotokoll.

### Änderungen

- **Ein neues Recht für die internen Daten eines Termins.** Es öffnet das benötigte Material und die Dateien, die der Termin zurückhält, ohne etwas ändern zu dürfen. Wer Termine bearbeiten darf, hat es bereits, es muss also nichts neu vergeben werden; gib es den Leuten, die Abende durchführen, aber kein Inventar führen.

### Fehlerbehebungen

- **Was einem Termin fehlt, bot eine Schaltfläche an, die den Drückenden abwies.** Das benötigte Material beim Verband anzufragen wird jetzt nur noch denen angeboten, die überhaupt Material anfragen dürfen; alle anderen sehen weiterhin, was fehlt.
- **Ein später ausgefüllter Bogen trug unter den vergangenen das falsche Datum.** Ein Abend, der Tage oder Wochen später eingetragen wurde, stand unter dem Datum der Eingabe statt unter dem des Abends und sortierte sich zwischen die Bögen jener Woche, sodass ein Abend im Juli ganz oben mit September stand. Vergangene Bögen tragen jetzt ihr eigenes Datum und laufen vom jüngsten Abend abwärts.
- **Meldungen wurden nie an ein Beacon weitergegeben.** Das Einschalten von „Meldungen weiterleiten" merkte sich die Entscheidung und änderte sonst nichts: Eine geschriebene Meldung blieb auf der Instanz liegen und ging nicht weiter, sodass ein Beacon zwar Fehler und Zahlen sammelte, aber nie ein Wort, das jemand geschrieben hatte. Meldungen gehen jetzt weiter, sobald sie geschrieben werden, und die Parameter hinter dem Fragezeichen der Seitenadresse bleiben dabei zurück.
- **Ein Beacon wies alles ab, was ihm gemeldet wurde.** Meldete eine Instanz nicht zufällig an sich selbst, wurde jede signierte Zustellung als an ein anderes Beacon gerichtet abgewiesen, sodass Betreiber ein leeres Beacon sahen, ohne dass irgendwo stand, warum. Ein Beacon misst eine Zustellung jetzt an seiner eigenen Adresse, und das ist die aus `api.baseUrl`.
