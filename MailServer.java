
// MailServer.java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MailServer {
    public static final int SERVER_PORT = 9999;
    private static final String SERVER_DATA_DIR = "server_data";
    private static final String USERS_DIR = SERVER_DATA_DIR + File.separator + "users";
    private static final String MAILBOXES_DIR = SERVER_DATA_DIR + File.separator + "mailboxes";

    private static final Map<String, ClientInfo> onlineUsers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Starting MailServer on UDP port " + SERVER_PORT + "...");
        ensureServerDirectoriesExist();

        try (DatagramSocket socket = new DatagramSocket(SERVER_PORT)) {
            byte[] buffer = new byte[8192];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                new Thread(() -> handlePacket(socket, packet)).start();
            }
        } catch (SocketException se) {
            System.err.println("Socket error: " + se.getMessage());
            se.printStackTrace();
        } catch (IOException ioe) {
            System.err.println("I/O error while receiving packet: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    private static void handlePacket(DatagramSocket socket, DatagramPacket packet) {
        String senderIp = packet.getAddress().getHostAddress();
        int senderPort = packet.getPort();
        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

        System.out.println("--- Received Packet ---");
        System.out.println("From IP   : " + senderIp + ":" + senderPort);
        System.out.println("Raw       : " + message);

        String[] parts = message.split(" ", 2);
        String command = parts[0].toUpperCase();
        String payload = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "REGISTER":
                handleRegister(payload, packet.getAddress(), packet.getPort(), socket);
                break;
            case "LOGIN":
                handleLogin(payload, packet.getAddress(), packet.getPort(), socket);
                break;
            case "LOGOUT":
                handleLogout(payload);
                break;
            case "SEND":
                handleSend(payload, packet.getAddress(), packet.getPort(), socket);
                break;
            case "LIST":
                handleList(payload, packet.getAddress(), packet.getPort(), socket);
                break;
            default:
                sendResponse(socket, packet.getAddress(), packet.getPort(), "ERROR Unknown command");
        }
        System.out.println("-----------------------\n");
    }

    private static class ClientInfo {
        public final InetAddress address;
        public final int port;

        public ClientInfo(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    private static void handleRegister(String payload, InetAddress addr, int port, DatagramSocket socket) {
        String[] toks = payload.split(" ", 2);
        if (toks.length < 2) {
            sendResponse(socket, addr, port, "ERROR REGISTER requires username and password");
            return;
        }
        String username = toks[0].trim();
        String password = toks[1].trim();

        if (userExists(username)) {
            sendResponse(socket, addr, port, "ERROR User already exists");
            return;
        }

        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            sendResponse(socket, addr, port, "ERROR Server-side hashing error");
            return;
        }

        File userFile = new File(USERS_DIR, username + ".txt");
        String createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile))) {
            writer.write("username: " + username);
            writer.newLine();
            writer.write("password: " + hashedPassword);
            writer.newLine();
            writer.write("createdAt: " + createdAt);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to create user file for " + username);
            e.printStackTrace();
            sendResponse(socket, addr, port, "ERROR Server failed to create user file");
            return;
        }

        sendResponse(socket, addr, port, "OK Registered successfully");
    }

    private static void handleLogin(String payload, InetAddress addr, int port, DatagramSocket socket) {
        String[] toks = payload.split(" ", 2);
        if (toks.length < 2) {
            sendResponse(socket, addr, port, "ERROR LOGIN requires username and password");
            return;
        }
        String username = toks[0].trim();
        String password = toks[1].trim();

        if (checkCredentials(username, password)) {
            onlineUsers.put(username, new ClientInfo(addr, port));
            System.out.println("User '" + username + "' logged in from " + addr.getHostAddress() + ":" + port);
            sendResponse(socket, addr, port, "OK Logged in successfully");
        } else {
            sendResponse(socket, addr, port, "ERROR Invalid credentials");
        }
    }

    private static void handleLogout(String username) {
        username = username.trim();
        if (onlineUsers.remove(username) != null) {
            System.out.println("User '" + username + "' logged out.");
        }
    }

    private static void handleSend(String payload, InetAddress senderAddr, int senderPort, DatagramSocket socket) {
        String[] head = payload.split(" ", 3);
        if (head.length < 3) {
            sendResponse(socket, senderAddr, senderPort,
                    "ERROR SEND command format is: <recipient> <fromUser> <title|content>");
            return;
        }

        String recipient = head[0].trim();
        String fromUser = head[1].trim();
        String rest = head[2];

        if (!onlineUsers.containsKey(fromUser)) {
            sendResponse(socket, senderAddr, senderPort, "ERROR You must be logged in to send mail.");
            return;
        }

        if (!userExists(recipient)) {
            sendResponse(socket, senderAddr, senderPort, "ERROR Recipient '" + recipient + "' does not exist.");
            return;
        }

        String title = "(no title)";
        String content = rest;
        int sep = rest.indexOf('|');
        if (sep != -1) {
            title = rest.substring(0, sep).trim();
            content = rest.substring(sep + 1).trim();
        }

        File recipientInbox = new File(MAILBOXES_DIR + File.separator + recipient + File.separator + "inbox");
        if (!recipientInbox.exists()) {
            recipientInbox.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        File mailFile = new File(recipientInbox, timestamp + ".txt");
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mailFile))) {
            writer.write("from: " + fromUser);
            writer.newLine();
            writer.write("to: " + recipient);
            writer.newLine();
            writer.write("date: " + date);
            writer.newLine();
            writer.write("title: " + title);
            writer.newLine();
            writer.newLine();
            writer.write(content);
        } catch (IOException e) {
            System.err.println("Failed to save mail file for " + recipient);
            e.printStackTrace();
            sendResponse(socket, senderAddr, senderPort, "ERROR Server failed to save mail");
            return;
        }

        ClientInfo recipientInfo = onlineUsers.get(recipient);
        if (recipientInfo != null) {
            String notification = "NEW_MAIL|" + fromUser + "|" + title + "|" + content;
            sendResponse(socket, recipientInfo.address, recipientInfo.port, notification);
        }

        sendResponse(socket, senderAddr, senderPort, "OK Mail sent successfully to " + recipient);
    }

    private static void handleList(String username, InetAddress addr, int port, DatagramSocket socket) {
        username = username.trim();
        if (username.isEmpty()) {
            sendResponse(socket, addr, port, "ERROR LIST requires a username");
            return;
        }

        File userInbox = new File(MAILBOXES_DIR + File.separator + username + File.separator + "inbox");
        if (!userInbox.exists() || !userInbox.isDirectory()) {
            sendResponse(socket, addr, port, "OK No mails found.");
            return;
        }

        File[] mailFiles = userInbox.listFiles((dir, name) -> name.endsWith(".txt"));
        if (mailFiles == null || mailFiles.length == 0) {
            sendResponse(socket, addr, port, "OK No mails found.");
            return;
        }

        Arrays.sort(mailFiles, (f1, f2) -> f2.getName().compareTo(f1.getName()));

        StringBuilder sb = new StringBuilder("OK Your mails:\n\n");
        for (File mailFile : mailFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(mailFile))) {
                String from = "N/A", title = "N/A", date = "N/A";
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("from: "))
                        from = line.substring(6);
                    if (line.startsWith("title: "))
                        title = line.substring(7);
                    if (line.startsWith("date: "))
                        date = line.substring(6);
                    if (line.trim().isEmpty())
                        break;
                }
                sb.append(String.format("Time: %s | From: %s | Title: %s\n---\n", date, from, title));
            } catch (IOException e) {
                System.err.println("Error reading mail file: " + mailFile.getName());
            }
        }
        sendResponse(socket, addr, port, sb.toString());
    }

    private static boolean userExists(String username) {
        File userFile = new File(USERS_DIR, username + ".txt");
        return userFile.exists();
    }

    private static boolean checkCredentials(String username, String password) {
        File userFile = new File(USERS_DIR, username + ".txt");
        if (!userFile.exists())
            return false;

        String hashedPassword = hashPassword(password);
        if (hashedPassword == null)
            return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("password: ")) {
                    String storedHash = line.substring(10);
                    return storedHash.equals(hashedPassword);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading user file for credential check: " + username);
        }
        return false;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void ensureServerDirectoriesExist() {
        File dataDir = new File(SERVER_DATA_DIR);
        if (!dataDir.exists())
            dataDir.mkdirs();

        File usersDir = new File(USERS_DIR);
        if (!usersDir.exists())
            usersDir.mkdirs();

        File mailboxesDir = new File(MAILBOXES_DIR);
        if (!mailboxesDir.exists())
            mailboxesDir.mkdirs();
    }

    private static void sendResponse(DatagramSocket socket, InetAddress addr, int port, String text) {
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            DatagramPacket resp = new DatagramPacket(data, data.length, addr, port);
            socket.send(resp);
        } catch (IOException e) {
            System.err.println("Failed to send response to " + addr + ":" + port);
        }
    }
}