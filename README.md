# Simple Java UDP Mail Server and Client

This project contains two simple Java programs demonstrating UDP communication using
DatagramSocket and DatagramPacket.

Files:
- `MailServer.java` - UDP server that listens on a fixed port and prints sender IP/port and message.
- `MailClient.java` - Command-line client to enter a message and send it to the server.

How to compile (Windows PowerShell):

```powershell
cd d:\namba\hocky1\laptrinhmang\mail_server
javac MailServer.java MailClient.java
```

How to run (in two separate PowerShell windows):

Run the server (listens on port 9999):

```powershell
java MailServer
```

Run the client and send a message:

```powershell
java MailClient
```

Enter the server IP (e.g., `127.0.0.1`), port `9999`, and type your message. Finish message input with
an empty line. The server will print the sender IP/port and the message content when it receives the packet.

Notes:
- Uses UTF-8 encoding for message bytes.
- Server runs indefinitely until terminated.
