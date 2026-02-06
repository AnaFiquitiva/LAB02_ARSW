package co.eci.snake.core;

import java.util.HashMap;
//Se elimina la importacion de HashSet ya que se implementa ConcurrentHashMap
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Board {
  private final int width;
  private final int height;

  // Se inicializan los conjuntos de forma concurrente
  private final Set<Position> mice = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final Set<Position> obstacles = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final Set<Position> turbo = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final Map<Position, Position> teleports = new HashMap<>(); // Read-only after init

  // Agregamos un nuevo resultado para representar la muerte por colisión
  public enum MoveResult {
    MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED, SNAKE_DIED
  }

  public Board(int width, int height) {
    if (width <= 0 || height <= 0)
      throw new IllegalArgumentException("Board dimensions must be positive");
    this.width = width;
    this.height = height;
    for (int i = 0; i < 6; i++)
      mice.add(randomEmpty());
    for (int i = 0; i < 4; i++)
      obstacles.add(randomEmpty());
    for (int i = 0; i < 3; i++)
      turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  // Retornamos la vista directa, sin el uso de syncronized, ya que son
  // concurrentes y seguras para iterar
  public Set<Position> mice() {
    return mice;
  }

  public Set<Position> obstacles() {
    return obstacles;
  }

  public Set<Position> turbo() {
    return turbo;
  }

  public Map<Position, Position> teleports() {
    return teleports;
  }

  /**
   * Ejecuta un paso de movimiento.
   * Se añade el parámetro allSnakes para verificar colisiones entre ellas.
   */
  public MoveResult step(Snake snake, List<Snake> allSnakes) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir = snake.direction();

    // Calcular siguiente posición con wrap-around
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    // 1. REBOTE: Si choca con un obstáculo naranja, solo retorna HIT_OBSTACLE
    if (obstacles.contains(next))
      return MoveResult.HIT_OBSTACLE;

    // 2. MUERTE: Si choca con cualquier serpiente (incluida ella misma)
    for (Snake s : allSnakes) {
      if (s.snapshot().contains(next)) {
        return MoveResult.SNAKE_DIED;
      }
    }

    // Lógica de Teleport
    boolean teleported = false;
    if (teleports.containsKey(next)) {
      next = teleports.get(next);
      teleported = true;
    }

    boolean ateMouse = mice.remove(next);
    boolean ateTurbo = turbo.remove(next);

    snake.advance(next, ateMouse);

    if (ateMouse) {
      mice.add(randomEmpty());
      obstacles.add(randomEmpty());
      if (ThreadLocalRandom.current().nextDouble() < 0.2)
        turbo.add(randomEmpty());
    }

    if (ateTurbo)
      return MoveResult.ATE_TURBO;
    if (ateMouse)
      return MoveResult.ATE_MOUSE;
    if (teleported)
      return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  private void createTeleportPairs(int pairs) {
    for (int i = 0; i < pairs; i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      teleports.put(a, b);
      teleports.put(b, a);
    }
  }

  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width * height * 2)
        break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }
}