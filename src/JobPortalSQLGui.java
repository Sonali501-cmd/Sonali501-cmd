// JobPortalSQLGui.java
// Single-file GUI + MySQL persistence for your job portal.
// Requirements:
//  - MySQL server accessible at sql100.infinityfree.com
//  - DB user: if0_40508232, password: Arnim9412
//  - Database name: f0_40508232_arnimjha (must exist or be created by your host admin)
//  - Add MySQL Connector/J jar to classpath (mysql-connector-java-X.Y.Z.jar)

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// ================== MAIN CLASS ==================
public class JobPortalSQLGui extends JFrame {

    // ====== CONFIG: change if needed ======
    private static final String DB_HOST = "localhost";
private static final String DB_NAME = "job_portal";
private static final String DB_USER = "root";
private static final String DB_PASS = "";
private static final String JDBC_URL = "jdbc:mysql://" + DB_HOST + "/" + DB_NAME + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // ====== GLOBAL THEME ======
    private static final Color BG_DARK = new Color(25, 25, 25);
    private static final Color BG_PANEL = new Color(35, 35, 35);
    private static final Color BG_HEADER = new Color(20, 20, 20);
    private static final Color ACCENT = new Color(102, 153, 255);

    private static final Font APP_FONT   = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font TAB_FONT   = new Font("Segoe UI", Font.PLAIN, 14);

    private static void applyGlobalStyles() {
        UIManager.put("Label.font", APP_FONT);
        UIManager.put("Button.font", APP_FONT);
        UIManager.put("TextField.font", APP_FONT);
        UIManager.put("PasswordField.font", APP_FONT);
        UIManager.put("TextArea.font", APP_FONT);
        UIManager.put("Table.font", APP_FONT);
        UIManager.put("TableHeader.font", APP_FONT.deriveFont(Font.BOLD));

        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("TextField.background", BG_PANEL);
        UIManager.put("PasswordField.background", BG_PANEL);
        UIManager.put("TextArea.background", BG_PANEL);
        UIManager.put("TextField.foreground", Color.WHITE);
        UIManager.put("PasswordField.foreground", Color.WHITE);
        UIManager.put("TextArea.foreground", Color.WHITE);
    }

    private static JLabel createLogoTitle(String text) {
        ImageIcon icon = null;
        File logoFile = new File("bg.png"); // user-supplied background/ logo placeholder
        if (logoFile.exists()) {
            ImageIcon raw = new ImageIcon(logoFile.getAbsolutePath());
            Image img = raw.getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH);
            icon = new ImageIcon(img);
        }
        JLabel lbl = new JLabel(" " + text, icon, JLabel.LEFT);
        lbl.setFont(TITLE_FONT);
        lbl.setForeground(Color.WHITE);
        return lbl;
    }

    private static JPanel createHeaderBar(String title) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_HEADER);
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = createLogoTitle(title);
        header.add(titleLabel, BorderLayout.WEST);

        return header;
    }

    static void styleTable(JTable table) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(BG_PANEL);
        table.setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(70, 90, 160));
        table.setSelectionForeground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_HEADER);
        header.setForeground(Color.WHITE);
        header.setReorderingAllowed(false);
        header.setFont(APP_FONT.deriveFont(Font.BOLD));
    }

    // ====== SIMPLE MODELS (used in GUI) ======
    static class User {
        int id;
        String name;
        String email;
        String password;
        String role; // EMPLOYER / JOB_SEEKER / ADMIN
        String company;
        String resume;

        public User() {}
        public String toString() { return "["+id+"] "+name+" ("+role+")"; }
    }

    static class Job {
        int id;
        String title;
        String description;
        String location;
        double salary;
        int employerId;
        String employerName;
    }

    static class ApplicationModel {
        int id;
        int jobId;
        int seekerId;
        String status;
    }

    // ====== DATABASE MANAGER ======
    static class DatabaseManager {
        private final String jdbcUrl;
        private final String user;
        private final String pass;

        public DatabaseManager(String jdbcUrl, String user, String pass) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.pass = pass;
        }

        // Ensure tables exist
        public void init() throws SQLException {
            try (Connection conn = getConnection()) {
                try (Statement st = conn.createStatement()) {
                    // users table
                    st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS users (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(255) NOT NULL," +
                            "email VARCHAR(255) NOT NULL UNIQUE," +
                            "password VARCHAR(255) NOT NULL," +
                            "role VARCHAR(50) NOT NULL," +
                            "company VARCHAR(255)," +
                            "resume TEXT" +
                        ") ENGINE=InnoDB"
                    );

                    // jobs table
                    st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS jobs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "title VARCHAR(255) NOT NULL," +
                            "description TEXT," +
                            "location VARCHAR(255)," +
                            "salary DOUBLE," +
                            "employer_id INT," +
                            "FOREIGN KEY (employer_id) REFERENCES users(id) ON DELETE SET NULL" +
                        ") ENGINE=InnoDB"
                    );

                    // applications table
                    st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS applications (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "job_id INT," +
                            "seeker_id INT," +
                            "status VARCHAR(50)," +
                            "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE," +
                            "FOREIGN KEY (seeker_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ") ENGINE=InnoDB"
                    );
                }
            }
        }

        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, user, pass);
        }

        // USER CRUD
        public User findUserByEmail(String email) {
            String sql = "SELECT * FROM users WHERE email = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rowToUser(rs);
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return null;
        }

        public User findUserById(int id) {
            String sql = "SELECT * FROM users WHERE id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rowToUser(rs);
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return null;
        }

        public int insertUser(User u) {
            String sql = "INSERT INTO users (name,email,password,role,company,resume) VALUES (?,?,?,?,?,?)";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.name);
                ps.setString(2, u.email);
                ps.setString(3, u.password);
                ps.setString(4, u.role);
                ps.setString(5, u.company);
                ps.setString(6, u.resume);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return -1;
        }

        public List<User> getAllUsers() {
            List<User> out = new ArrayList<>();
            String sql = "SELECT * FROM users";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rowToUser(rs));
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return out;
        }

        public void deleteUserById(int id) {
            String sql = "DELETE FROM users WHERE id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

        }

        public void updateUser(User u) {
            String sql = "UPDATE users SET name=?, email=?, password=?, company=?, resume=? WHERE id=?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, u.name);
                ps.setString(2, u.email);
                ps.setString(3, u.password);
                ps.setString(4, u.company);
                ps.setString(5, u.resume);
                ps.setInt(6, u.id);
                ps.executeUpdate();
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

        }

        // JOB CRUD
        public int insertJob(Job j) {
            String sql = "INSERT INTO jobs (title,description,location,salary,employer_id) VALUES (?,?,?,?,?)";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, j.title);
                ps.setString(2, j.description);
                ps.setString(3, j.location);
                ps.setDouble(4, j.salary);
                if (j.employerId > 0) ps.setInt(5, j.employerId);
                else ps.setNull(5, Types.INTEGER);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return -1;
        }

        public List<Job> getAllJobs() {
            List<Job> out = new ArrayList<>();
            String sql = "SELECT j.*, u.name as employer_name FROM jobs j LEFT JOIN users u ON j.employer_id = u.id ORDER BY j.id DESC";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Job j = new Job();
                    j.id = rs.getInt("id");
                    j.title = rs.getString("title");
                    j.description = rs.getString("description");
                    j.location = rs.getString("location");
                    j.salary = rs.getDouble("salary");
                    j.employerId = rs.getInt("employer_id");
                    j.employerName = rs.getString("employer_name");
                    out.add(j);
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return out;
        }

        public Job findJobById(int id) {
            String sql = "SELECT j.*, u.name as employer_name FROM jobs j LEFT JOIN users u ON j.employer_id = u.id WHERE j.id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Job j = new Job();
                        j.id = rs.getInt("id");
                        j.title = rs.getString("title");
                        j.description = rs.getString("description");
                        j.location = rs.getString("location");
                        j.salary = rs.getDouble("salary");
                        j.employerId = rs.getInt("employer_id");
                        j.employerName = rs.getString("employer_name");
                        return j;
                    }
                }
            }catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return null;
        }

        public void deleteJobById(int id) {
            String sql = "DELETE FROM jobs WHERE id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

        }

        // APPLICATIONS
        public int insertApplication(int jobId, int seekerId) {
            String sql = "INSERT INTO applications (job_id, seeker_id, status) VALUES (?,?, 'APPLIED')";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, jobId);
                ps.setInt(2, seekerId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return -1;
        }

        public List<ApplicationModel> getApplicationsForEmployer(int employerId) {
            List<ApplicationModel> out = new ArrayList<>();
            String sql = "SELECT a.* FROM applications a JOIN jobs j ON a.job_id = j.id WHERE j.employer_id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, employerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ApplicationModel a = new ApplicationModel();
                        a.id = rs.getInt("id");
                        a.jobId = rs.getInt("job_id");
                        a.seekerId = rs.getInt("seeker_id");
                        a.status = rs.getString("status");
                        out.add(a);
                    }
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return out;
        }

        public List<ApplicationModel> getApplicationsForSeeker(int seekerId) {
            List<ApplicationModel> out = new ArrayList<>();
            String sql = "SELECT * FROM applications WHERE seeker_id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, seekerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ApplicationModel a = new ApplicationModel();
                        a.id = rs.getInt("id");
                        a.jobId = rs.getInt("job_id");
                        a.seekerId = rs.getInt("seeker_id");
                        a.status = rs.getString("status");
                        out.add(a);
                    }
                }
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

            return out;
        }

        public void updateApplicationStatus(int appId, String status) {
            String sql = "UPDATE applications SET status = ? WHERE id = ?";
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setInt(2, appId);
                ps.executeUpdate();
            } catch (SQLException ex) {
    JOptionPane.showMessageDialog(null, "DB ERROR: " + ex.getMessage());
    ex.printStackTrace();
}

        }

        private User rowToUser(ResultSet rs) throws SQLException {
            User u = new User();
            u.id = rs.getInt("id");
            u.name = rs.getString("name");
            u.email = rs.getString("email");
            u.password = rs.getString("password");
            u.role = rs.getString("role");
            u.company = rs.getString("company");
            u.resume = rs.getString("resume");
            return u;
        }
    }

    // ====== SERVICE LAYER (uses DatabaseManager) ======
    static class JobPortalService {
        private final DatabaseManager dbm;

        public JobPortalService(DatabaseManager dbm) {
            this.dbm = dbm;
        }

        public User registerEmployer(String name, String email, String password, String company) {
            User u = new User();
            u.name = name; u.email = email; u.password = password; u.role = "EMPLOYER"; u.company = company;
            int id = dbm.insertUser(u);
            u.id = id;
            return u;
        }

        public User registerJobSeeker(String name, String email, String password, String resume) {
            User u = new User();
            u.name = name; u.email = email; u.password = password; u.role = "JOB_SEEKER"; u.resume = resume;
            int id = dbm.insertUser(u);
            u.id = id;
            return u;
        }

        public User createAdminIfNotExists(String name, String email, String password) {
            User found = dbm.findUserByEmail(email);
            if (found != null) return found;
            User u = new User();
            u.name = name; u.email = email; u.password = password; u.role = "ADMIN";
            int id = dbm.insertUser(u);
            u.id = id;
            return u;
        }

        public User login(String email, String password) {
            User u = dbm.findUserByEmail(email);
            if (u != null && u.password.equals(password)) return u;
            return null;
        }

        public Job postJob(String title, String description, String location, double salary, User employer) {
            Job j = new Job();
            j.title = title; j.description = description; j.location = location; j.salary = salary; j.employerId = employer.id;
            int id = dbm.insertJob(j);
            j.id = id;
            j.employerName = employer.name;
            return j;
        }

        public List<Job> getAllJobs() { return dbm.getAllJobs(); }

        public Job findJobById(int id) { return dbm.findJobById(id); }

        public int applyToJob(Job job, User seeker) {
            return dbm.insertApplication(job.id, seeker.id);
        }

        public List<ApplicationModel> getApplicationsForEmployer(User emp) {
            return dbm.getApplicationsForEmployer(emp.id);
        }

        public List<ApplicationModel> getApplicationsForJobSeeker(User seeker) {
            return dbm.getApplicationsForSeeker(seeker.id);
        }

        public void updateApplicationStatus(int appId, String status) {
            dbm.updateApplicationStatus(appId, status);
        }

        public List<User> getAllUsers() { return dbm.getAllUsers(); }

        public void deleteUser(int userId) { dbm.deleteUserById(userId); }

        public void deleteJob(int jobId) { dbm.deleteJobById(jobId); }
    }

    // ====== UI HELPERS (Rounded button, gradient panel) ======
    static class GradientPanel extends JPanel {
        private Color color1 = new Color(45, 45, 45);
        private Color color2 = new Color(20, 20, 20);
        public GradientPanel() { setOpaque(false); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, color1, 0, h, color2);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RoundedButton extends JButton {
        public RoundedButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(APP_FONT);
            setMargin(new Insets(5, 15, 5, 15));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = ACCENT;
            Color hover = ACCENT.brighter();
            Color press = ACCENT.darker();
            if (getModel().isPressed()) g2.setColor(press);
            else if (getModel().isRollover()) g2.setColor(hover);
            else g2.setColor(base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ====== STATIC singletons for this app ======
    private static DatabaseManager dbm;
    private static JobPortalService service;

    // ====== FIELDS FOR MAIN GUI APP ======
    private JTextField loginEmailField;
    private JPasswordField loginPasswordField;
    private JLabel loginStatusLabel;
    private JCheckBox rememberMeCheckBox;

    // remember me file
    private static final String REMEMBER_FILE = "remember_me.txt";

    // ====== CONSTRUCTOR ======
    public JobPortalSQLGui() {
        setTitle("Job Portal - SQL");
        setSize(800, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        applyGlobalStyles();

        // header
        add(createHeaderBar("Job Portal"), BorderLayout.NORTH);

        initUI();
        loadRememberedUser();
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(Color.WHITE);
        tabs.setFont(TAB_FONT);
        tabs.setBorder(new EmptyBorder(10, 10, 10, 10));

        tabs.addTab("Login", buildLoginPanel());
        tabs.addTab("Register Employer", buildEmployerRegisterPanel());
        tabs.addTab("Register Job Seeker", buildJobSeekerRegisterPanel());

        add(tabs, BorderLayout.CENTER);
    }

    // ====== VALIDATION HELPERS ======
    private static boolean isValidEmail(String email) {
        if (email == null) return false;
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
    private static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    // ====== REMEMBER me helpers ======
    private void saveRememberMe(String email, String password) {
        try {
            File f = new File(REMEMBER_FILE);
            if (rememberMeCheckBox != null && rememberMeCheckBox.isSelected()) {
                try (FileWriter fw = new FileWriter(f)) {
                    fw.write(email + "\n" + password);
                }
            } else {
                if (f.exists()) {
                    int res = JOptionPane.showConfirmDialog(this,
                            "Clear saved login information?",
                            "Confirm",
                            JOptionPane.YES_NO_OPTION);
                    if (res == JOptionPane.YES_OPTION) f.delete();
                }
            }
        } catch (IOException e) { /* ignore */ }
    }

    private void loadRememberedUser() {
        File f = new File(REMEMBER_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String email = br.readLine();
            String pass = br.readLine();
            if (email == null || pass == null) { f.delete(); return; }
            if (loginEmailField != null && loginPasswordField != null) {
                loginEmailField.setText(email);
                loginPasswordField.setText(pass);
                if (rememberMeCheckBox != null) rememberMeCheckBox.setSelected(true);
                try { doLogin(); } catch (Exception ex) { f.delete(); }
            }
        } catch (IOException e) {}
    }

    // ====== LOGIN PANEL ======
    private JPanel buildLoginPanel() {
        GradientPanel panel = new GradientPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel bgLabel = new JLabel();
        File bg = new File("bg.png");
        if (bg.exists()) {
            ImageIcon ic = new ImageIcon("bg.png");
            Image img = ic.getImage().getScaledInstance(140, 80, Image.SCALE_SMOOTH);
            bgLabel.setIcon(new ImageIcon(img));
        } else {
            bgLabel.setText(" ");
        }

        JLabel title = new JLabel("Login");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);

        JLabel emailLabel = new JLabel("Email:");
        JLabel passwordLabel = new JLabel("Password:");
        emailLabel.setForeground(Color.WHITE);
        passwordLabel.setForeground(Color.WHITE);

        loginEmailField = new JTextField(20);
        loginPasswordField = new JPasswordField(20);
        RoundedButton loginButton = new RoundedButton("Login");
        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setForeground(Color.ORANGE);

        rememberMeCheckBox = new JCheckBox("Remember me / auto-login");
        rememberMeCheckBox.setOpaque(false);
        rememberMeCheckBox.setForeground(Color.WHITE);

        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0: image
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(bgLabel, gbc);

        // Row 1: title
        gbc.gridy = 1;
        panel.add(title, gbc);

        gbc.gridwidth = 1;

        // Row 2: email
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(emailLabel, gbc);
        gbc.gridx = 1;
        panel.add(loginEmailField, gbc);

        // Row 3: password
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(passwordLabel, gbc);
        gbc.gridx = 1;
        panel.add(loginPasswordField, gbc);

        // Row 4: Remember me
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(rememberMeCheckBox, gbc);

        // Row 5: login button
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loginButton, gbc);

        // Row 6: status
        gbc.gridy = 6;
        panel.add(loginStatusLabel, gbc);

        loginButton.addActionListener(e -> doLogin());

        return panel;
    }

    private void doLogin() {
        String email = loginEmailField.getText().trim();
        String password = new String(loginPasswordField.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            loginStatusLabel.setText("Please enter email and password.");
            return;
        }
        if (!isValidEmail(email)) {
            loginStatusLabel.setText("Invalid email format.");
            return;
        }

        User user = service.login(email, password);
        if (user == null) {
            loginStatusLabel.setText("Invalid credentials.");
            return;
        }

        loginStatusLabel.setText("Login successful as " + user.role);
        saveRememberMe(email, password);
        openDashboard(user);
    }

    private void openDashboard(User user) {
        if ("EMPLOYER".equals(user.role)) {
            new EmployerFrame(user, service).setVisible(true);
        } else if ("JOB_SEEKER".equals(user.role)) {
            new JobSeekerFrame(user, service).setVisible(true);
        } else if ("ADMIN".equals(user.role)) {
            new AdminFrame(user, service).setVisible(true);
        }
    }

    // ====== REGISTER PANELS ======
    private JPanel buildEmployerRegisterPanel() {
        GradientPanel panel = new GradientPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel title = new JLabel("Register Employer");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);

        JLabel nameLabel = new JLabel("Name:");
        JLabel emailLabel = new JLabel("Email:");
        JLabel passwordLabel = new JLabel("Password:");
        JLabel companyLabel = new JLabel("Company:");

        nameLabel.setForeground(Color.WHITE);
        emailLabel.setForeground(Color.WHITE);
        passwordLabel.setForeground(Color.WHITE);
        companyLabel.setForeground(Color.WHITE);

        JTextField nameField = new JTextField(20);
        JTextField emailField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);
        JTextField companyField = new JTextField(20);

        RoundedButton registerButton = new RoundedButton("Register");
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.ORANGE);

        gbc.insets = new Insets(5,5,5,5);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(nameLabel, gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(emailLabel, gbc);
        gbc.gridx = 1;
        panel.add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(passwordLabel, gbc);
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(companyLabel, gbc);
        gbc.gridx = 1;
        panel.add(companyField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        panel.add(registerButton, gbc);

        gbc.gridy = 6;
        panel.add(statusLabel, gbc);

        registerButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String pass = new String(passwordField.getPassword());
            String company = companyField.getText().trim();

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || company.isEmpty()) {
                statusLabel.setText("All fields are required.");
                return;
            }
            if (!isValidEmail(email)) {
                statusLabel.setText("Invalid email format.");
                return;
            }
            if (!isValidPassword(pass)) {
                statusLabel.setText("Password must be at least 6 characters.");
                return;
            }
            if (dbm.findUserByEmail(email) != null) {
                statusLabel.setText("User already exists with this email.");
                return;
            }
            User u = new User();
            u.name = name; u.email = email; u.password = pass; u.role = "EMPLOYER"; u.company = company;
            int id = dbm.insertUser(u);
            if (id > 0) statusLabel.setText("Registered. Your ID: " + id);
            else statusLabel.setText("Registration failed.");
        });

        return panel;
    }

    private JPanel buildJobSeekerRegisterPanel() {
        GradientPanel panel = new GradientPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel title = new JLabel("Register Job Seeker");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);

        JLabel nameLabel = new JLabel("Name:");
        JLabel emailLabel = new JLabel("Email:");
        JLabel passwordLabel = new JLabel("Password:");
        JLabel resumeLabel = new JLabel("Resume Summary:");

        nameLabel.setForeground(Color.WHITE);
        emailLabel.setForeground(Color.WHITE);
        passwordLabel.setForeground(Color.WHITE);
        resumeLabel.setForeground(Color.WHITE);

        JTextField nameField = new JTextField(20);
        JTextField emailField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);
        JTextArea resumeArea = new JTextArea(3, 20);
        JScrollPane resumeScroll = new JScrollPane(resumeArea);

        RoundedButton registerButton = new RoundedButton("Register");
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.ORANGE);

        gbc.insets = new Insets(5,5,5,5);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(nameLabel, gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(emailLabel, gbc);
        gbc.gridx = 1;
        panel.add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(passwordLabel, gbc);
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(resumeLabel, gbc);
        gbc.gridx = 1;
        panel.add(resumeScroll, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        panel.add(registerButton, gbc);

        gbc.gridy = 6;
        panel.add(statusLabel, gbc);

        registerButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String pass = new String(passwordField.getPassword());
            String resume = resumeArea.getText().trim();

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || resume.isEmpty()) {
                statusLabel.setText("All fields are required.");
                return;
            }
            if (!isValidEmail(email)) {
                statusLabel.setText("Invalid email format.");
                return;
            }
            if (!isValidPassword(pass)) {
                statusLabel.setText("Password must be at least 6 characters.");
                return;
            }
            if (dbm.findUserByEmail(email) != null) {
                statusLabel.setText("User already exists with this email.");
                return;
            }
            User u = new User();
            u.name = name; u.email = email; u.password = pass; u.role = "JOB_SEEKER"; u.resume = resume;
            int id = dbm.insertUser(u);
            if (id > 0) statusLabel.setText("Registered. Your ID: " + id);
            else statusLabel.setText("Registration failed.");
        });

        return panel;
    }

    // ====== DASHBOARD FRAMES ======
    // EMPLOYER FRAME
    static class EmployerFrame extends JFrame {
        private final User employer;
        private final JobPortalService service;
        private JTable jobsTable;
        private JTable appsTable;

        public EmployerFrame(User employer, JobPortalService service) {
            this.employer = employer;
            this.service = service;

            setTitle("Employer Dashboard - " + employer.name);
            setSize(900, 550);
            setLocationRelativeTo(null);
            getContentPane().setBackground(BG_DARK);
            setLayout(new BorderLayout());

            JPanel header = createHeaderBar("Employer Dashboard");
            RoundedButton backBtn = new RoundedButton("Back");
            backBtn.addActionListener(e -> this.dispose());
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));
            right.setOpaque(false);
            right.add(backBtn);
            header.add(right, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            initUI();
        }

        private void initUI() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(BG_PANEL);
            tabs.setForeground(Color.WHITE);
            tabs.setFont(TAB_FONT);
            tabs.setBorder(new EmptyBorder(10,10,10,10));

            tabs.addTab("Post Job", buildPostJobPanel());
            tabs.addTab("My Jobs", buildJobsPanel());
            tabs.addTab("Applications", buildApplicationsPanel());
            tabs.addTab("Profile", buildProfilePanel());

            add(tabs, BorderLayout.CENTER);
        }

        private JPanel buildPostJobPanel() {
            GradientPanel panel = new GradientPanel();
            panel.setLayout(new GridBagLayout());
            panel.setBorder(new EmptyBorder(10,20,10,20));
            GridBagConstraints gbc = new GridBagConstraints();

            JLabel titleLabel = new JLabel("Post a New Job");
            titleLabel.setFont(TITLE_FONT);
            titleLabel.setForeground(Color.WHITE);

            JLabel jobTitleLabel = new JLabel("Job Title:");
            JLabel descLabel = new JLabel("Description:");
            JLabel locLabel = new JLabel("Location:");
            JLabel salLabel = new JLabel("Salary:");

            jobTitleLabel.setForeground(Color.WHITE);
            descLabel.setForeground(Color.WHITE);
            locLabel.setForeground(Color.WHITE);
            salLabel.setForeground(Color.WHITE);

            JTextField titleField = new JTextField(20);
            JTextArea descArea = new JTextArea(3,20);
            JScrollPane descScroll = new JScrollPane(descArea);
            JTextField locField = new JTextField(20);
            JTextField salField = new JTextField(20);

            RoundedButton postButton = new RoundedButton("Post Job");
            JLabel statusLabel = new JLabel(" ");
            statusLabel.setForeground(Color.ORANGE);

            gbc.insets = new Insets(5,5,5,5);
            gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2;
            panel.add(titleLabel, gbc);

            gbc.gridwidth=1;
            gbc.gridx=0; gbc.gridy=1;
            panel.add(jobTitleLabel, gbc);
            gbc.gridx=1;
            panel.add(titleField, gbc);

            gbc.gridx=0; gbc.gridy=2;
            panel.add(descLabel, gbc);
            gbc.gridx=1;
            panel.add(descScroll, gbc);

            gbc.gridx=0; gbc.gridy=3;
            panel.add(locLabel, gbc);
            gbc.gridx=1;
            panel.add(locField, gbc);

            gbc.gridx=0; gbc.gridy=4;
            panel.add(salLabel, gbc);
            gbc.gridx=1;
            panel.add(salField, gbc);

            gbc.gridx=0; gbc.gridy=5; gbc.gridwidth=2;
            panel.add(postButton, gbc);

            gbc.gridy=6;
            panel.add(statusLabel, gbc);

            postButton.addActionListener(e -> {
                try {
                    String t = titleField.getText().trim();
                    String d = descArea.getText().trim();
                    String loc = locField.getText().trim();
                    String stext = salField.getText().trim();
                    if (t.isEmpty() || d.isEmpty() || loc.isEmpty() || stext.isEmpty()) {
                        statusLabel.setText("All fields required.");
                        return;
                    }
                    double sal = Double.parseDouble(stext);
                    Job j = new Job();
                    j.title = t; j.description = d; j.location = loc; j.salary = sal; j.employerId = employer.id;
                    int id = dbm.insertJob(j);
                    if (id>0) statusLabel.setText("Posted job with ID: " + id);
                    else statusLabel.setText("Failed to post.");
                    refreshJobsTable();
                } catch (NumberFormatException ex) {
                    statusLabel.setText("Invalid salary.");
                    salField.setText("");
                }
            });

            return panel;
        }

        private JPanel buildJobsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(BG_DARK);

            jobsTable = new JTable();
            styleTable(jobsTable);
            refreshJobsTable();

            JPanel top = new JPanel();
            top.setBackground(BG_DARK);
            RoundedButton exportBtn = new RoundedButton("Export My Jobs to CSV");
            exportBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "Export feature available."));
            top.add(exportBtn);

            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(jobsTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshJobsTable() {
            String[] cols = {"Job ID","Title","Location","Salary"};
            DefaultTableModel model = new DefaultTableModel(cols,0);
            List<Job> jobs = service.getAllJobs();
            for (Job j : jobs) {
                if (j.employerId == employer.id) {
                    model.addRow(new Object[]{j.id, j.title, j.location, j.salary});
                }
            }
            jobsTable.setModel(model);
        }

        private JPanel buildApplicationsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(BG_DARK);

            appsTable = new JTable();
            styleTable(appsTable);
            refreshAppsTable();

            JPanel top = new JPanel();
            top.setBackground(BG_DARK);
            JLabel appIdLabel = new JLabel("Application ID:");
            JTextField appIdField = new JTextField(5);
            JLabel statusLabel = new JLabel("Status:");
            String[] statuses = {"APPLIED","ACCEPTED","REJECTED"};
            JComboBox<String> statusBox = new JComboBox<>(statuses);
            RoundedButton updateButton = new RoundedButton("Update Status");

            appIdLabel.setForeground(Color.WHITE);
            statusLabel.setForeground(Color.WHITE);

            top.add(appIdLabel);
            top.add(appIdField);
            top.add(statusLabel);
            top.add(statusBox);
            top.add(updateButton);

            updateButton.addActionListener(e -> {
                try {
                    int aid = Integer.parseInt(appIdField.getText().trim());
                    String st = (String) statusBox.getSelectedItem();
                    service.updateApplicationStatus(aid, st);
                    refreshAppsTable();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid Application ID");
                }
            });

            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(appsTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshAppsTable() {
            String[] cols = {"App ID","Job ID","Seeker ID","Status"};
            DefaultTableModel model = new DefaultTableModel(cols,0);
            List<ApplicationModel> apps = service.getApplicationsForEmployer(employer);
            for (ApplicationModel a : apps) {
                model.addRow(new Object[]{a.id, a.jobId, a.seekerId, a.status});
            }
            appsTable.setModel(model);
        }

        private JPanel buildProfilePanel() {
            GradientPanel panel = new GradientPanel();
            panel.setLayout(new GridBagLayout());
            panel.setBorder(new EmptyBorder(10,20,10,20));
            GridBagConstraints gbc = new GridBagConstraints();

            JLabel title = new JLabel("Update Profile");
            title.setFont(TITLE_FONT);
            title.setForeground(Color.WHITE);

            JLabel nameLabel = new JLabel("Name:");
            JLabel emailLabel = new JLabel("Email:");
            JLabel passwordLabel = new JLabel("Password:");
            JLabel companyLabel = new JLabel("Company:");

            nameLabel.setForeground(Color.WHITE);
            emailLabel.setForeground(Color.WHITE);
            passwordLabel.setForeground(Color.WHITE);
            companyLabel.setForeground(Color.WHITE);

            JTextField nameField = new JTextField(employer.name, 20);
            JTextField emailField = new JTextField(employer.email, 20);
            JPasswordField passwordField = new JPasswordField(employer.password, 20);
            JTextField companyField = new JTextField(employer.company, 20);

            RoundedButton saveButton = new RoundedButton("Save Changes");

            gbc.insets = new Insets(5,5,5,5);
            gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2;
            panel.add(title, gbc);

            gbc.gridwidth=1;
            gbc.gridx=0; gbc.gridy=1;
            panel.add(nameLabel, gbc);
            gbc.gridx=1;
            panel.add(nameField, gbc);

            gbc.gridx=0; gbc.gridy=2;
            panel.add(emailLabel, gbc);
            gbc.gridx=1;
            panel.add(emailField, gbc);

            gbc.gridx=0; gbc.gridy=3;
            panel.add(passwordLabel, gbc);
            gbc.gridx=1;
            panel.add(passwordField, gbc);

            gbc.gridx=0; gbc.gridy=4;
            panel.add(companyLabel, gbc);
            gbc.gridx=1;
            panel.add(companyField, gbc);

            gbc.gridx=0; gbc.gridy=5; gbc.gridwidth=2;
            panel.add(saveButton, gbc);

            saveButton.addActionListener(e -> {
                String name = nameField.getText().trim();
                String email = emailField.getText().trim();
                String pass = new String(passwordField.getPassword());
                String company = companyField.getText().trim();

                if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || company.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "All fields are required.");
                    return;
                }
                if (!isValidEmail(email)) {
                    JOptionPane.showMessageDialog(this, "Invalid email format.");
                    return;
                }
                if (!isValidPassword(pass)) {
                    JOptionPane.showMessageDialog(this, "Password must be at least 6 characters.");
                    return;
                }

                employer.name = name;
                employer.email = email;
                employer.password = pass;
                employer.company = company;
                dbm.updateUser(employer);
                JOptionPane.showMessageDialog(this, "Profile updated.");
            });

            return panel;
        }
    }

    // JOB SEEKER FRAME
    static class JobSeekerFrame extends JFrame {
        private final User seeker;
        private final JobPortalService service;
        private JTable jobsTable;
        private JTable appsTable;
        private JTextField searchField, locationField, minSalaryField;

        public JobSeekerFrame(User seeker, JobPortalService service) {
            this.seeker = seeker;
            this.service = service;

            setTitle("Job Seeker Dashboard - " + seeker.name);
            setSize(900, 550);
            setLocationRelativeTo(null);
            getContentPane().setBackground(BG_DARK);
            setLayout(new BorderLayout());

            JPanel header = createHeaderBar("Job Seeker Dashboard");
            RoundedButton backBtn = new RoundedButton("Back");
            backBtn.addActionListener(e -> this.dispose());
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));
            right.setOpaque(false);
            right.add(backBtn);
            header.add(right, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            initUI();
        }

        private void initUI() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(BG_PANEL);
            tabs.setForeground(Color.WHITE);
            tabs.setFont(TAB_FONT);
            tabs.setBorder(new EmptyBorder(10,10,10,10));

            tabs.addTab("All Jobs", buildJobsPanel());
            tabs.addTab("Apply", buildApplyPanel());
            tabs.addTab("My Applications", buildAppsPanel());
            tabs.addTab("Profile", buildProfilePanel());

            add(tabs, BorderLayout.CENTER);
        }

        private JPanel buildJobsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(BG_DARK);

            jobsTable = new JTable();
            styleTable(jobsTable);

            JPanel top = new JPanel();
            top.setBackground(BG_DARK);

            JLabel searchLabel = new JLabel("Search:");
            JLabel locLabel = new JLabel("Location:");
            JLabel salLabel = new JLabel("Min Salary:");

            searchLabel.setForeground(Color.WHITE);
            locLabel.setForeground(Color.WHITE);
            salLabel.setForeground(Color.WHITE);

            searchField = new JTextField(10);
            locationField = new JTextField(10);
            minSalaryField = new JTextField(7);

            RoundedButton filterButton = new RoundedButton("Filter");
            RoundedButton clearButton = new RoundedButton("Clear");
            RoundedButton exportButton = new RoundedButton("Export Jobs to CSV");

            filterButton.addActionListener(e -> refreshJobsTable());
            clearButton.addActionListener(e -> { searchField.setText(""); locationField.setText(""); minSalaryField.setText(""); refreshJobsTable(); });
            exportButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "Export feature available."));

            top.add(searchLabel); top.add(searchField);
            top.add(locLabel); top.add(locationField);
            top.add(salLabel); top.add(minSalaryField);
            top.add(filterButton); top.add(clearButton); top.add(exportButton);

            refreshJobsTable();
            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(jobsTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshJobsTable() {
            String[] cols = {"Job ID","Title","Company","Location","Salary"};
            DefaultTableModel model = new DefaultTableModel(cols,0);

            String keyword = searchField != null ? searchField.getText().trim().toLowerCase() : "";
            String loc = locationField != null ? locationField.getText().trim().toLowerCase() : "";
            String minSalStr = minSalaryField != null ? minSalaryField.getText().trim() : "";
            double minSal = 0;
            if (!minSalStr.isEmpty()) {
                try { minSal = Double.parseDouble(minSalStr); } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Invalid minimum salary. Using 0."); minSalaryField.setText(""); minSal = 0; }
            }

            List<Job> jobs = service.getAllJobs();
            for (Job j : jobs) {
                if (!keyword.isEmpty()) {
                    if (!j.title.toLowerCase().contains(keyword) && (j.description==null || !j.description.toLowerCase().contains(keyword))) continue;
                }
                if (!loc.isEmpty() && (j.location==null || !j.location.toLowerCase().contains(loc))) continue;
                if (j.salary < minSal) continue;
                model.addRow(new Object[]{j.id, j.title, j.employerName, j.location, j.salary});
            }
            jobsTable.setModel(model);
        }

        private JPanel buildApplyPanel() {
            GradientPanel panel = new GradientPanel();
            panel.setLayout(new FlowLayout());
            panel.setBorder(new EmptyBorder(10,20,10,20));

            JLabel label = new JLabel("Enter Job ID to apply:");
            label.setForeground(Color.WHITE);
            JTextField jobIdField = new JTextField(10);
            RoundedButton applyButton = new RoundedButton("Apply");
            JLabel statusLabel = new JLabel(" ");
            statusLabel.setForeground(Color.ORANGE);

            panel.add(label); panel.add(jobIdField); panel.add(applyButton); panel.add(statusLabel);

            applyButton.addActionListener(e -> {
                try {
                    int jobId = Integer.parseInt(jobIdField.getText().trim());
                    Job job = service.findJobById(jobId);
                    if (job==null) { statusLabel.setText("Invalid Job ID."); return; }
                    int appId = service.applyToJob(job, seeker);
                    if (appId>0) statusLabel.setText("Applied. App ID: " + appId);
                    else statusLabel.setText("Failed to apply.");
                    refreshAppsTable();
                } catch (NumberFormatException ex) {
                    statusLabel.setText("Invalid Job ID.");
                }
            });

            return panel;
        }

        private JPanel buildAppsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(BG_DARK);

            appsTable = new JTable();
            styleTable(appsTable);
            refreshAppsTable();

            JPanel top = new JPanel();
            top.setBackground(BG_DARK);
            RoundedButton exportButton = new RoundedButton("Export My Applications to CSV");
            exportButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "Export feature available."));
            top.add(exportButton);

            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(appsTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshAppsTable() {
            String[] cols = {"App ID","Job ID","Status"};
            DefaultTableModel model = new DefaultTableModel(cols,0);
            List<ApplicationModel> apps = service.getApplicationsForJobSeeker(seeker);
            for (ApplicationModel a : apps) {
                model.addRow(new Object[]{a.id, a.jobId, a.status});
            }
            appsTable.setModel(model);
        }

        private JPanel buildProfilePanel() {
            GradientPanel panel = new GradientPanel();
            panel.setLayout(new GridBagLayout());
            panel.setBorder(new EmptyBorder(10,20,10,20));
            GridBagConstraints gbc = new GridBagConstraints();

            JLabel title = new JLabel("Update Profile");
            title.setFont(TITLE_FONT);
            title.setForeground(Color.WHITE);

            JLabel nameLabel = new JLabel("Name:");
            JLabel emailLabel = new JLabel("Email:");
            JLabel passwordLabel = new JLabel("Password:");
            JLabel resumeLabel = new JLabel("Resume Summary:");

            nameLabel.setForeground(Color.WHITE);
            emailLabel.setForeground(Color.WHITE);
            passwordLabel.setForeground(Color.WHITE);
            resumeLabel.setForeground(Color.WHITE);

            JTextField nameField = new JTextField(seeker.name,20);
            JTextField emailField = new JTextField(seeker.email,20);
            JPasswordField passwordField = new JPasswordField(seeker.password,20);
            JTextArea resumeArea = new JTextArea(seeker.resume==null?"":seeker.resume,3,20);
            JScrollPane resumeScroll = new JScrollPane(resumeArea);

            RoundedButton saveButton = new RoundedButton("Save Changes");

            gbc.insets = new Insets(5,5,5,5);
            gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2;
            panel.add(title, gbc);

            gbc.gridwidth=1;
            gbc.gridx=0; gbc.gridy=1; panel.add(nameLabel, gbc); gbc.gridx=1; panel.add(nameField, gbc);
            gbc.gridx=0; gbc.gridy=2; panel.add(emailLabel, gbc); gbc.gridx=1; panel.add(emailField, gbc);
            gbc.gridx=0; gbc.gridy=3; panel.add(passwordLabel, gbc); gbc.gridx=1; panel.add(passwordField, gbc);
            gbc.gridx=0; gbc.gridy=4; panel.add(resumeLabel, gbc); gbc.gridx=1; panel.add(resumeScroll, gbc);
            gbc.gridx=0; gbc.gridy=5; gbc.gridwidth=2; panel.add(saveButton, gbc);

            saveButton.addActionListener(e -> {
                String name = nameField.getText().trim();
                String email = emailField.getText().trim();
                String pass = new String(passwordField.getPassword());
                String resume = resumeArea.getText().trim();
                if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "All fields required.");
                    return;
                }
                seeker.name = name; seeker.email = email; seeker.password = pass; seeker.resume = resume;
                dbm.updateUser(seeker);
                JOptionPane.showMessageDialog(this, "Profile updated.");
            });

            return panel;
        }
    }

    // ADMIN FRAME
    static class AdminFrame extends JFrame {
        private final User admin;
        private final JobPortalService service;
        private JTable usersTable;
        private JTable jobsTable;

        public AdminFrame(User admin, JobPortalService service) {
            this.admin = admin; this.service = service;
            setTitle("Admin Dashboard - " + admin.name);
            setSize(900,550);
            setLocationRelativeTo(null);
            getContentPane().setBackground(BG_DARK);
            setLayout(new BorderLayout());

            JPanel header = createHeaderBar("Admin Dashboard");
            RoundedButton backBtn = new RoundedButton("Back");
            backBtn.addActionListener(e -> this.dispose());
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));
            right.setOpaque(false);
            right.add(backBtn);
            header.add(right, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            initUI();
        }

        private void initUI() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(BG_PANEL);
            tabs.setForeground(Color.WHITE);
            tabs.setFont(TAB_FONT);
            tabs.setBorder(new EmptyBorder(10,10,10,10));
            tabs.addTab("Users", buildUsersPanel());
            tabs.addTab("Jobs", buildJobsPanel());
            add(tabs, BorderLayout.CENTER);
        }

        private JPanel buildUsersPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(BG_DARK);

            usersTable = new JTable();
            styleTable(usersTable);
            refreshUsersTable();

            JPanel top = new JPanel();
            top.setBackground(BG_DARK);
            JLabel label = new JLabel("User ID to delete:");
            label.setForeground(Color.WHITE);
            JTextField userIdField = new JTextField(5);
            RoundedButton deleteBtn = new RoundedButton("Delete");
            top.add(label); top.add(userIdField); top.add(deleteBtn);

            deleteBtn.addActionListener(e -> {
                try {
                    int uid = Integer.parseInt(userIdField.getText().trim());
                    int res = JOptionPane.showConfirmDialog(this, "Delete user " + uid + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (res==JOptionPane.YES_OPTION) {
                        service.deleteUser(uid);
                        refreshUsersTable();
                    }
                } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Invalid User ID"); }
            });

            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshUsersTable() {
            String[] cols = {"User ID","Name","Email","Role"};
            DefaultTableModel model = new DefaultTableModel(cols,0);
            List<User> users = service.getAllUsers();
            for (User u : users) model.addRow(new Object[]{u.id, u.name, u.email, u.role});
            usersTable.setModel(model);
        }

        private JPanel buildJobsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(BG_DARK);

            jobsTable = new JTable();
            styleTable(jobsTable);
            refreshJobsTable();

            JPanel top = new JPanel();
            top.setBackground(BG_DARK);
            JLabel label = new JLabel("Job ID to delete:");
            label.setForeground(Color.WHITE);
            JTextField jobIdField = new JTextField(5);
            RoundedButton deleteBtn = new RoundedButton("Delete");
            RoundedButton exportBtn = new RoundedButton("Export All Jobs to CSV");
            top.add(label); top.add(jobIdField); top.add(deleteBtn); top.add(exportBtn);

            deleteBtn.addActionListener(e -> {
                try {
                    int jid = Integer.parseInt(jobIdField.getText().trim());
                    int res = JOptionPane.showConfirmDialog(this, "Delete job " + jid + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (res==JOptionPane.YES_OPTION) {
                        service.deleteJob(jid);
                        refreshJobsTable();
                    }
                } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Invalid Job ID"); }
            });

            exportBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "Export available."));

            panel.add(top, BorderLayout.NORTH);
            panel.add(new JScrollPane(jobsTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshJobsTable() {
            String[] cols = {"Job ID","Title","Company","Location","Salary"};
            DefaultTableModel model = new DefaultTableModel(cols,0);
            List<Job> jobs = service.getAllJobs();
            for (Job j : jobs) model.addRow(new Object[]{j.id, j.title, j.employerName, j.location, j.salary});
            jobsTable.setModel(model);
        }
    }

    // ====== MAIN ======
    public static void main(String[] args) {
        // Load JDBC driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            JOptionPane.showMessageDialog(null, "MySQL JDBC driver not found. Add the connector JAR to classpath.\nDownload: https://dev.mysql.com/downloads/connector/j/");
            ex.printStackTrace();
            return;
        }

        // initialize DB manager and service
        dbm = new DatabaseManager(JDBC_URL, DB_USER, DB_PASS);
        try {
            System.out.println("Connecting to: " + JDBC_URL);
            dbm.init();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to initialize database/tables: " + ex.getMessage());
            return;
        }
        service = new JobPortalService(dbm);

        // create default admin if missing
        service.createAdminIfNotExists("Super Admin", "admin@portal.com", "admin123");

        // set look & feel tweaks
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            UIManager.put("control", new Color(40, 40, 40));
            UIManager.put("info", new Color(60, 63, 65));
            UIManager.put("nimbusBase", new Color(18, 30, 49));
            UIManager.put("nimbusLightBackground", new Color(43, 43, 43));
            UIManager.put("text", Color.WHITE);
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            JobPortalSQLGui gui = new JobPortalSQLGui();
            gui.setVisible(true);
        });
    }
}
