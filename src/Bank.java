import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Bank {
    private KeyPair keyPair;
    private static final String FILE_NAME = "password";
    private static final String publicKeyPath = "public_key.pem";
    private static final String privateKeyPath = "private_key.pem";
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public static RSAPublicKey readPublicKey(File file) throws Exception {
        String key = new String(Files.readAllBytes(file.toPath()), Charset.defaultCharset());

        String publicKeyPEM = key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END PUBLIC KEY-----", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
    public RSAPrivateKey readPrivateKey(File file) throws Exception {
        String key = new String(Files.readAllBytes(file.toPath()), Charset.defaultCharset());

        String privateKeyPEM = key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END PRIVATE KEY-----", "");

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }
    private KeyPair loadKeyPair(String publicKeyPath, String privateKeyPath) throws Exception {
        File pub = new File(publicKeyPath);
        File priv = new File(privateKeyPath);

        publicKey = readPublicKey(pub);
        privateKey = readPrivateKey(priv);

        return new KeyPair(publicKey, privateKey);
    }

    public Bank() {
        try {

            this.keyPair = loadKeyPair(publicKeyPath, privateKeyPath);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }  

    public void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    new Thread(new ClientHandler(clientSocket, keyPair,FILE_NAME,"balance")).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Server <port number>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        if (port < 1024 || port > 65535) {
            System.out.println("The port number should be a user-defined number between 1024 and 65535");
            System.exit(1);
        }
        Bank server = new Bank();
        server.startServer(port);
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private KeyPair keyPair;
    private String fileName;
    private String balanceFile;
    private String userId;

    public ClientHandler(Socket clientSocket, KeyPair keyPair, String fileName, String balanceFile) {
        this.clientSocket = clientSocket;
        this.keyPair = keyPair;
        this.fileName = fileName;
        this.balanceFile = balanceFile;
    }

    @Override
    public void run() {
        try (ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream())) {

            outputStream.writeObject(keyPair.getPublic());
            outputStream.flush();
            
           
            authenticateClient(inputStream, outputStream);

            
            handleClientRequests(inputStream, outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void authenticateClient(ObjectInputStream inputStream, ObjectOutputStream outputStream) throws Exception {
        String id;
        String password;
        boolean credentialsValid = false;
        do {
            String encryptedKey = (String) inputStream.readObject();
            String encryptedCredentials = (String) inputStream.readObject();

            String decryptedSymmetricKey = decryptWithPrivateKey(encryptedKey);
            SecretKey symmetricKey = new SecretKeySpec(Base64.getDecoder().decode(decryptedSymmetricKey), "AES");

            String decryptedCredentials = decryptWithSymmetricKey(encryptedCredentials, symmetricKey);
            String[] parts = decryptedCredentials.split("\\|\\|");
            id = parts[0];
            password = parts[1];

            credentialsValid = validateCredentials(id, password);
            userId = credentialsValid ? id : null;

            String response = credentialsValid ? "ID and password are correct" : "ID or password is incorrect";
            outputStream.writeObject(response);
        } while (!credentialsValid);
    }

    private void handleClientRequests(ObjectInputStream inputStream, ObjectOutputStream outputStream) throws Exception {
        while (true) {
            int requestType = (int) inputStream.readObject();
            switch (requestType) {
                case 1:
                    handleMoneyTransfer(inputStream, outputStream);
                    break;
                case 2:
                    handleBalanceCheck(outputStream);
                    break;
                case 3:
                    System.out.println("Closing connection with client.");
                    return;
                default:
                    System.out.println("Invalid request type.");
                    break;
            }
        }
    }

    private void handleMoneyTransfer(ObjectInputStream inputStream, ObjectOutputStream outputStream) throws Exception {
        int accountChoice = (int) inputStream.readObject();
        String recipientId = (String) inputStream.readObject();
        double amount = (double) inputStream.readObject();

        String response = processTransfer(accountChoice, recipientId, amount);
        outputStream.writeObject(response);
        outputStream.flush();
    }

    private void handleBalanceCheck(ObjectOutputStream outputStream) throws Exception {
        AccountDetails userDetails = getAccountDetailsForUser(userId);
        outputStream.writeObject(String.valueOf(userDetails.getSavingsBalance()));
        outputStream.writeObject(String.valueOf(userDetails.getCheckingBalance()));
        outputStream.flush();
    }

    
    private String decryptWithPrivateKey(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedData);
    }

    private String decryptWithSymmetricKey(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedData);
    }

    private boolean validateCredentials(String id, String password) {
        FileProcessor fileProcessor = new FileProcessor(fileName);
        boolean isValid = false;

        try {
            String outputLine;
            while ((outputLine = fileProcessor.readNextLineFromFile()) != null) {
                String[] parts = outputLine.split(" ", 2);
                if (id.equals(parts[0]) && password.equals(parts[1])) {
                    isValid = true;
                    break;
                }
            }
        } catch (Exception ex) {
            System.err.println("Exception message: " + ex.getMessage());
        }
        return isValid;
    }
    private AccountDetails getAccountDetailsForUser(String userId) {
        FileProcessor fileProcessor = new FileProcessor("balance");
        String checking ="";
        String savings="";

        try {
            String outputLine;
            while ((outputLine = fileProcessor.readNextLineFromFile()) != null) {
                String[] parts = outputLine.split(" ", 3);
                if(userId.equals(parts[0])){
                    savings = parts[1];
                    checking = parts[2];
                }
            }
        } catch (Exception ex) {
            System.err.println("Exception message: " + ex.getMessage());
        }
        return new AccountDetails(Double.parseDouble(savings), Double.parseDouble(checking));
    }
    private Map<String, AccountDetails> loadAccountDetails(String filePath) {
        Map<String, AccountDetails> detailsMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String userId = parts[0];
                double savingsBalance = Double.parseDouble(parts[1]);
                double checkingBalance = Double.parseDouble(parts[2]);
                detailsMap.put(userId, new AccountDetails(savingsBalance, checkingBalance));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return detailsMap;
    }
    private boolean sufficientfunds(int accountChoice,double amount) {
        FileProcessor fileProcessor = new FileProcessor("balance");
        boolean isValid = false;

        try {
            String outputLine;
            while ((outputLine = fileProcessor.readNextLineFromFile()) != null) {
                String[] parts = outputLine.split(" ", 3);
                if(accountChoice == 1){
                    if(amount > Double.parseDouble(parts[1])){
                        isValid = true;
                        break;
                    }

                } else if (accountChoice==2) {
                    if(amount > Double.parseDouble(parts[2])){
                        isValid = true;
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Exception message: " + ex.getMessage());
        }

        return isValid;
    }

     private boolean validateIDCredentials(String id) {
        FileProcessor fileProcessor = new FileProcessor(fileName);
        boolean isValid = false;

        try {
            String outputLine;
            while ((outputLine = fileProcessor.readNextLineFromFile()) != null) {
                String[] parts = outputLine.split(" ", 2);
                if (id.equals(parts[0])) {
                    isValid = true;
                    break;
                }
            }
        } catch (Exception ex) {
            System.err.println("Exception message: " + ex.getMessage());
        }

        return isValid;
    }

    private String processTransfer(int accountChoice, String recipientId, double amount) {
        String response = "";
        final String BALANCE_FILE = "balance";
        Map<String, AccountDetails> accountDetailsMap = loadAccountDetails(BALANCE_FILE);
        AccountDetails userDetails = accountDetailsMap.get(userId);
        if(!validateIDCredentials(recipientId)){
            response = "the recipient’s ID does not exist";
        } else if (sufficientfunds(accountChoice, amount)) {
            response = "Your account does not have enough funds";
        } else{
            if (accountChoice == 1) {
                userDetails.setSavingsBalance(userDetails.getSavingsBalance() - amount);
                accountDetailsMap.get(recipientId).setSavingsBalance(accountDetailsMap.get(recipientId).getSavingsBalance() + amount);

            } else {
                userDetails.setCheckingBalance(userDetails.getCheckingBalance() - amount);
                accountDetailsMap.get(recipientId).setCheckingBalance(accountDetailsMap.get(recipientId).getCheckingBalance() + amount);
            }


            
            writeUpdatedBalances(accountDetailsMap, BALANCE_FILE);
            response = "your transaction is successful";
        }
        return response;
    }
     
    private void writeUpdatedBalances(Map<String, AccountDetails> accountDetailsMap, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
            for (Map.Entry<String, AccountDetails> entry : accountDetailsMap.entrySet()) {
                String line = entry.getKey() + " " + entry.getValue().getSavingsBalance() + " " + entry.getValue().getCheckingBalance();
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
 
class FileProcessor {
    private BufferedReader reader;

    public FileProcessor(String filePath) {
        try {
            this.reader = new BufferedReader(new FileReader(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public String readNextLineFromFile() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }
}
class AccountDetails {
    private double savingsBalance;
    private double checkingBalance;

    public AccountDetails(double savingsBalance, double checkingBalance) {
        this.savingsBalance = savingsBalance;
        this.checkingBalance = checkingBalance;
    }

    public double getSavingsBalance() {
        return savingsBalance;
    }

    public void setSavingsBalance(double savingsBalance) {
        this.savingsBalance = savingsBalance;
    }

    public double getCheckingBalance() {
        return checkingBalance;
    }

    public void setCheckingBalance(double checkingBalance) {
        this.checkingBalance = checkingBalance;
    }
}
