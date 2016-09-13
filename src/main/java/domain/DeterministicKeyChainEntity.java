package domain;

import lombok.*;

/**
 * A DeterministicKeyChain entity
 */
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class DeterministicKeyChainEntity {

    @NonNull private byte[] pub;
    @NonNull private long created;
    /* parent key */
    private EncryptedData encrypted;
    /* seed */
    private EncryptedData seedEncrypted;

}
