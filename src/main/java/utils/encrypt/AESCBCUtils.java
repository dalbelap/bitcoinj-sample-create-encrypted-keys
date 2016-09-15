package utils.encrypt;

import domain.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * AES CBC encryption
 */
public class AESCBCUtils{

    private static final String CIPHER = "AES";
    private static final String BLOCK_MODE = "CBC";

    public static EncryptedData encrypt(byte[] textPlain, CharSequence password, long iter) {

        checkNotNull(textPlain);
        checkNotNull(password);

        /* random salt */
        byte[] salt = KeyCrypterBuilder.getSalt();

        /* get keyCrypter */
        KeyCrypter keyCrypter = KeyCrypterBuilder.getKeyCrypter(salt, iter);

        /* encrypt data */
        org.bitcoinj.crypto.EncryptedData data = keyCrypter.encrypt(textPlain, keyCrypter.deriveKey(password));

        //Base64.Encoder encoder = Base64.getEncoder();
        return new EncryptedData(
                data.initialisationVector,
                iter,
                CIPHER,
                KeyCrypterScrypt.KEY_LENGTH * 8,
                BLOCK_MODE,
                KeyCrypterScrypt.BLOCK_LENGTH * 8,
                salt,
                KeyCrypterScrypt.SALT_LENGTH * 8,
                data.encryptedBytes);

    }

    public static byte[] decrypt(EncryptedData encryptedData, CharSequence password) {

        checkNotNull(encryptedData.getIter());
        checkNotNull(encryptedData.getData());
        checkNotNull(encryptedData.getIter());
        checkNotNull(encryptedData.getSalt());
        checkNotNull(password);

        /* get keyCrypter */
        KeyCrypter keyCrypter = KeyCrypterBuilder.getKeyCrypter(encryptedData.getSalt(), encryptedData.getIter());

        return keyCrypter.decrypt(new org.bitcoinj.crypto.EncryptedData(encryptedData.getIv(), encryptedData.getData()),
                keyCrypter.deriveKey(password));

    }
}
