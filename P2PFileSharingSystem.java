import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// User Authentication Class
class UserAuthentication {
    private final Map<String, String> users = new ConcurrentHashMap<>();

    // Register a new user
    public boolean register(String username, String password) {
        if (users.containsKey(username)) {
            return false;
        }
        users.put(username, password);
        return true;
    }

    // Login a user
    public boolean login(String username, String password) {
        return users.containsKey(username) && users.get(username).equals(password);
    }
}

// Peer class for P2P communication
class Peer {
    private final String username;
    private final ServerSocket serverSocket;
    private final ExecutorService executorService;

    public Peer(String username, int port) throws IOException {
        this.username = username;
        this.serverSocket = new ServerSocket(port);
        this.executorService = Executors.newCachedThreadPool();
        System.out.println(username + " is online on port " + port);
    }

    // Start listening for incoming file requests
    public void startListening() {
        executorService.execute(() -> {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.execute(new FileSender(clientSocket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Request a file from another peer
    public void requestFile(String peerAddress, int peerPort, String fileName) {
        try (Socket socket = new Socket(peerAddress, peerPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF(fileName);
            String response = dis.readUTF();
            if ("File Not Found".equals(response)) {
                System.out.println("File not found on peer: " + peerAddress);
            } else {
                long fileSize = dis.readLong();
                byte[] buffer = new byte[4096];
                try (FileOutputStream fos = new FileOutputStream(fileName)) {
                    int bytesRead;
                    while (fileSize > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        fileSize -= bytesRead;
                    }
                }
                System.out.println("File received: " + fileName);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Send a file to a peer upon request
    private class FileSender implements Runnable {
        private final Socket clientSocket;

        public FileSender(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                 DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {

                String requestedFile = dis.readUTF();
                File file = new File(requestedFile);
                if (file.exists()) {
                    dos.writeUTF("File Found");
                    dos.writeLong(file.length());
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    dos.writeUTF("File Not Found");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

// Main Application Class
public class P2PFileSharingSystem {
    private static final UserAuthentication auth = new UserAuthentication();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to the P2P Education and Research System");

        while (true) {
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            int option = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (option == 3) {
                System.out.println("Exiting...");
                break;
            }

            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            if (option == 1) {
                if (auth.register(username, password)) {
                    System.out.println("Registration successful.");
                } else {
                    System.out.println("User already exists.");
                }
            } else if (option == 2) {
                if (auth.login(username, password)) {
                    System.out.println("Login successful.");
                    try {
                        System.out.print("Enter port to listen on: ");
                        int port = scanner.nextInt();
                        Peer peer = new Peer(username, port);
                        peer.startListening();

                        System.out.println("1. Request File");
                        System.out.println("2. Exit");
                        System.out.print("Choose an option: ");
                        int peerOption = scanner.nextInt();
                        scanner.nextLine(); // Consume newline

                        if (peerOption == 1) {
                            System.out.print("Enter peer address: ");
                            String peerAddress = scanner.nextLine();
                            System.out.print("Enter peer port: ");
                            int peerPort = scanner.nextInt();
                            scanner.nextLine(); // Consume newline
                            System.out.print("Enter file name: ");
                            String fileName = scanner.nextLine();
                            peer.requestFile(peerAddress, peerPort, fileName);
                        } else if (peerOption == 2) {
                            System.out.println("Exiting...");
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Login failed. Invalid credentials.");
                }
            }
        }
        scanner.close();
    }
}
