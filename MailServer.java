import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Simple UDP Mail Server.
 * Listens on a fixed UDP port and prints sender IP, sender port and message
 * content.
 *
 * Usage:
 * java MailServer // listens on default port 9999
 *
 * The server runs indefinitely until killed (Ctrl+C).
 */
public class MailServer {
    // Fixed UDP port the server listens on
    public static final int SERVER_PORT = 9999;

    public static void main(String[] args) {
        System.out.println("Starting MailServer on UDP port " + SERVER_PORT + "...");

        // DatagramSocket used for receiving UDP packets. We bind it to SERVER_PORT so
        // OS
        // delivers packets targeting this port to us.
        try (DatagramSocket socket = new DatagramSocket(SERVER_PORT)) {
            // Buffer to receive incoming packets (max 65KB for UDP payload is smaller, but
            // 8KB is enough for simple messages)
            byte[] buffer = new byte[8192];

            while (true) {
                // DatagramPacket will be filled with incoming data and sender information
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // BLOCKING call - waits until a packet arrives
                socket.receive(packet);

                // Extract sender address (IP) and port from the packet
                String senderIp = packet.getAddress().getHostAddress();
                int senderPort = packet.getPort();

                // Extract message bytes and convert to a UTF-8 string using the packet's length
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                // Display clear information about the received mail
                System.out.println("--- Received Mail ---");
                System.out.println("From IP   : " + senderIp);
                System.out.println("From Port : " + senderPort);
                System.out.println("Content   : " + message);
                System.out.println("---------------------\n");
            }

        } catch (SocketException se) {
            System.err.println("Socket error: " + se.getMessage());
        } catch (IOException ioe) {
            System.err.println("I/O error while receiving packet: " + ioe.getMessage());
        }
    }
}
