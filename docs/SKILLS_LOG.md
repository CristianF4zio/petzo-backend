# Skills log

| Sprint | Skill | Para qué la usaste | Resultado |
| --- | --- | --- | --- |
| B0 | (ninguna nueva) | Scaffolding con Spring Initializr y Flyway | — |
| B1 | api-and-interface-design | Contrato REST de §17: errores ProblemDetail con `code`, validación en el borde, paginación `{items,page,pageSize,total}` | 27 rutas documentadas en Swagger |
| B2 | claude-api | SDK de Java para Haiku 5.5: `AnthropicOkHttpClient`, `system`, `outputConfig(effort LOW)`, `usage()`, chequeo de `stopReason` REFUSAL, sin temperature/prefill | `AiService` con ai_logs y tope diario; pre-evaluación async y match validados con Jackson |
| B3 | (ninguna nueva) | Flujos n8n y worker Stagehand; API de Stagehand 4.3 verificada leyendo sus tipos y README | Worker compila; 17/17 tests |
| F1 | code-review | Revisar los endpoints nuevos que pide la app (galería, borrar borrador) junto con el diff del cliente | Se cambió a D-28: alta como borrador + `PUT /api/pets/{id}/photos` atómico (una moderación por envío); `deletePhoto` vuelve a moderar; 20/20 tests |
