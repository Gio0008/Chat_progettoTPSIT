package volterra.informatica.quintab;

//RSAUtil.java
import java.security.*;
import javax.crypto.Cipher;
import java.util.Base64;

public class RSAUtil {

    public static KeyPair generateKeyPair() throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048); // 2048-bit

        return generator.generateKeyPair();
    }

    public static String encrypt(String message, PublicKey pubKey) throws Exception {

        Cipher encryptCipher = Cipher.getInstance("RSA");

        encryptCipher.init(Cipher.ENCRYPT_MODE, pubKey);

        byte[] encrypted = encryptCipher.doFinal(message.getBytes());

        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedMessage, PrivateKey privKey) throws Exception {

        byte[] bytes = Base64.getDecoder().decode(encryptedMessage);

        Cipher decryptCipher = Cipher.getInstance("RSA");

        decryptCipher.init(Cipher.DECRYPT_MODE, privKey);
        
        return new String(decryptCipher.doFinal(bytes));
    }
}
