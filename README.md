# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**

Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

## Integrantes

- **Ana Gabriela Fiquitiva Poveda**
- **Miguel Ángel Monroy Cárdenas**

---

> **Nota**: La solución de la Parte I (PrimeFinder con wait/notify) se encuentra disponible en el archivo comprimido adjunto.

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- **SO**: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente 0 (Jugador 1).
  - **WASD**: serpiente 1 (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego

- **N serpientes** corren de forma autónoma, cada una en su propio hilo.
- **Ratones**: al comer uno, la serpiente crece y aparece un nuevo obstáculo.
- **Obstáculos**: si la cabeza entra en un obstáculo, ocurre un rebote (cambio de dirección).
- **Teletransportadores**: entrar por uno te desplaza hacia su par correspondiente.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene velocidad aumentada de forma temporal.
- **Wrap-around**: el tablero es circular; los bordes están conectados entre sí.

---

## Arquitectura del Proyecto

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botones
```

---

## Actividades del laboratorio

### Parte I — wait/notify en un programa multi-hilo

#### Diseño de sincronización en PrimeFinder

Se implementó un esquema de coordinación basado en un monitor único para gestionar la pausa de los hilos buscadores de primos.

- **Lock y Condición**: Se utilizó un objeto monitor compartido y una variable booleana para representar el estado de pausa.
- **Mecanismo de Suspensión**: Se evitó el uso de espera activa empleando `monitor.wait()`. Los hilos verifican la condición de pausa dentro de un bloque sincronizado y liberan el procesador mientras esperan la señal de reanudación.
- **Evitando Lost Wakeups**: El uso de un bucle `while` para verificar la condición de pausa garantiza que los hilos no continúen su ejecución por despertares accidentales. El controlador utiliza `monitor.notifyAll()` para despertar a todos los hilos simultáneamente.

---

### Parte II — SnakeRace concurrente (núcleo del laboratorio)

#### 1. Análisis de concurrencia

- **Hilos y Autonomía**: El código utiliza Virtual Threads (Java 21) para dar autonomía a cada serpiente. Cada instancia de `SnakeRunner` se ejecuta en su propio hilo ligero. Ésta implementa la interfaz runnable, lo que le permite a una tarea ser ejecutada por un thread. Dado que cada serpiente se crea en un nuevo thread utilizando `newVirtualThreadPerTaskExecutor` y cada una tiene su propio contexto del tablero, su posición y la dirección en que se dirigen es posible la autonomía de cada una.
- **Condiciones de Carrera**: Se identificaron riesgos en la actualización del tablero cuando múltiples serpientes intentan consumir el mismo ratón o generar nuevos obstáculos al mismo tiempo.
  Una forma de evitar este tipo de errores es utilizando colecciones o estructuras thread-safe o seguras para hilos. Algunas de las colecciones y estructuras que no son thread-safe pero se usan en el codigo son:
  - ArrayDeque -> Usado en Snake
  - HashSet, HashMap -> Usado en Board (junto con un synchronized)

- **Inconsistencia Visual (Tearing)**: La interfaz gráfica leía la estructura del cuerpo de la serpiente mientras los hilos de lógica la modificaban, resultando en representaciones visuales incompletas o dañadas.

#### 2. Colecciones No Seguras y Sustitución

Las estructuras de datos originales no estaban diseñadas para acceso concurrente, lo que provocaba excepciones de modificación y estados inconsistentes.

- **Sustitución en Board**: Se identificó que `HashSet` y `HashMap` no son seguros. Se reemplazaron por `ConcurrentHashMap` (usando un mapa como conjunto) para permitir que múltiples hilos de serpientes verifiquen y modifiquen obstáculos o ratones de forma segura.
- **Sustitución en Snake**: La `ArrayDeque` que almacena el cuerpo se protegió mediante bloques sincronizados para asegurar que el método `snapshot()` entregue una copia íntegra a la UI mientras el hilo de la serpiente añade nuevas posiciones.
- **Eliminación de syncronized innecesario**: Se eliminó el syncronized de teleport() ya que, como los datos no cambian, se puede considerar thread-safe
- **Sustitución de ArrayDeque**: Se reemplazó el uso de ArrayDeque que puede llegar a cruzar o sobreescribir datos si se hace por dos hilos en un mismo espacio de memoria y usar un ConcurrentLinkedDeque en su lugar.
- **Medida preventiva**: Como medida preventiva se cambia la ArrayList de Snakes por una CopyOnWriteArrayList que permite iterar sobre las Snakes de forma mas segura

#### 3. Regiones Críticas Detalladas

| Región Crítica | Hilos Involucrados | Justificación |
|----------------|-------------------|---------------|
| `Board.step()` | Múltiples `SnakeRunner` | Evita que dos serpientes consuman el mismo ratón o que la generación de obstáculos sobreescriba datos existentes de forma inconsistente. |
| `Snake.advance()` | `SnakeRunner` y `UI` | Garantiza que la estructura interna del cuerpo no cambie mientras se está generando un snapshot para el dibujado. |
| `SnakeRunner.isPaused` | `SnakeRunner` y `UI` | Asegura que todos los hilos trabajadores vean el cambio de estado de pausa de manera inmediata y consistente. |

#### 4. Eliminación de Espera Activa

Se eliminó el ciclo infinito de verificación de estado (busy-wait) para optimizar el uso de la CPU.

**Antes (Espera Activa):**

```java
while (isPaused) {
    // El hilo sigue ejecutándose en el procesador sin hacer nada útil
}
```

**Después (Sincronización con Monitor):**

```java
synchronized (gameMonitor) {
    while (isPaused) {
        gameMonitor.wait(); // El hilo se suspende y libera el procesador
    }
}
```

#### 5. Data Races Específicas Detectadas

- **Race 1: Consumo de Ítems**: Dos serpientes llegaban al mismo ratón simultáneamente. Ambas detectaban el ratón antes de que `remove()` terminara, causando que ambas "comieran" el mismo objeto. **Síntoma**: Crecimiento doble e inconsistencia en la cuenta de ratones.
- **Race 2: Actualización de Dirección**: El hilo de la UI enviaba una nueva dirección mientras el hilo de la serpiente ejecutaba `step()`. **Síntoma**: La serpiente se movía en una dirección prohibida (ej. de Arriba a Abajo directamente) debido a lecturas sucias.
- **Race 3: Renderizado de Cuerpo**: La UI recorría la lista de posiciones mientras la serpiente añadía la cabeza. **Síntoma**: `ConcurrentModificationException` y desaparición momentánea de segmentos en pantalla.

#### 6. Control de ejecución seguro (UI)

- **Ciclo de Vida**: Se implementó la lógica de Iniciar / Pausar / Reanudar. El juego inicia en pausa para permitir la configuración inicial.
- **Estadísticas de Pausa**: Al pausar, se despliega un diálogo con:
  - La longitud de la serpiente viva más larga.
  - La longitud de la "peor" serpiente (la primera en morir tras un choque).
- **Interacción**: El diálogo incluye botones para "Continuar" y "Finalizar", permitiendo una gestión fluida de la aplicación.

---

## Conclusiones

Este laboratorio permitió validar la efectividad de los mecanismos de sincronización de Java para resolver problemas complejos de concurrencia. La implementación de monitores con `wait/notify` eliminó el desperdicio de recursos por espera activa, mientras que el uso de snapshots sincronizados y colecciones concurrentes garantizó la integridad del sistema. El uso de Virtual Threads de Java 21 demostró ser una solución eficiente para gestionar múltiples agentes autónomos con un impacto mínimo en el rendimiento.
