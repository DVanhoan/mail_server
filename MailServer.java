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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailServer {
    public static final int SERVER_PORT = 9999;
    private static final String USERS_FILE = "users.txt";
    private static final String MAILS_FILE = "mails.txt";

    private static final Map<String, ClientInfo> onlineUsers = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Starting MailServer on UDP port " + SERVER_PORT + "...");

        ensureFileExists(USERS_FILE);
        ensureFileExists(MAILS_FILE);

        try (DatagramSocket socket = new DatagramSocket(SERVER_PORT)) {
            byte[] buffer = new byte[8192];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                String senderIp = packet.getAddress().getHostAddress();
                int senderPort = packet.getPort();

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                System.out.println("--- Received Packet ---");
                System.out.println("From IP   : " + senderIp);
                System.out.println("From Port : " + senderPort);
                System.out.println("Raw       : " + message);

                String[] parts = message.split(" ", 2);
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "REGISTER":
                        handleRegister(parts.length > 1 ? parts[1] : "", packet.getAddress(), packet.getPort(), socket);
                        break;
                    case "LOGIN":
                        handleLogin(parts.length > 1 ? parts[1] : "", packet.getAddress(), packet.getPort(), socket);
                        break;
                    case "SEND":
                        handleSend(parts.length > 1 ? parts[1] : "", senderIp, senderPort, socket);
                        break;
                    case "LIST":
                        handleList(parts.length > 1 ? parts[1] : "", packet.getAddress(), packet.getPort(), socket);
                        break;
                    default:
                        sendResponse(socket, packet.getAddress(), packet.getPort(), "ERROR Unknown command");
                }

                System.out.println("-----------------------\n");
            }

        } catch (SocketException se) {
            System.err.println("Socket error: " + se.getMessage());
        } catch (IOException ioe) {
            System.err.println("I/O error while receiving packet: " + ioe.getMessage());
        }
    }

    private static class ClientInfo {
        public final InetAddress address;
        public final int port;

        public ClientInfo(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    private static void ensureFileExists(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) {
                f.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Could not create file " + path + ": " + e.getMessage());
        }
    }

    private static void sendResponse(DatagramSocket socket, InetAddress addr, int port, String text) {
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            DatagramPacket resp = new DatagramPacket(data, data.length, addr, port);
            socket.send(resp);
        } catch (IOException e) {
            System.err.println("Failed to send response to " + addr + ":" + port + " -> " + e.getMessage());
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

        String createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String line = username + "|" + password + "|" + createdAt;
        appendLine(USERS_FILE, line);
        sendResponse(socket, addr, port, "OK Registered");
    }

    private static boolean userExists(String username) {
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\\|");
                if (p.length > 0 && p[0].equals(username))
                    return true;
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private static void handleLogin(String payload, InetAddress addr, int port, DatagramSocket socket) {
        String[] toks = payload.split(" ", 2);
        if (toks.length < 2) {
            sendResponse(socket, addr, port, "ERROR LOGIN requires username and password");
            return;
        }
        String username = toks[0].trim();
        String password = toks[1].trim();

        if (!checkCredentials(username, password)) {
            sendResponse(socket, addr, port, "ERROR Invalid credentials");
            return;
        }

        // record user as online
        onlineUsers.put(username, new ClientInfo(addr, port));
        sendResponse(socket, addr, port, "OK LoggedIn");
    }

    private static boolean checkCredentials(String username, String password) {
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\\|");
                if (p.length >= 2 && p[0].equals(username) && p[1].equals(password))
                    return true;
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private static void handleSend(String payload, String senderIp, int senderPort, DatagramSocket socket) {
        String recipient = "";
        String fromUser = "unknown";
        String rest = "";

        String[] head = payload.split(" ", 3);
        if (head.length >= 3) {
            recipient = head[0].trim();
            fromUser = head[1].trim();
            rest = head[2];
        } else if (head.length == 2) {
            recipient = head[0].trim();
            rest = head[1];
        } else {
            return;
        }

        String title = "(no title)";
        String content = rest;
        int sep = rest.indexOf('|');
        if (sep >= 0) {
            title = rest.substring(0, sep).trim();
            content = rest.substring(sep + 1).trim();
        }

        String serverTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // Persist mail as: senderIp|recipient|fromUser|serverTime|title|content
        String stored = senderIp + "|" + recipient + "|" + fromUser + "|" + serverTime + "|"
                + escapePipes(title) + "|" + escapePipes(content);
        appendLine(MAILS_FILE, stored);

        // Notify recipient if online
        ClientInfo ci = onlineUsers.get(recipient);
        if (ci != null) {
            String notif = "NEW_MAIL " + senderIp + " " + fromUser + " " + serverTime + " " + title;
            sendResponse(socket, ci.address, ci.port, notif);
        }
    }

    private static void handleList(String username, InetAddress addr, int port, DatagramSocket socket) {
        username = username.trim();
        List<String> results = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(MAILS_FILE))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\\|");
                if (p.length >= 6) {
                    String recipient = p[1];
                    if (recipient.equals(username)) {
                        // reconstruct title and content
                        String title = unescapePipes(p[4]);
                        String content = unescapePipes(p[5]);
                        String fromIp = p[0];
                        String serverTime = p[3];
                        results.add(
                                "From:" + fromIp + " Time:" + serverTime + " Title:" + title + " Content:" + content);
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }

        if (results.isEmpty()) {
            sendResponse(socket, addr, port, "OK LIST 0");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String r : results) {
                sb.append(r).append('\n');
            }
            sendResponse(socket, addr, port, "OK LIST " + results.size() + "\n" + sb.toString());
        }
    }

    private static void appendLine(String path, String line) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, true))) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Failed to append to " + path + ": " + e.getMessage());
        }
    }

    private static String escapePipes(String s) {
        return s.replace("|", "\\|");
    }

    private static String unescapePipes(String s) {
        return s.replace("\\|", "|");
    }
}
