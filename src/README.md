Name: Rushabh Mehta
Email: rmehta13@binghamton.edu

Programming Language: Java

Code for performing encryption/decryption
Symmetric Key - AES
PublicKey Encryption/Decryption - RSA/ECB/PKCS1Padding
SymmetricKey Encryption/Decryption - AES
For Reading
PublicKey - RSA
PrivateKey - RSA

Whether your code was tested on remote.cs.binghamton.edu. - Yes it was tested on remote

Steps to execute program
0. Go to "cd src"
1. Run "make compile"
2. Run Bank.java on any port with command "make server" which will run "java Bank.java portnumber" which will start the server
(Multiple ATM can connect to Bank Server)
3. Run Atm.java with host name and port number that of the server "make client" which will take hostname and password from user "java Atm.java remote01-7.cs.binghamton.edu 8090"
4. After that Atm and Bank are connected. the Atm will ask for ID and Password which will be encrypted with symmetric key which is generated after that symmetric key is encrypted with public key and send over server and similarly ID and Password are send with encrypted symmetric key.
5. After sending username and password from the client side server will check in "password" file that if id and password match if yes it will return "correct id/password" and close connection
6. If the id/password is incorrect then it will return "Incorrect id/password" and ask for id and password again
7. If id and password are incorrect then atm will ask for correct id and password
8. If id and password are correct then atm will display the main menu 1.Transfer 2.Check Balance 3.Exit
9. Then you select the options using numbers 1,2,3 anyother number or character will not be counted 
10. If you select 1 then it will ask for 1.Savings 2.Checkings then after selecting 1 or 2 it will ask for receipent Id and amount to transfer then if you have that much amount it will be transfered to receipent account of similar type
11. If you select 2 it will go to "balance" and will return checking and savings balance
12. If you select 3 it will Exit the connection

public_key.pem and private_key.pem are two file generated using openssl where public key is extract from private key using RSA
