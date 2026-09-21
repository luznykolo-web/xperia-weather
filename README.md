# Sony Xperia Z2 Tablet — Stacja Pogodowa v1

Wersja przygotowana z zatwierdzonego projektu v13 bez zmiany jego wyglądu.

- ekran docelowy: Sony Xperia Z2 Tablet, 1920×1200, poziomo
- układ zachowuje proporcje zaakceptowanej wizualizacji 1024×600 — bez rozciągania ikon i paneli
- grafiki pogodowe: przestrzenne 3D
- tło: automatycznie dobierane do aktualnej pogody oraz dnia/nocy
- dane: Skarżysko-Kamienna / Open-Meteo
- HTTPS: pozostawiona sprawdzona poprawka Conscrypt + ISRG Root X1 z działającej wersji v9
- odświeżanie: co 15 minut
- tryb: pełny ekran, landscape, keep-screen-on, możliwość działania jako HOME
- minSdk 19, dzięki czemu pakiet obejmuje także starsze wersje Androida spotykane na Xperia Z2 Tablet

GitHub Actions buduje artefakt: `Sony-Xperia-Z2-Weather-Station-v1`.
