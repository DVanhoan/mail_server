import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class MailClient extends JFrame {

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String currentUser;

    private JTextField serverIpField, serverPortField, usernameField, recipientField, titleField;
    private JPasswordField passwordField;
    private JTextArea messageArea, composeArea;
    private JButton connectButton, loginButton, registerButton, sendButton, listButton, logoutButton;
    private JLabel statusLabel;

    public MailClient() {
        super("UDP Mail Client");
        initComponents();
        layoutComponents();
        addEventListeners();

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        serverIpField = new JTextField("127.0.0.1", 15);
        serverPortField = new JTextField("9999", 5);
        connectButton = new JButton("Connect");
        usernameField = new JTextField(15);
        passwordField = new JPasswordField(15);
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");
        logoutButton = new JButton("Logout");
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        listButton = new JButton("List My Mails (Inbox)");
        recipientField = new JTextField(15);
        titleField = new JTextField(20);
        composeArea = new JTextArea(5, 40);
        composeArea.setLineWrap(true);
        composeArea.setWrapStyleWord(true);
        sendButton = new JButton("Send Mail");
        statusLabel = new JLabel("Status: Disconnected");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        setLoggedInState(false);
        connectButton.setEnabled(true);
        serverIpField.setEditable(true);
        serverPortField.setEditable(true);
    }

    private void layoutComponents() {
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout(5, 5));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Connection & Authentication"));
        topPanel.add(new JLabel("Server IP:"));
        topPanel.add(serverIpField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(serverPortField);
        topPanel.add(connectButton);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(new JLabel("Username:"));
        topPanel.add(usernameField);
        topPanel.add(new JLabel("Password:"));
        topPanel.add(passwordField);
        topPanel.add(loginButton);
        topPanel.add(registerButton);
        topPanel.add(logoutButton);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Inbox / Server Messages"));
        centerPanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionsPanel.add(listButton);
        centerPanel.add(actionsPanel, BorderLayout.SOUTH);

        JPanel composePanel = new JPanel(new BorderLayout(5, 5));
        composePanel.setBorder(BorderFactory.createTitledBorder("Compose Mail"));
        JPanel composeInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        composeInfoPanel.add(new JLabel("To (username):"));
        composeInfoPanel.add(recipientField);
        composeInfoPanel.add(new JLabel("Title:"));
        composeInfoPanel.add(titleField);
        composePanel.add(composeInfoPanel, BorderLayout.NORTH);
        composePanel.add(new JScrollPane(composeArea), BorderLayout.CENTER);
        composePanel.add(sendButton, BorderLayout.SOUTH);

        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(composePanel, BorderLayout.CENTER);
        bottomContainer.add(statusLabel, BorderLayout.SOUTH);

        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(centerPanel, BorderLayout.CENTER);
        contentPane.add(bottomContainer, BorderLayout.SOUTH);
    }

    private void addEventListeners() {
        connectButton.addActionListener(e -> connectToServer());

        registerButton.addActionListener(e -> {
            String user = usernameField.getText().trim();
            String pass = new String(passwordField.getPassword()).trim();
            if (user.isEmpty() || pass.isEmpty()) {
                logMessage("Client: Username and password cannot be empty for registration.");
                return;
            }
            sendCommand("REGISTER " + user + " " + pass);
        });

        loginButton.addActionListener(e -> {
            String user = usernameField.getText().trim();
            String pass = new String(passwordField.getPassword()).trim();
            if (user.isEmpty() || pass.isEmpty()) {
                logMessage("Client: Username and password cannot be empty for login.");
                return;
            }
            sendCommand("LOGIN " + user + " " + pass);
            currentUser = user;
        });

        logoutButton.addActionListener(e -> {
            if (currentUser != null) {
                sendCommand("LOGOUT " + currentUser);
                logMessage("Client: Logged out.");
                currentUser = null;
                setLoggedInState(false);
            }
        });

        listButton.addActionListener(e -> {
            if (currentUser != null)
                sendCommand("LIST " + currentUser);
        });

        sendButton.addActionListener(e -> {
            String recipient = recipientField.getText().trim();
            String title = titleField.getText().trim();
            String content = composeArea.getText().trim();
            if (recipient.isEmpty() || content.isEmpty()) {
                logMessage("Client: Recipient and content cannot be empty.");
                return;
            }
            if (title.isEmpty())
                title = "(no title)";
            String command = String.format("SEND %s %s %s|%s", recipient, currentUser, title, content);
            sendCommand(command);
            composeArea.setText("");
        });

        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (socket != null && !socket.isClosed()) {
                    if (currentUser != null)
                        sendCommand("LOGOUT " + currentUser);
                    socket.close();
                }
            }
        });
    }

    private void connectToServer() {
        try {
            String ip = serverIpField.getText().trim();
            int port = Integer.parseInt(serverPortField.getText().trim());
            serverAddress = InetAddress.getByName(ip);
            serverPort = port;
            socket = new DatagramSocket();
            connectButton.setEnabled(false);
            serverIpField.setEditable(false);
            serverPortField.setEditable(false);
            setLoginAndRegisterEnabled(true);
            statusLabel.setText("Status: Connected to " + ip + ":" + port);
            logMessage("Client: Successfully connected to server. Please log in or register.");
            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (Exception e) {
            logMessage("Client Error: Could not connect. " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void listenToServer() {
        byte[] buffer = new byte[8192];
        while (socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(() -> {
                    if (message.startsWith("NEW_MAIL|")) {
                        handleIncomingMailNotification(message);
                    } else {
                        logMessage("Server: " + message);
                    }

                    if (message.startsWith("OK Logged in")) {
                        setLoggedInState(true);
                    } else if (message.startsWith("ERROR Invalid credentials")) {
                        currentUser = null;
                    }
                });
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    SwingUtilities.invokeLater(
                            () -> logMessage("Client Error: Lost connection to server. " + e.getMessage()));
                }
                break;
            }
        }
    }

    private void handleIncomingMailNotification(String rawMessage) {
        String[] parts = rawMessage.split("\\|", 4);
        if (parts.length == 4) {
            String fromUser = parts[1];
            String title = parts[2];
            logMessage(">> NEW MAIL Received from: " + fromUser + " | Title: " + title);
            logMessage(">> Use 'List My Mails' to refresh your inbox.");
        }
    }

    private void sendCommand(String command) {
        if (socket == null || socket.isClosed()) {
            logMessage("Client: Not connected to the server.");
            return;
        }
        try {
            byte[] data = command.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            logMessage("Client Error: Failed to send command. " + e.getMessage());
        }
    }

    private void logMessage(String message) {
        messageArea.append(message + "\n");
    }

    private void setLoggedInState(boolean loggedIn) {
        setLoginAndRegisterEnabled(!loggedIn);
        logoutButton.setEnabled(loggedIn);
        sendButton.setEnabled(loggedIn);
        listButton.setEnabled(loggedIn);
        recipientField.setEditable(loggedIn);
        titleField.setEditable(loggedIn);
        composeArea.setEditable(loggedIn);
        if (loggedIn) {
            statusLabel.setText("Status: Logged in as " + currentUser);
            setTitle("UDP Mail Client - " + currentUser);
        } else {
            setLoginAndRegisterEnabled(socket != null && !socket.isClosed());
            statusLabel.setText(socket != null && !socket.isClosed() ? "Status: Connected, but not logged in."
                    : "Status: Disconnected");
            setTitle("UDP Mail Client");
        }
    }

    private void setLoginAndRegisterEnabled(boolean enabled) {
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
        usernameField.setEditable(enabled);
        passwordField.setEditable(enabled);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MailClient().setVisible(true));
    }
}