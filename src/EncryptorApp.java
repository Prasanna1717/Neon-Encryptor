import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;

public class EncryptorApp extends JFrame {
    private JTextArea inputArea;
    private JButton encryptBtn, viewBtn, clearBtn, exitBtn;
    private Connection conn;
    private BinaryLoadingPanel loadingPanel;
    private JLayeredPane layeredPane;
    private JPanel mainPanel;


    // UI Theme Constants
    private static final Color BG_COLOR = new Color(224, 247, 250);
    private static final Color ACCENT_COLOR = new Color(0, 191, 255);
    private static final Color TEXT_AREA_BG = Color.WHITE;
    private static final Color BUTTON_FG = Color.WHITE;
    private static final Font APP_FONT = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font TEXT_AREA_FONT = new Font("Consolas", Font.PLAIN, 14);

    public EncryptorApp() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }

        initUI();
        connectDatabase();
        createTableIfNotExists();

        // Action Listeners
        encryptBtn.addActionListener(e -> startEncryptionTask());
        viewBtn.addActionListener(e -> startViewTask());
        clearBtn.addActionListener(e -> startClearTask());
        exitBtn.addActionListener(e -> exitApplication());
    }

    private void initUI() {
        setTitle("Neon Encryptor");
        setSize(750, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main Panel Setup (as before)
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(BG_COLOR);

        inputArea = new JTextArea();
        inputArea.setFont(TEXT_AREA_FONT);
        inputArea.setBackground(TEXT_AREA_BG);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(inputArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ACCENT_COLOR, 2, true),
            "Enter Text Here", TitledBorder.LEFT, TitledBorder.TOP, APP_FONT, ACCENT_COLOR
        ));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setOpaque(false);

        encryptBtn = new JButton("START ENCRYPT");
        viewBtn = new JButton("View in SQLite");
        clearBtn = new JButton("Clear Records");
        exitBtn = new JButton("Exit");

        styleButton(encryptBtn); styleButton(viewBtn);
        styleButton(clearBtn); styleButton(exitBtn);

        btnPanel.add(encryptBtn); btnPanel.add(viewBtn);
        btnPanel.add(clearBtn); btnPanel.add(exitBtn);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        // Loading Panel and Layered Pane for Overlay
        loadingPanel = new BinaryLoadingPanel();
        loadingPanel.setVisible(false);

        layeredPane = new JLayeredPane();
        // ** THE FIX IS HERE: Add components and a ComponentAdapter to handle resizing **
        layeredPane.add(mainPanel, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(loadingPanel, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Ensure both layers are always the same size as the pane
                mainPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                loadingPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            }
        });

        setContentPane(layeredPane);
    }
    
    private void styleButton(JButton button) {
        button.setFont(APP_FONT);
        button.setBackground(ACCENT_COLOR);
        button.setForeground(BUTTON_FG);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setToolTipText(button.getText());
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    }
    
    // ** THE FIX IS HERE: A much more robust way to enable/disable controls **
    private void setControlsEnabled(Container container, boolean enable) {
        for (Component c : container.getComponents()) {
            c.setEnabled(enable);
            if (c instanceof Container) {
                setControlsEnabled((Container) c, enable);
            }
        }
    }
    
    // Task Control Methods

    private void startEncryptionTask() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Input cannot be empty!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setControlsEnabled(mainPanel, false);
        loadingPanel.start();
        new EncryptWorker(text).execute();
    }

    private void startViewTask() {
        setControlsEnabled(mainPanel, false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new ViewWorker().execute();
    }

    private void startClearTask() {
        int response = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete ALL records?\nThis action cannot be undone.", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (response == JOptionPane.YES_OPTION) {
            setControlsEnabled(mainPanel, false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new ClearWorker().execute();
        }
    }

    private void exitApplication() {
        int response = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Confirm Exit", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (response == JOptionPane.YES_OPTION) {
            dispose();
        }
    }

    // --- SwingWorker Classes (No changes) ---
    class EncryptWorker extends SwingWorker<Boolean, Void> {
        private final String textToEncrypt;
        public EncryptWorker(String text) { this.textToEncrypt = text; }
        @Override
        protected Boolean doInBackground() throws Exception {
            Thread.sleep(2500);
            String encrypted = encrypt(textToEncrypt);
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO messages (encrypted) VALUES (?)")) {
                pstmt.setString(1, encrypted);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) { e.printStackTrace(); return false; }
        }

        @Override
        protected void done() {
            try {
                if (get()) {
                    JOptionPane.showMessageDialog(EncryptorApp.this, "Text encrypted & saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    inputArea.setText("");
                } else {
                    JOptionPane.showMessageDialog(EncryptorApp.this, "Failed to save data!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                loadingPanel.stop();
                setControlsEnabled(mainPanel, true);
            }
        }
    }
    
    class ViewWorker extends SwingWorker<DefaultTableModel, Void> {
        @Override
        protected DefaultTableModel doInBackground() throws Exception {
            Thread.sleep(1000);
            String[] columnNames = {"ID", "Encrypted Text", "Decrypted Text"};
            DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT id, encrypted FROM messages ORDER BY id DESC")) {
                while (rs.next()) { model.addRow(new Object[]{rs.getInt("id"), rs.getString("encrypted"), decrypt(rs.getString("encrypted"))}); }
            }
            return model;
        }

        @Override
        protected void done() {
            try {
                DefaultTableModel model = get();
                if (model.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(EncryptorApp.this, "No records found.", "Information", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showRecordsDialog(model);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                setControlsEnabled(mainPanel, true);
                setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    class ClearWorker extends SwingWorker<Integer, Void> {
        @Override
        protected Integer doInBackground() throws Exception {
            Thread.sleep(1000);
            try (Statement stmt = conn.createStatement()) {
                return stmt.executeUpdate("DELETE FROM messages");
            }
        }
        @Override
        protected void done() {
            try {
                int deletedRows = get();
                JOptionPane.showMessageDialog(EncryptorApp.this, "Successfully cleared " + deletedRows + " record(s).", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                setControlsEnabled(mainPanel, true);
                setCursor(Cursor.getDefaultCursor());
            }
        }
    }
    
    // --- BinaryLoadingPanel Class (No changes) ---
    class BinaryLoadingPanel extends JPanel implements ActionListener {
        private final Timer timer;
        private final ArrayList<BinaryChar> binaryChars;
        private final Random random = new Random();

        public BinaryLoadingPanel() {
            this.binaryChars = new ArrayList<>();
            this.timer = new Timer(50, this);
            setOpaque(false);
        }

        public void start() {
            this.binaryChars.clear();
            setVisible(true);
            timer.start();
        }

        public void stop() {
            timer.stop();
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!timer.isRunning()) return;

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            for (int i = 0; i < 5; i++) {
                binaryChars.add(new BinaryChar());
            }

            g2d.setFont(TEXT_AREA_FONT.deriveFont(Font.BOLD, 18f));
            for (int i = binaryChars.size() - 1; i >= 0; i--) {
                BinaryChar bc = binaryChars.get(i);
                if (bc.update()) {
                    binaryChars.remove(i);
                } else {
                    bc.draw(g2d);
                }
            }
            
            g2d.setFont(APP_FONT.deriveFont(32f));
            FontMetrics fm = g2d.getFontMetrics();
            String loadingText = "ENCRYPTING...";
            int x = (getWidth() - fm.stringWidth(loadingText)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2d.setColor(ACCENT_COLOR);
            g2d.drawString(loadingText, x, y);

            g2d.dispose();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            repaint();
        }

        private class BinaryChar {
            private final char character;
            private final int x;
            private int y;
            private final int speed;
            private int alpha = 255;

            BinaryChar() {
                this.character = random.nextBoolean() ? '0' : '1';
                this.x = random.nextInt(getWidth());
                this.y = 0;
                this.speed = random.nextInt(5) + 3;
            }

            boolean update() {
                y += speed;
                alpha = Math.max(0, alpha - 4);
                return y > getHeight() || alpha == 0;
            }

            void draw(Graphics2D g2d) {
                g2d.setColor(new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), 255, alpha));
                g2d.drawString(String.valueOf(character), x, y);
            }
        }
    }
    
    
    // --- Unchanged Methods ---
    private void showRecordsDialog(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(25);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);

        JDialog dialog = new JDialog(this, "Saved Records", true);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    private void connectDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:encrypted_data.db");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database connection failed!", "Fatal Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void createTableIfNotExists() {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, encrypted TEXT NOT NULL)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String encrypt(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes());
    }

    private String decrypt(String input) {
        try {
            return new String(Base64.getDecoder().decode(input));
        } catch (IllegalArgumentException e) {
            return "!! INVALID DATA !!";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EncryptorApp().setVisible(true));
    }
}