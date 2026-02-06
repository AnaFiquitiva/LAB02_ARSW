package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;
import co.eci.snake.core.sync.PauseController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final PauseController pauseController;
  private final GameClock clock;
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();
  private boolean isFirstStart = true;

  public SnakeApp() {
    super("The Snake Race");
    // El tablero se inicializa con dimensiones fijas
    this.board = new Board(35, 28);

    // Bloqueo inicial de hilos para que no arranquen solos
    SnakeRunner.isPaused = true;

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }

    this.gamePanel = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Iniciar");

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(actionButton, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    // Reloj de refresco visual
    this.pauseController = new PauseController();
    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint), pauseController);

    // Uso de Virtual Threads para autonomía de cada serpiente
    var exec = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, snakes)));

    actionButton.addActionListener((ActionEvent e) -> togglePause());

    setupKeyboardActions();

    setVisible(true);
  }

  private void setupKeyboardActions() {
    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    var player = snakes.get(0);
    bindKey(player, "LEFT", Direction.LEFT);
    bindKey(player, "RIGHT", Direction.RIGHT);
    bindKey(player, "UP", Direction.UP);
    bindKey(player, "DOWN", Direction.DOWN);

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      bindKey(p2, "A", Direction.LEFT);
      bindKey(p2, "D", Direction.RIGHT);
      bindKey(p2, "W", Direction.UP);
      bindKey(p2, "S", Direction.DOWN);
    }
  }

  private void bindKey(Snake s, String key, Direction dir) {
    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
    gamePanel.getActionMap().put(key, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        s.turn(dir);
      }
    });
  }

  private void togglePause() {
    if (isFirstStart) {
      isFirstStart = false;
      synchronized (SnakeRunner.gameMonitor) {
        SnakeRunner.isPaused = false;
        SnakeRunner.gameMonitor.notifyAll();
      }
      clock.start();
      actionButton.setText("Pausar");
      return;
    }

    if (!SnakeRunner.isPaused) {
      SnakeRunner.isPaused = true;
      clock.pause();
      actionButton.setText("Reanudar");

      Snake longest = snakes.stream()
          .filter(s -> !s.isDead())
          .max((s1, s2) -> Integer.compare(s1.getLength(), s2.getLength()))
          .orElse(snakes.get(0));

      String worstInfo = (SnakeRunner.firstDead != null)
          ? "La peor serpiente (murió primero) medía: " + SnakeRunner.firstDead.getLength()
          : "Ninguna serpiente ha muerto aún.";

      // Mensaje sin la línea divisoria solicitado
      String stats = String.format(
          "ESTADÍSTICAS DE CARRERA \n" +
              "Serpiente viva más larga: %d segmentos\n" +
              "%s\n\n" +
              "¿Desea continuar o finalizar la carrera?",
          longest.getLength(), worstInfo);

      Object[] options = { "Continuar", "Finalizar" };
      int selection = JOptionPane.showOptionDialog(
          this,
          stats,
          "Juego Pausado",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.INFORMATION_MESSAGE,
          null,
          options,
          options[0]);

      if (selection == 1) {
        System.exit(0);
      }

    } else {
      synchronized (SnakeRunner.gameMonitor) {
        SnakeRunner.isPaused = false;
        SnakeRunner.gameMonitor.notifyAll();
      }
      clock.resume();
      actionButton.setText("Pausar");
    }
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles())
        g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);

      g2.setColor(Color.BLACK);
      for (var p : board.mice())
        g2.fillOval(p.x() * cell + 4, p.y() * cell + 4, cell - 8, cell - 8);

      g2.setColor(Color.RED);
      for (var p : board.teleports().keySet())
        g2.fillOval(p.x() * cell + 6, p.y() * cell + 6, cell - 12, cell - 12);

      g2.setColor(Color.BLUE);
      for (var p : board.turbo())
        g2.fillRect(p.x() * cell + 6, p.y() * cell + 6, cell - 12, cell - 12);

      var snakesList = snakesSupplier.get();
      int idx = 0;
      for (Snake s : snakesList) {
        var body = s.snapshot(); // Uso de snapshot para evitar Tearing
        int i = 0;
        for (Position p : body) {
          Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
          if (s.isDead())
            base = Color.GRAY;

          int shade = Math.max(0, 40 - i * 2);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
          i++;
        }
        idx++;
      }
      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}