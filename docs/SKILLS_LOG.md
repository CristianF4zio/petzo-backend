# Skills log

| Sprint | Skill | Para qué la usaste | Resultado |
| --- | --- | --- | --- |
| B0 | (ninguna nueva) | Scaffolding con Spring Initializr y Flyway | — |
| B1 | api-and-interface-design | Contrato REST de §17: errores ProblemDetail con `code`, validación en el borde, paginación `{items,page,pageSize,total}` | 27 rutas documentadas en Swagger |
| B2 | claude-api | SDK de Java para Haiku 5.5: `AnthropicOkHttpClient`, `system`, `outputConfig(effort LOW)`, `usage()`, chequeo de `stopReason` REFUSAL, sin temperature/prefill | `AiService` con ai_logs y tope diario; pre-evaluación async y match validados con Jackson |
| B3 | (ninguna nueva) | Flujos n8n y worker Stagehand; API de Stagehand 4.3 verificada leyendo sus tipos y README | Worker compila; 17/17 tests |
| F1 | code-review | Revisar los endpoints nuevos que pide la app (galería, borrar borrador) junto con el diff del cliente | Se cambió a D-28: alta como borrador + `PUT /api/pets/{id}/photos` atómico (una moderación por envío); `deletePhoto` vuelve a moderar; 20/20 tests |
| F1 | artifact-design (vía quickstart del Artifact) | Guía publicada del modelo de datos: decisiones, pruebas y criterio de "lista" | https://claude.ai/artifact/HRdiphme71C66X52KqqYym |
| F1 | ponytail (nivel full, toda la sesión) | Cambio mínimo y completo en los endpoints nuevos y en la migración del contrato | Un solo `PUT /photos` en vez de reordenar + borrar por separado; se reusó `editable()`, `resubmit()` y `deleteAfterCommit()` sin capas nuevas |
