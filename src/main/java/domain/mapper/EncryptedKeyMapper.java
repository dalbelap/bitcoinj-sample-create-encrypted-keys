package domain.mapper;

import lombok.ToString;
import domain.EncryptedData;

import java.util.Base64;

/**
 * Encrypted key mapped DTO for json conversion
 */
@ToString
public class EncryptedKeyMapper {

    private String iv;
    private long iter;
    private String cipher;
    private int ks;    // in bits
    private String mode;
    private int bs; // in bits
    private String salt;
    private int sl; // in bits
    private String data;

    public EncryptedKeyMapper(EncryptedData encryptedData) {

        Base64.Encoder encoder = Base64.getEncoder();

        this.iv = encoder.encodeToString(encryptedData.getIv());
        this.iter = encryptedData.getIter();
        this.cipher = encryptedData.getCipher();
        this.ks = encryptedData.getKs();
        this.mode = encryptedData.getMode();
        this.bs = encryptedData.getBs();
        this.salt = encoder.encodeToString(encryptedData.getSalt());
        this.sl = encryptedData.getSl();
        this.data = encoder.encodeToString(encryptedData.getData());
    }
}
