import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MailClient {
    private static final int BUFFER_SIZE = 8192;

    public static void main(String[] args) {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter server IP (e.g. 127.0.0.1): ");
            String serverIp = console.readLine().trim();

            System.out.print("Enter server port (e.g. 9999): ");
            int serverPort = Integer.parseInt(console.readLine().trim());

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(0);

                AtomicBoolean running = new AtomicBoolean(true);

                Thread listener = new Thread(() -> {
                    byte[] buf = new byte[BUFFER_SIZE];
                    while (running.get()) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        try {
                            socket.receive(p);
                            String s = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                            System.out.println("\n[Server->You] " + s + "\n> ");
                        } catch (IOException e) {
                            if (running.get()) {
                                System.err.println("Listener error: " + e.getMessage());
                            }
                        }
                    }
                });
                listener.setDaemon(true);
                listener.start();

                System.out.println("Commands: register, login, send, list, quit");
                String currentUser = null;

                String line;
                while (true) {
                    System.out.print("> ");
                    line = console.readLine();
                    if (line == null)
                        break;
                    line = line.trim();
                    if (line.isEmpty())
                        continue;

                    String[] parts = line.split(" ", 2);
                    String cmd = parts[0].toLowerCase();

                    switch (cmd) {
                        case "register":
                            if (parts.length < 2) {
                                System.out.println("Usage: register username password");
                                break;
                            }
                            sendCommand(socket, serverIp, serverPort, "REGISTER " + parts[1]);
                            break;
                        case "login":
                            if (parts.length < 2) {
                                System.out.println("Usage: login username password");
                                break;
                            }
                            sendCommand(socket, serverIp, serverPort, "LOGIN " + parts[1]);
                            String[] creds = parts[1].split(" ", 2);
                            if (creds.length >= 1)
                                currentUser = creds[0];
                            break;
                        case "send":
                            if (parts.length < 2) {
                                System.out.println("Usage: send recipient title|content");
                                break;
                            }
                            String payload = parts[1];
                            if (currentUser != null) {
                                String[] phead = payload.split(" ", 2);
                                if (phead.length < 2) {
                                    System.out.println("Usage: send recipient title|content");
                                    break;
                                }
                                String recipient = phead[0];
                                String rest = phead[1];
                                sendCommand(socket, serverIp, serverPort,
                                        "SEND " + recipient + " " + currentUser + " " + rest);
                            } else {
                                sendCommand(socket, serverIp, serverPort, "SEND " + payload);
                            }
                            break;
                        case "list":
                            String userToList = currentUser;
                            if (parts.length >= 2 && !parts[1].trim().isEmpty())
                                userToList = parts[1].trim();
                            if (userToList == null) {
                                System.out.println("No user specified and not logged in. Use: list username");
                                break;
                            }
                            sendCommand(socket, serverIp, serverPort, "LIST " + userToList);
                            break;
                        case "quit":
                            running.set(false);
                            socket.close();
                            System.out.println("Exiting.");
                            return;
                        default:
                            System.out.println("Unknown command: " + cmd);
                    }
                }

            } catch (SocketException se) {
                System.err.println("Socket error: " + se.getMessage());
            }

        } catch (IOException ioe) {
            System.err.println("I/O error: " + ioe.getMessage());
        }
    }

    private static void sendCommand(DatagramSocket socket, String serverIp, int serverPort, String text) {
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(serverIp);
            DatagramPacket p = new DatagramPacket(data, data.length, addr, serverPort);
            socket.send(p);
            System.out.println("(Sent) " + text);
        } catch (IOException e) {
            System.err.println("Failed to send: " + e.getMessage());
        }
    }
}
