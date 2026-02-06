package co.eci.snake.core.sync;

import co.eci.snake.core.GameState;

/**
 * Controlador centralizado para pausar y reanudar el juego.
 * Usa wait/notify para evitar busy-waiting.
 * Thread-safe.
 */
public final class PauseController {
    private final Object monitor = new Object();
    private volatile GameState state = GameState.STOPPED;

    /**
     * Pausa el juego. Todos los hilos bloqueados en waitIfPaused() esperarán.
     */
    public void pause() {
        synchronized (monitor) {
            state = GameState.PAUSED;
        }
    }

    /**
     * Reanuda el juego. Despierta todos los hilos en espera.
     */
    public void resume() {
        synchronized (monitor) {
            state = GameState.RUNNING;
            monitor.notifyAll(); // Despertar todos los hilos pausados
        }
    }

    /**
     * Inicia el juego desde estado STOPPED.
     */
    public void start() {
        synchronized (monitor) {
            state = GameState.RUNNING;
            monitor.notifyAll();
        }
    }

    /**
     * Detiene el juego completamente.
     */
    public void stop() {
        synchronized (monitor) {
            state = GameState.STOPPED;
            monitor.notifyAll();
        }
    }

    /**
     * Bloquea el hilo actual si el juego está pausado.
     * Los hilos deben llamar esto en cada iteración de su loop.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public void waitIfPaused() throws InterruptedException {
        synchronized (monitor) {
            // Loop while para evitar lost wakeups
            while (state == GameState.PAUSED) {
                monitor.wait();
            }
        }
    }

    /**
     * Verifica si el juego está en estado RUNNING.
     */
    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    /**
     * Verifica si el juego está pausado.
     */
    public boolean isPaused() {
        return state == GameState.PAUSED;
    }

    /**
     * Obtiene el estado actual.
     */
    public GameState getState() {
        return state;
    }
}
