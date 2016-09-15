import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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

        if(args.length < 1 ){
            log.error("usage: "+ log.getName() + " {address}");
            System.exit(-1);
        }

        File directory = new File("wallets");
        String walletFileName = CreateMarriedWallet.WALLET_FILENAME;
        kit = new WalletAppKit(params, directory, walletFileName);
        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();

        Address sendToAddress = Address.fromBase58(params, args[0]);
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        // Wi will try to send 100 satoshis
        Coin value = Coin.valueOf(100);
        System.out.println("Forwarding " + value.toFriendlyString() + " to address " + sendToAddress.toString());

        System.out.println("Wallet is encrypted?: " + kit.wallet().isEncrypted());

        final Wallet.SendResult sendResult = kit.wallet().sendCoins(kit.peerGroup(), sendToAddress, value);
        checkNotNull(sendResult);  // We should never try to send more coins than we have!
        System.out.println("Sending ...");

        sendResult.broadcastComplete.addListener(() -> {
            // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
            System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
        }, MoreExecutors.newDirectExecutorService());


        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}

    }

}