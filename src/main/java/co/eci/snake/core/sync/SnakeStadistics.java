package co.eci.snake.core.sync;

import co.eci.snake.core.Snake;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recolector de estadísticas del juego.
 * Thread-safe usando CopyOnWriteArrayList para registro de muertes.
 */
public final class SnakeStatistics {

    /**
     * Información de una serpiente muerta.
     */
    public static final class DeadSnake {
        public final int snakeId;
        public final long deathTime;
        public final int finalLength;
        public final String cause;

        public DeadSnake(int snakeId, long deathTime, int finalLength, String cause) {
            this.snakeId = snakeId;
            this.deathTime = deathTime;
            this.finalLength = finalLength;
            this.cause = cause;
        }

        @Override
        public String toString() {
            return String.format("Snake #%d (length: %d) - %s", snakeId, finalLength, cause);
        }
    }

    /**
     * Snapshot de estadísticas en un momento dado.
     */
    public static final class Snapshot {
        public final Snake longestSnake;
        public final int longestLength;
        public final DeadSnake firstDead;
        public final int totalSnakes;
        public final int aliveSnakes;

        public Snapshot(Snake longestSnake, int longestLength, DeadSnake firstDead,
                        int totalSnakes, int aliveSnakes) {
            this.longestSnake = longestSnake;
            this.longestLength = longestLength;
            this.firstDead = firstDead;
            this.totalSnakes = totalSnakes;
            this.aliveSnakes = aliveSnakes;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ESTADÍSTICAS DEL JUEGO \n");
            sb.append(String.format("Serpientes vivas: %d/%d\n", aliveSnakes, totalSnakes));

            if (longestSnake != null) {
                sb.append(String.format("\nSerpiente más larga:\n"));
                sb.append(String.format("  Longitud: %d segmentos\n", longestLength));
            }

            if (firstDead != null) {
                sb.append(String.format("\nPrimera muerte:\n"));
                sb.append(String.format("  %s\n", firstDead));
                sb.append(String.format("  Tiempo: %.2f segundos\n", firstDead.deathTime / 1000.0));
            } else {
                sb.append("\nAún no hay bajas\n");
            }

            return sb.toString();
        }
    }

    private final List<DeadSnake> deadSnakes = new CopyOnWriteArrayList<>();
    private final long startTime;

    public SnakeStatistics() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Registra la muerte de una serpiente.
     * Thread-safe.
     */
    public void recordDeath(int snakeId, int finalLength, String cause) {
        long deathTime = System.currentTimeMillis() - startTime;
        deadSnakes.add(new DeadSnake(snakeId, deathTime, finalLength, cause));
    }

    /**
     * Captura un snapshot consistente de las estadísticas.
     *
     * @param snakes Lista de todas las serpientes (vivas y muertas)
     * @return Snapshot con las estadísticas actuales
     */
    public Snapshot captureSnapshot(List<Snake> snakes) {
        Objects.requireNonNull(snakes, "snakes");

        Snake longestSnake = null;
        int longestLength = 0;

        // Encontrar la serpiente más larga
        for (Snake snake : snakes) {
            int length = snake.getLength();
            if (length > longestLength) {
                longestLength = length;
                longestSnake = snake;
            }
        }

        // Primera serpiente muerta (si existe)
        DeadSnake firstDead = deadSnakes.isEmpty() ? null : deadSnakes.get(0);

        int aliveSnakes = snakes.size() - deadSnakes.size();

        return new Snapshot(longestSnake, longestLength, firstDead, snakes.size(), aliveSnakes);
    }

    /**
     * Reinicia las estadísticas.
     */
    public void reset() {
        deadSnakes.clear();
    }

    /**
     * Obtiene todas las muertes registradas.
     */
    public List<DeadSnake> getDeadSnakes() {
        return List.copyOf(deadSnakes);
    }
}