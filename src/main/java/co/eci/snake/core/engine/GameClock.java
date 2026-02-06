package co.eci.snake.core.engine;

import co.eci.snake.core.sync.PauseController;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reloj del juego que controla la frecuencia de actualización visual.
 * Integrado con PauseController.
 */
public final class GameClock {
  private final ScheduledExecutorService scheduler;
  private final Runnable tickCallback;
  private final long periodMs;
  private final PauseController pauseController;

  /**
   * Crea un reloj del juego.
   *
   * @param fps Fotogramas por segundo deseados
   * @param tickCallback Acción a ejecutar en cada tick (típicamente repaint)
   * @param pauseController Controlador de pausa
   */
  public GameClock(int fps, Runnable tickCallback, PauseController pauseController) {
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "GameClock");
      t.setDaemon(true);
      return t;
    });
    this.tickCallback = tickCallback;
    this.periodMs = 1000 / fps;
    this.pauseController = pauseController;
  }

  /**
   * Inicia el reloj.
   */
  public void start() {
    pauseController.start();
    scheduler.scheduleAtFixedRate(
            tickCallback,
            0,
            periodMs,
            TimeUnit.MILLISECONDS
    );
  }

  /**
   * Pausa el reloj (la animación se detiene pero el scheduler sigue corriendo).
   */
  public void pause() {
    pauseController.pause();
  }

  /**
   * Reanuda el reloj.
   */
  public void resume() {
    pauseController.resume();
  }

  /**
   * Detiene el reloj completamente.
   */
  public void stop() {
    pauseController.stop();
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}