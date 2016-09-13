import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.testing.KeyChainTransactionSigner;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

/**
 * MarriedWallets based in WalletTest.java, encrypt and check seed,
 * load wallet again and see if public following keys changed
 */
public class CreateMarriedWallet {

    private static final Logger log = LoggerFactory.getLogger(CreateMarriedWallet.class);
    public static final String WALLET_FILENAME = "married-sample";
    private static final File FILE_WALLET = new File("wallets", WALLET_FILENAME + ".wallet");
    private static final CharSequence PASSWORD1 = "my helicopter contains eels. Use more strong passphrases!!";
    private static final SecureRandom secureRandom = new SecureRandom();
    public static final int ENTROPY_BITS = 256;

    private static Wallet wallet;
    private static NetworkParameters params;
    private static Context ctx;

    /**
     * Initial setup configuration in RegTest Bitcon Netword
     * Creates a wallet Using Context to create a
     * @throws Exception
     */
    public static void setUp() throws Exception {
        BriefLogFormatter.init();
        params = RegTestParams.get();
        Wallet.SendRequest.DEFAULT_FEE_PER_KB = Coin.ZERO;
        ctx = new Context(params);
        wallet = new Wallet(ctx);
    }

    public static void main(String[] args) throws Exception {

        /* initial setup */
        setUp();

        /* create wallet with 2 signers */
        createMarriedWallet(2, 3, true);
    }

    /**
     * Creates a married wallet with a threshold and number of keys
     *
     * @param threshold
     * @param numKeys
     * @param addSigners
     * @throws BlockStoreException
     */
    private static void createMarriedWallet(int threshold, int numKeys, boolean addSigners) throws BlockStoreException, IOException, UnreadableWalletException {
        final DeterministicKeyChain keyChainToWatch = new DeterministicKeyChain(secureRandom, 256, PASSWORD1.toString(), new Date().getTime()/100);
        DeterministicSeed seed = keyChainToWatch.getSeed();
        log.info("Seed from key chain to watch is encrpyted?: {}",  seed.isEncrypted());

        // Create with a seed
        createMarriedWalletWithSeed(seed, threshold, numKeys, addSigners);

        printSaveAndLoad();

        // Get watching key (account key), drop private and parent is necessary to build a MarriedKeyChain
        DeterministicKey watchingKey = keyChainToWatch.getWatchingKey().dropPrivateBytes().dropParent();
        createMarriedWalletWithWatchingKey(watchingKey, threshold, numKeys, addSigners);

        printSaveAndLoad();

    }

    private static void printSaveAndLoad() throws IOException, UnreadableWalletException {
        // Print info
        printInfo(wallet);

        /* save wallet */
        wallet.saveToFile(FILE_WALLET);

        // Print info
        printInfo(wallet);
    }

    /**
     * Create a Wallet with a given seed, threshold and number of keys
     *
     *
     * @return
     */
    private static void createMarriedWalletWithSeed(DeterministicSeed seed, int threshold, int numKeys, boolean addSigners) throws BlockStoreException {

        List<DeterministicKey> followingKeys = Lists.newArrayList();
        for (int i = 0; i < numKeys - 1; i++) {
            final DeterministicKeyChain keyChain = new DeterministicKeyChain(secureRandom, 256, PASSWORD1.toString(), new Date().getTime()/100);

            log.debug("New Deterministic key chain seed is encrpyted?: {}", keyChain.getSeed().isEncrypted());

            DeterministicKey partnerKey = DeterministicKey.deserializeB58(null, keyChain.getWatchingKey().serializePubB58(params), params);
            followingKeys.add(partnerKey);
            if (addSigners && i < threshold - 1)
                wallet.addTransactionSigner(new KeyChainTransactionSigner(keyChain));
        }

        MarriedKeyChain chain = MarriedKeyChain.builder()
                .seed(seed) // The KeyChain to Watch (seed with its secret content)
                .followingKeys(followingKeys) // the other keys to follow (numKeys - 1)
                .threshold(threshold) // the number of keys must sign to spend
                .build();
        wallet.addAndActivateHDChain(chain);
    }

    /**
     * Create a Wallet with a given seed, threshold and number of keys
     *
     * @return
     */
    private static void createMarriedWalletWithWatchingKey(DeterministicKey watchingKey, int threshold, int numKeys, boolean addSigners) throws BlockStoreException {

        List<DeterministicKey> followingKeys = Lists.newArrayList();
        for (int i = 0; i < numKeys - 1; i++) {
            final DeterministicKeyChain keyChain = new DeterministicKeyChain(secureRandom, 256, PASSWORD1.toString(), new Date().getTime()/100);

            log.debug("New Deterministic key chain seed is encrpyted?: {}", keyChain.getSeed().isEncrypted());

            DeterministicKey partnerKey = DeterministicKey.deserializeB58(null, keyChain.getWatchingKey().serializePubB58(params), params);
            followingKeys.add(partnerKey);
            if (addSigners && i < threshold - 1)
                wallet.addTransactionSigner(new KeyChainTransactionSigner(keyChain));
        }

        MarriedKeyChain chain = MarriedKeyChain.builder()
                // The watching key without private key and its creation time in seconds
                .watchingKey(watchingKey).seedCreationTimeSecs(watchingKey.getCreationTimeSeconds())
                .followingKeys(followingKeys) // the other keys to follow (numKeys - 1)
                .threshold(threshold) // the number of keys must sign to spend
                .build();
        wallet.addAndActivateHDChain(chain);
    }

    /**
     * Print info from a given wallet
     * about if it is a Married Wallet, is watching, is following
     * and print its chain with private keys
     * @param wallet
     */
    private static void printInfo(Wallet wallet) {
        MarriedKeyChain chain = (MarriedKeyChain) wallet.getActiveKeychain();

        log.info("Is Following: {}", chain.isFollowing());
        log.info("Is Married: {}", chain.isMarried());
        log.info("Is Watching: {}", chain.isWatching());
        log.info("Num keys: {}", chain.numKeys());
        log.info("Num keys issued: {}", chain.numLeafKeysIssued());
        log.info(chain.toString(true, params));
    }

}
