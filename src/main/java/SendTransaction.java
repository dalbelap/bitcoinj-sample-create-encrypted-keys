import com.google.common.util.concurrent.MoreExecutors;
import domain.WalletEntity;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.FileUtils;
import utils.JsonConverter;
import utils.encrypt.AESCBCUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A Sync sample to send a transaction from to an specific address.
 * You will need first send some bitcoins to one address using ReceiveTransaction.java
 * You will need to run a Bitcoind node in regtest mode:
 *  ~$ ./bin/bitcoind -regtest -daemon
 */
public class SendTransaction {

    private static NetworkParameters params = RegTestParams.get();
    private static final Logger log = LoggerFactory.getLogger(SendTransaction.class);

    private static WalletAppKit kit;

    public static void main(String[] args) throws UnreadableWalletException, IOException, BlockStoreException, AddressFormatException, InsufficientMoneyException, ExecutionException, InterruptedException {

         /* recover data from Json*/
        String string = FileUtils.
                readFile(CreateHDKeys.WALLET_JSON, StandardCharsets.UTF_8);
        WalletEntity walletEntityEntity = JsonConverter.fromJson(string);

        DeterministicKey userKey = DeterministicKey.deserialize(null, AESCBCUtils.decrypt(walletEntityEntity.getKey().getEncrypted(), CreateHDKeys.USER_PASSWORD));
        DeterministicKey onlyPubKey = DeterministicKey.deserialize(null, walletEntityEntity.getKey().getPub());
        DeterministicKeyChain keyChain = new DeterministicKeyChain(onlyPubKey, walletEntityEntity.getKey().getCreated());

        if(args.length < 1 ){
            log.error("usage: "+ log.getName() + " {address}");
            System.exit(-1);
        }

        /* load wallet */
        File file = new File(CreateMarriedWallet.WALLET_FILENAME);
        Wallet myWallet = Wallet.loadFromFile(file);

        kit = new WalletAppKit(params, new File("wallets"), CreateMarriedWallet.WALLET_FILENAME);
        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();

        Address fromAddress = kit.wallet().currentReceiveKey().toAddress(params);
        Address sendToAddress = new Address(params, args[0]);
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        // 100 satoshis
        Coin value = Coin.valueOf(100);
        System.out.println("Forwarding " + value.toFriendlyString() + " to address " + sendToAddress.toString());

        // Decrypt wallet and send
        System.out.println("Wallet is encrypted?: " + kit.wallet().isEncrypted());


        final Wallet.SendResult sendResult = kit.wallet().sendCoins(kit.peerGroup(), sendToAddress, value);

        checkNotNull(sendResult);  // We should never try to send more coins than we have!
        System.out.println("Sending ...");

        sendResult.broadcastComplete.addListener(new Runnable() {
            @Override
            public void run() {
                // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
            }
        }, MoreExecutors.sameThreadExecutor());


        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}

    }

}