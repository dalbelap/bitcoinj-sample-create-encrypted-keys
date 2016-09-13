package domain.mapper;

import domain.DeterministicKeyChainEntity;

import java.util.Base64;

/**
 * Key entity DTO mapped for json conversion
 */
public class KeyMapper {

    private String pub;
    private long created;
    private EncryptedKeyMapper encrypted;
    private EncryptedKeyMapper seedEncrypted;

    public KeyMapper(DeterministicKeyChainEntity deterministicKeyChainEntity, boolean addEncryptedData){

        Base64.Encoder encoder = Base64.getEncoder();

        this.pub = encoder.encodeToString(deterministicKeyChainEntity.getPub());
        this.created = deterministicKeyChainEntity.getCreated();

        if(addEncryptedData){
            this.encrypted = new EncryptedKeyMapper(deterministicKeyChainEntity.getEncrypted());
            this.seedEncrypted = new EncryptedKeyMapper(deterministicKeyChainEntity.getSeedEncrypted());
        }

    }
}
