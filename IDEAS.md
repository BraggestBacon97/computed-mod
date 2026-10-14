# Computed — Ideen-Backlog

Offene Ideen und Requests, sortiert nach Reife. Wird beim Bauen neuer Features ergänzt.

## Offene Ideen

### Speaker / Audio
- **Envelope für `play_wave`** — Attack/Decay-Parameter, damit Töne nicht hart abbeißen.
- **Sweep/Oszillator** — `play_sweep(wave, f1, f2, seconds)`: Frequenz wandert linear
  von f1 nach f2 (Riser, Alarme, Motoren).
- **Disc-Loop** — SpeakerEndpoint `loop(true/false)` oder `play(loop)` für endlose Platten.
- **`play_sequence`** — Node/Endpoint der eine Liste von Noten mit Timing abspielt
  (kleine Melodien ohne externe Taktdaten).
- **Disc-Playlist** — mehrere Discs in die Warteschlange legen, `play` spielt der Reihe nach.
- **Custom-Instrument-Samples** — Nutzer legen eigene .ogg ins Speaker-Mod-Ordner, die dann
  als Instrumente/Wellenformen verfügbar sind.

### Widgets / Display
- **Neue Widget-Typen** — Diagramm/Verlaufs-Widget, Fortschrittsbalken, Checkbox,
  Texteingabe-Feld am Monitor (für Formulare/Passwörter).
- **Bild-Widget** — Textur/Item-Icon auf dem Monitor anzeigen.
- **Kanal-Management im Portable Screen** — Tab per Rechtsklick entfernen (statt am Computer).
- **Widget-Klicks mit Rechtsbutton** — zweite Aktionsspalte für Widgets (z.B. Kontext-Menü).
- **Name im Tab-Label** — Tabs zeigen aktuell nur Nummern; bei wenig Kanälen evtl. den
  Anfangsbuchstaben des Computernamens.

### Computer / Welt
- **Radar-/Sensor-Endpoint** — Entities, Lichtlevel, Wetter oder Blöcke in Radius abfragen
  (`scan(radius)` → Tabelle) — passt direkt zum Create-Radar des Users.
- **Redstone-Endpoint** — Signal am Computer-Face lesen/schreiben (`get_redstone`,
  `set_redstone`), damit Graphen mit der Welt schalten.
- **Monitor-Endpoint `has_display()`** — für Programme, die prüfen ob ein Monitor-Block
  existiert; Portable Monitor und Monitor-Blöcke antworten beide.
- **Energie-Endpoint** — FE/EU lesen und entnehmen (Create/Forge-Energy-Integration).
- **Autostart auf Disk** — Programm das beim Einschalten automatisch läuft.

### Editor / Werkzeuge
- **Undo/Redo im Graph-Editor** — Rückgängig nach Schleifenklicks.
- **Auto-Save** — Graph periodisch oder bei Änderung speichern.
- **Widget-Preview im Editor** — Mini-Render der Ausgabe ohne laufendes Programm.
- **Beispiel-Disk im Creative Tab** — Demoprogramm (Radar, Uhr, Musik) zum Ausprobieren.

### Diskutiert
- **Portable Monitor Screen-Animation** — Öffnungs-Animation (Scale/Fade) im
  `PortableMonitorScreen`; User baut sie als Lernprojekt selbst (siehe Umgesetzt).

### Integrationen
- **Create-Integration ausbauen** — Create-Screens als Monitor, Kinetic-Speed als Sensor.
- **Aeronautics-Integration** — Flugdaten/Höhe als Widget.
- **Komputer-zu-Komputer-Link** — zwei Computer direkt koppeln (Datenübertragung,
  Remote-Aufrufe über den `net`-Endpoint).

## Umgesetzt

- **GeckoLib-Item (optional)** — Portable Monitor nutzt mit GeckoLib im Modpack ein
  keyframe-animierte Geo-Item-Modell (Rechtsklick triggert `unfold` auf dem `use`-Controller);
  ohne GeckoLib bleibt das flache/3D-Item-Model. Platzhalter-Geo + -Animation in
  `geo/item/` + `animations/item/`; Modell/Animation werden in Blockbench (GeckoLib-Plugin)
  neu gebaut und über die selben Dateien exportiert.
- **Portable Monitor 3D** — Item-Model aus Element-Boxen (Front/Back/Side-Texturen,
  Spyglass-artige Display-Transformationen). Die Öffnungs-Animation im Screen ist als
  Lernprojekt beim User: Einstieg in `PortableMonitorScreen.render` (openProgress 0→1,
  Scale um Display-Zentrum + Fade-Overlay).
- **Conditional Nodes (CC-Style)** — `BundledLuaLibrary`-Entries können eine `requiredMod`
  tragen; ohne die Mod erscheinen sie nicht im Explorer. Der Aeronautics-Telemetry-Node
  hängt an Create (ohne Create keine Contraption).
- **Speaker-Synthese `play_wave`** — sine/square/saw/triangle/noise via Pitch-Shift
  (55–3520 Hz), Node „Speaker Play Wave".

## Verworfen

- **USB-Stick-Item** — Files werden bereits gespeichert (User-Entscheidung).
