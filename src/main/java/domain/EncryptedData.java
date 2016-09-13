package domain;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Encrypted data of the private key from a Deterministic Key Chain
 */
@Data
@RequiredArgsConstructor
@ToString
public class EncryptedData {

    @NonNull private byte[] iv;
    @NonNull private long iter;
    @NonNull private String cipher;
    @NonNull private int ks;    // in bits
    @NonNull private String mode;
    @NonNull private int bs; // in bits
    @NonNull private byte[] salt;
    @NonNull private int sl; // in bits
    @NonNull private byte[] data;

}
