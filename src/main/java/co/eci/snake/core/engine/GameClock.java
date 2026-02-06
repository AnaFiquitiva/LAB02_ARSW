package co.eci.snake.core.engine;

import javax.swing.Timer;
import java.awt.event.ActionListener;

import co.eci.snake.core.sync.PauseController;

/**
 * El GameClock actúa como el motor de refresco de la interfaz.
 * Utiliza un Timer de Swing que es seguro para hilos de UI.
 */
public final class GameClock {

    private final Timer timer;
    private final int fps;
    private final PauseController pauseController;

    /**
     * @param fps    Cuadros por segundo (ej. 60)
     * @param onTick Acción a ejecutar en cada refresco (normalmente repaint())
     */
    public GameClock(int fps, Runnable onTick) {
        this(fps, onTick, null);
    }

    public GameClock(int fps, Runnable onTick, PauseController pauseController) {
        this.fps = fps;
        this.pauseController = pauseController;
        // El Timer ejecuta la acción cada (1000/fps) milisegundos
        this.timer = new Timer(1000 / fps, e -> {
            if (this.pauseController == null || !this.pauseController.isPaused()) {
                onTick.run();
            }
        });
    }

    /**
     * Inicia el reloj del juego.
     */
    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    /**
     * Pausa el refresco de la pantalla.
     */
    public void pause() {
        timer.stop();
    }

    /**
     * Reanuda el refresco de la pantalla.
     */
    public void resume() {
        timer.start();
    }

    /**
     * Verifica si el reloj está activo.
     */
    public boolean isRunning() {
        return timer.isRunning();
    }

    /**
     * Permite ajustar la velocidad del juego dinámicamente si fuera necesario.
     */
    public void setSpeed(int newFps) {
        timer.setDelay(1000 / newFps);
    }
}