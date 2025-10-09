import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class MailServer {
    public static final int SERVER_PORT = 9999;

    public static void main(String[] args) {
        System.out.println("Starting MailServer on UDP port " + SERVER_PORT + "...");

        try (DatagramSocket socket = new DatagramSocket(SERVER_PORT)) {
            byte[] buffer = new byte[8192];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                String senderIp = packet.getAddress().getHostAddress();
                int senderPort = packet.getPort();

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

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
