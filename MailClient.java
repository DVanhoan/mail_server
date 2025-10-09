import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

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

            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            InetAddress serverAddress = InetAddress.getByName(serverIp);

            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
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
