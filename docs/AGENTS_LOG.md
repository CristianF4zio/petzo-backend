# Agents log

| Sprint | Agente | Tarea | Por qué un agente y no hacerlo directo |
| --- | --- | --- | --- |
| B4 | general-purpose (revisor de seguridad) | Revisar toda la API Java contra las reglas de §15/§17 | Contexto limpio enfocado en buscar fallos mientras yo seguía con docs. Encontró 11 hallazgos (3 altos: bypass de `/internal` con ruta codificada, fotos 2–5 sin moderación, moderación con solo 3 fotos); 10 corregidos con tests, el barrido de archivos huérfanos quedó como atajo documentado |
| F1 | code-review (subagente en contexto aparte) | Revisar los endpoints nuevos junto con el diff de la app | Lee el código conectado sin cargar mi contexto; sus 9 hallazgos llevaron a D-28 (`PUT /photos`, alta como borrador) |
| F1 | general-purpose (auditor ponytail del backend) | Auditar todo el backend: flujos, seguridad, concurrencia, tests y código sobrante | Corre en paralelo con el auditor de la app y lee ~50 archivos sin cargar mi contexto; 14 hallazgos con caso concreto |
