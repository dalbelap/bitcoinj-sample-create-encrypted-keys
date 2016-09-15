import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import domain.DeterministicKeyChainEntity;
import domain.EncryptedData;
import domain.WalletEntity;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.FileUtils;
import utils.JsonConverter;
import utils.encrypt.AESCBCUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;

/**
 * A Sample to create Hierarchical Deterministic Keys
 * @link https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki
 *
 */
public class CreateHDKeys {

    private static final Logger log = LoggerFactory.getLogger(CreateHDKeys.class);

    public static final long ITERATIONS = 16384;
    public static final int ENTROPY_BITS = 256;
    public static final String WALLET_NAME = "Wallet User Name";
    public static final String USER_PASSWORD = "USER PASSWORD. Use strong passphrases!!!";
    public static final String SERVER_PASSWORD = "SERVER PASSWORD. Use strong passphrases!!!";
    public static final String WALLET_JSON = "wallet.json";

    public static void main(String[] args) throws Exception {

        /* real Bitcoin Network */
        //NetworkParameters params = MainNetParams.get();
        /* test P2P Bitcoin Network */
        //NetworkParameters params = TestNetParams.get();
        /* test LAN Bitcoin Network */
        NetworkParameters params = RegTestParams.get();

        createKeys(params, "server.json", SERVER_PASSWORD, ITERATIONS);
        DeterministicKeyChainEntity userDeterministicKeyChainEntity = createKeys(params, "user.json", USER_PASSWORD, ITERATIONS);
        DeterministicKeyChainEntity backupDeterministicKeyChainEntity = createKeys(params, "backup.json", USER_PASSWORD, ITERATIONS);

        WalletEntity walletEntity = new WalletEntity(WALLET_NAME, userDeterministicKeyChainEntity, backupDeterministicKeyChainEntity);

        JsonConverter.toJson(walletEntity, WALLET_JSON);

        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(FileUtils.readFile("backup.json", Charset.defaultCharset())).getAsJsonObject();
        System.out.println("# Backup");
        System.out.println("backupCreated="+ json.get("created").getAsInt());
        System.out.println("backupPub=" + json.get("pub").getAsString());

        json = parser.parse(FileUtils.readFile("user.json", Charset.defaultCharset())).getAsJsonObject();
        System.out.println("# User");
        System.out.println("created="+ json.get("created").getAsInt());
        System.out.println("pub=" + json.get("pub").getAsString());
        System.out.println("salt=" + json.get("seedEncrypted").getAsJsonObject().get("salt").getAsString());
        System.out.println("iv=" + json.get("seedEncrypted").getAsJsonObject().get("iv").getAsString());
        System.out.println("data=" + json.get("seedEncrypted").getAsJsonObject().get("data").getAsString());
    }

    /**
     * Create private keys and ciphered with the parameter password
     * @param params
     * @param keyFileName
     * @param password
     * @param iterations
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws MnemonicException.MnemonicLengthException
     */
    private static DeterministicKeyChainEntity createKeys(NetworkParameters params, String keyFileName, String password, long iterations) throws NoSuchAlgorithmException, IOException, MnemonicException.MnemonicLengthException {

        // TODO change iv and salt
        //LinuxSecureRandom s = new LinuxSecureRandom();
        SecureRandom secureRandom = new SecureRandom();

        // Method 1 - Calling nextBytes method to generate Random Bytes
        //byte[] bytes = new byte[512];
        //secureRandom.nextBytes(bytes);

        /* create user key */
        // NOTE: This does not work in Android. Android needs MnemonicCode.INSTANCE = new MnemonicCode();
        DeterministicKeyChain chain = new DeterministicKeyChain(secureRandom, ENTROPY_BITS, password, Calendar.getInstance().getTimeInMillis()/100);
        //DeterministicKeyChain chain = new DeterministicKeyChain(secureRandom, ENTROPY_BITS);

        /**
        * get master key from account key (first child)
         * @link https://bitcoinj.github.io/javadoc/0.13.1/org/bitcoinj/wallet/DeterministicKeyChain.html
         * A watching wallet is not instantiated using the public part of the master key as you may imagine.
         * Instead, you need to take the account key (first child of the master key) and provide the public
         * part of that to the watching wallet instead.
         * You can do this by calling getWatchingKey() and then serializing it with DeterministicKey.serializePubB58().
         * The resulting "xpub..." string encodes sufficient information about the account key to create a watching
         * chain via DeterministicKey.deserializeB58(org.bitcoinj.crypto.DeterministicKey, String)
         * (with null as the first parameter) and then DeterministicKeyChain(org.bitcoinj.crypto.DeterministicKey).
        */

        /**
        *  multi-sign needs Account Keys from HD
        * @link https://raw.githubusercontent.com/bitcoin/bips/master/bip-0032/derivation.png
        * */
        DeterministicKey key = chain.getWatchingKey();

        DeterministicKeyChainEntity deterministicKeyChainEntity =
                new DeterministicKeyChainEntity(key.serializePublic(params), key.getCreationTimeSeconds());

        /* encrypt key with custom library */
        EncryptedData encryptedData = AESCBCUtils.encrypt(key.serializePrivate(params), password, iterations);
        deterministicKeyChainEntity.setEncrypted(encryptedData);

        /* encrypt seed */
        EncryptedData seedAsEncryptedData = AESCBCUtils.encrypt(chain.getSeed().getSecretBytes(), password, iterations);
        deterministicKeyChainEntity.setSeedEncrypted(seedAsEncryptedData);

        /* store keys info with encrypted data */
        JsonConverter.toJson(keyFileName, deterministicKeyChainEntity, true);

        return deterministicKeyChainEntity;
    }
}
