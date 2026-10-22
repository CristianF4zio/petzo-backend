# Agents log

| Sprint | Agente | Tarea | Por qué un agente y no hacerlo directo |
| --- | --- | --- | --- |
| B4 | general-purpose (revisor de seguridad) | Revisar toda la API Java contra las reglas de §15/§17 | Contexto limpio enfocado en buscar fallos mientras yo seguía con docs. Encontró 11 hallazgos (3 altos: bypass de `/internal` con ruta codificada, fotos 2–5 sin moderación, moderación con solo 3 fotos); 10 corregidos con tests, el barrido de archivos huérfanos quedó como atajo documentado |
