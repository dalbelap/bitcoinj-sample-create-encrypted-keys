package domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * A wallet entity that represents their name and two pair of keys, main and backup for the user
 */
@Data
@AllArgsConstructor
@ToString
public class WalletEntity {

    String walletName;
    private DeterministicKeyChainEntity key;
    private DeterministicKeyChainEntity backup;

}
