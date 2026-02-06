package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Snake;
import co.eci.snake.core.Direction;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestiona la lógica de movimiento y autonomía de cada serpiente en un hilo
 * independiente.
 * Implementa el control de pausa mediante wait/notify y el registro de
 * colisiones.
 */
public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final List<Snake> snakes; // Referencia a todas las serpientes para detectar choques
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  private final Object monitor = new Object();

  // REQUISITOS DEL LABORATORIO: Monitor global y estados compartidos
  public static final Object gameMonitor = new Object();
  public static volatile boolean isPaused = false;
  public static volatile Snake firstDead = null; // Estadística: Peor serpiente

  public SnakeRunner(Snake snake, Board board, List<Snake> snakes) {
    this.snake = snake;
    this.board = board;
    this.snakes = snakes;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {

        // PARTE I y II: Sincronización mediante wait() (Evita el Busy-Waiting)
        // El hilo se bloquea aquí si el juego está pausado, liberando la CPU.
        synchronized (gameMonitor) {
          while (isPaused) {
            gameMonitor.wait();
          }
        }

        maybeTurn();

        // Ejecutar el paso en el tablero verificando colisiones con el resto de
        // serpientes
        var res = board.step(snake, snakes);

        if (res == Board.MoveResult.SNAKE_DIED) {
          // Lógica de muerte: Marcamos la serpiente y registramos si fue la primera
          snake.die();
          synchronized (gameMonitor) {
            if (firstDead == null) {
              firstDead = snake;
            }
          }
          // El hilo termina su ejecución al morir la serpiente
          break;

        } else if (res == Board.MoveResult.HIT_OBSTACLE) {
          // Rebote: Si choca con un cuadro naranja, gira aleatoriamente
          randomTurn();

        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        }

        // Gestión de velocidad (normal vs turbo)
        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0)
          turboTicks--;

        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      // Manejo de interrupción para cierre seguro del hilo
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Decide aleatoriamente si la serpiente debe girar en este tick.
   */
  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) {
      randomTurn();
    }
  }

  /**
   * Cambia la dirección de la serpiente de forma aleatoria.
   */
  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}