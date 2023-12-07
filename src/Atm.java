import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.PublicKey;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Scanner;
import java.util.Base64;

public class Atm {
    private PublicKey serverPublicKey;
    private SecretKey symmetricKey;

    private void connectToBankServer(String host, int port) throws IOException, ClassNotFoundException {
        Socket socket = new Socket(host, port);
        ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());

        serverPublicKey = (PublicKey) inputStream.readObject();
        System.out.println("Connected to bank server and received public key.");

        startATMOperations(socket, inputStream, outputStream);
    }

    private void generateSymmetricKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        symmetricKey = keyGen.generateKey();
    }

    private String encryptWithPublicKey(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    private String encryptWithSymmetricKey(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, symmetricKey);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    private void startATMOperations(Socket socket, ObjectInputStream inputStream, ObjectOutputStream outputStream) {
        try {
            Scanner scanner = new Scanner(System.in);
            boolean authenticated = false;

            while (!authenticated) {
                System.out.println("Starting ATM Operations");

                System.out.print("Enter ID: ");
                String id = scanner.nextLine();
                System.out.print("Enter Password: ");
                String password = scanner.nextLine();

                generateSymmetricKey();
                System.out.println("Symmetric key generated.");

                String encryptedKey = encryptWithPublicKey(Base64.getEncoder().encodeToString(symmetricKey.getEncoded()));
                String encryptedCredentials = encryptWithSymmetricKey(id + "||" + password);

                System.out.println("Sending encrypted data to the server...");
                outputStream.writeObject(encryptedKey);
                outputStream.writeObject(encryptedCredentials);
                System.out.println("Encrypted data sent to server.");

                String serverResponse;
                try {
                    serverResponse = (String) inputStream.readObject();
                    System.out.println(serverResponse);
                } catch (EOFException e) {
                    System.out.println("Server disconnected unexpectedly.");
                    break;
                }

                System.out.println("Message from server: " + serverResponse);

                if (serverResponse.equals("ID and password are correct")) {
                    authenticated = true;
                    displayMainMenu(socket, outputStream, inputStream, scanner);
                } else {
                    System.out.println("Invalid ID or password. Please try again.");

                }
            }
            scanner.close();
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (SocketException e) {
            e.printStackTrace();
            System.out.println("Server Socket disconnected unexpectedly.");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleMoneyTransfer(Socket socket, ObjectOutputStream outputStream, ObjectInputStream inputStream, Scanner scanner) throws IOException, ClassNotFoundException {
        outputStream.writeObject(1);
        outputStream.flush();
        int accountChoice;
        do {
            System.out.println("Please select an account (enter 1 or 2):");
            System.out.println("1. Savings");
            System.out.println("2. Checking");

            accountChoice = scanner.nextInt();
            if (accountChoice != 1 && accountChoice != 2) {
                System.out.println("Incorrect input");
            }
        } while (accountChoice != 1 && accountChoice != 2);

        System.out.print("Enter recipient's ID: ");
        String recipientId = scanner.next();
        System.out.print("Enter amount to transfer: ");
        double amount = scanner.nextDouble();

        outputStream.writeObject(accountChoice);
        outputStream.writeObject(recipientId);
        outputStream.writeObject(amount);

    
        outputStream.flush();


        String serverResponse = (String) inputStream.readObject();
        System.out.println(serverResponse);
    }
    private void handleCheckBalance(Socket socket, ObjectOutputStream outputStream, ObjectInputStream inputStream, Scanner scanner) throws IOException, ClassNotFoundException {
       
        outputStream.writeObject(2); 
        outputStream.flush();

      
        String savingsBalance = (String) inputStream.readObject();
        String checkingBalance = (String) inputStream.readObject();

      
        System.out.println("Your savings account balance: " + savingsBalance);
        System.out.println("Your checking account balance: " + checkingBalance);
    }
    private void displayMainMenu(Socket socket, ObjectOutputStream outputStream, ObjectInputStream inputStream, Scanner scanner) throws IOException, ClassNotFoundException {
        int choice;
       do {
           System.out.println("Please select one of the following actions (enter 1, 2, or 3):");
           System.out.println("1. Transfer money");
           System.out.println("2. Check account balance");
           System.out.println("3. Exit");

            choice= scanner.nextInt();
           switch (choice) {
               case 1:
                   handleMoneyTransfer(socket, outputStream, inputStream, scanner);
                   break;
               case 2:
                   handleCheckBalance(socket, outputStream, inputStream, scanner);
                   break;
               case 3:
                   System.out.println("Exiting...");
                   outputStream.writeObject(3);
                   break;
               default:
                   System.out.println("Invalid choice, please enter 1, 2, or 3.");
                   displayMainMenu(socket, outputStream, inputStream, scanner); 
           }
       }while (choice !=3);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.out.println("Usage: java Client <host> <port number>");
                return;
            }
    
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            if (host.equals("localhost") || host.isEmpty()) {
                System.out.println("The host name should not be localhost or empty");
                System.exit(1);
            }
    
            if (port < 1024 || port > 65535) {
                System.out.println("The port number should be a user-defined number between 1024 and 65535");
                System.exit(1);
            }
            Atm atm = new Atm();
            atm.connectToBankServer(host, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
