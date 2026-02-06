package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;
// Se implementan las colas concurrentes para que sean seguras para hilos
import java.util.concurrent.ConcurrentLinkedDeque;

public final class Snake {
  // Se implementa la cola concurrente para que sea segura para hilos
  private final Deque<Position> body = new ConcurrentLinkedDeque<>();
  private volatile Direction direction;
  private int maxLength = 5;
  private boolean dead = false;

  private Snake(Position start, Direction dir) {
    body.addFirst(start);
    this.direction = dir;
  }

  public static Snake of(int x, int y, Direction dir) {
    return new Snake(new Position(x, y), dir);
  }

  public Direction direction() {
    return direction;
  }

  // Sincronizado para evitar cambios de dirección inconsistentes
  public synchronized void turn(Direction dir) {
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  public synchronized Position head() {
    return body.peekFirst();
  }

  // REQUISITO: Retorna una copia profunda para que la UI no vea estados
  // intermedios
  public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body);
  }

  // REQUISITO: Región crítica para la actualización del cuerpo
  public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow)
      maxLength++;
    while (body.size() > maxLength)
      body.removeLast();
  }

  public synchronized void die() {
    this.dead = true;
  }

  public synchronized boolean isDead() {
    return dead;
  }

  public synchronized int getLength() {
    return body.size();
  }

}
