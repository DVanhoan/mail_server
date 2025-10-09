import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Simple UDP Mail Client.
 * Prompts the user for server IP, server port and message content, then sends
 * the message
 * as a UDP packet to the server using DatagramSocket/DatagramPacket.
 *
 * Usage:
 * java MailClient
 */
public class MailClient {
    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter server IP (e.g. 127.0.0.1): ");
            String serverIp = reader.readLine().trim();

            System.out.print("Enter server port (e.g. 9999): ");
            String portLine = reader.readLine().trim();
            int serverPort = Integer.parseInt(portLine);

            System.out.println("Enter your message. Finish with an empty line:");
            StringBuilder sb = new StringBuilder();
            String line;
            // Read multiple lines until an empty line
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    break;
                sb.append(line).append('\n');
            }

            String message = sb.toString();
            if (message.isEmpty()) {
                System.out.println("No message entered. Exiting.");
                return;
            }

            // Convert message to bytes (UTF-8)
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            // Resolve server IP address
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // Create a DatagramPacket containing the message, addressed to server IP/port
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);

            // DatagramSocket used to send the UDP packet. If we don't bind it to a local
            // port,
            // the OS will choose an ephemeral port automatically. After sending we close
            // it.
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
                // We can display the local IP/port used for sending by inspecting the socket's
                // local address/port
                String localIp = socket.getLocalAddress().getHostAddress();
                int localPort = socket.getLocalPort();
                System.out.println("Message sent to " + serverAddress.getHostAddress() + ":" + serverPort);
                System.out.println("Local (sender) IP:Port = " + localIp + ":" + localPort);
            }

        } catch (SocketException se) {
            System.err.println("Socket error: " + se.getMessage());
        } catch (IOException ioe) {
            System.err.println("I/O error: " + ioe.getMessage());
        } catch (NumberFormatException nfe) {
            System.err.println("Invalid port number.");
        }
    }
}
