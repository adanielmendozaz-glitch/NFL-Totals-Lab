# Verificación técnica V1.6.1

- Modelos Kotlin de simulación compilados de forma aislada con kotlinc.
- DataCenter.kt, Csv.kt, tipos Kotlin y coroutines compilados contra stubs locales de Android/JSON para detectar errores de tipos. Es una prueba de compilación parcial, **no** un build Android de Gradle.
- 100, 500 y 1,000 simulaciones sintéticas: porcentajes consistentes, integridad de jugadas, repetibilidad con la misma semilla, ausentes excluidos, ninguna semana futura en la calibración de juegos previos.
- EPA The Margin usa signo correcto: off EPA y def EPA permitido (menor = mejor defensa). Se utilizan también desgloses por pase y carrera cuando están disponibles.
- Observaciones importantes: los lotes sintéticos siguen produciendo puntos bajos en el escenario de prueba. El modelo NO se considera calibrado para apuestas. La nueva interfaz resalta divergencias mayores del 25% respecto a un PPG histórico de referencia.
- Por seguridad: los acumulados sin cutoff certificado (semana 0) no entran en el modelo pregame de juegos pasados. Los datos semanales sí entran.
- Compilación APK Android de GitHub Actions e instalación en el dispositivo PENDIENTES.
