package utils.encrypt;

import com.google.protobuf.ByteString;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;

import java.security.SecureRandom;

/**
 * A builder to create a KeyCrypter from BitcoinJ used in AESCBCUtils.
 */
public class KeyCrypterBuilder {

    final private static SecureRandom secureRandom = new SecureRandom();

    /**
     * Return a secure random salt
     *
     * @return
     */
    public static byte[] getSalt() {
        final byte[] salt = new byte[KeyCrypterScrypt.SALT_LENGTH];
        secureRandom.nextBytes(salt);

        return salt;
    }

    /**
     * Generate a KeyCrypter
     *
     * @param salt
     * @param iterations
     * @return
     */
    public static KeyCrypter getKeyCrypter(byte[] salt, long iterations) {

        return new KeyCrypterScrypt(Protos.ScryptParameters.newBuilder().setSalt(ByteString.copyFrom(salt))
                .setN(iterations).build());
    }
}
