package domain.mapper;

import domain.WalletEntity;
import lombok.Data;

/**
 * Wallet DTO mapped for json conversion
 */
@Data
public class WalletMapper{

    private String walletName;
    private KeyMapper key;
    private KeyMapper backup;

    public WalletMapper(WalletEntity walletEntity){

        this.walletName = walletEntity.getWalletName();

        /* with encrypted data */
        this.key = new KeyMapper(walletEntity.getKey(), true);

        /* without encrypted */
        this.backup = new KeyMapper(walletEntity.getBackup(), false);
    }
}
