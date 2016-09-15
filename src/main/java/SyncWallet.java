import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A Sync sample from a wallet
 * You will need to run a Bitcoind node in regtest mode:
 *  ~$ ./bin/bitcoind -regtest -daemon
 */
public class SyncWallet {

    private static NetworkParameters params;
    private static Wallet wallet;
    private static File walletFile;
    private static Address forwardingAddress;
    private static PeerGroup peerGroup;

    public static void main(String[] args) throws UnreadableWalletException, IOException, BlockStoreException, InterruptedException, AddressFormatException {

        if(args.length != 1){
            System.out.println("usage: java SyncWallet [Bitcoin address]");
            System.exit(-1);
        }

        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.init();

        params = RegTestParams.get();
        forwardingAddress = Address.fromBase58(params, args[0]);
        File directory = new File("wallets");
        walletFile = new File(directory, CreateMarriedWallet.WALLET_FILENAME + ".wallet");
        wallet = Wallet.loadFromFile(walletFile);
        wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);

        File chainFile = new File(directory, CreateMarriedWallet.WALLET_FILENAME + ".spvchain");
        BlockStore vStore = new SPVBlockStore(params, chainFile);
        BlockChain chain = new BlockChain(params, wallet, vStore);
        peerGroup = new PeerGroup(params, chain);
        peerGroup.setUserAgent("PeerMonitor", "1.0");

        PeerAddress peerAddresses = null;
        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            final InetAddress localHost = InetAddress.getLocalHost();
            peerAddresses = new PeerAddress(localHost, params.getPort());
        }

        peerGroup.addWallet(wallet);
        if (peerAddresses != null) {
            peerGroup.addAddress(peerAddresses);
            peerGroup.setMaxConnections(1);
        } else if (params != RegTestParams.get()) {
            peerGroup.addPeerDiscovery(new DnsDiscovery(params));
        }

        peerGroup.start();
        final DownloadProgressTracker listener = new DownloadProgressTracker();
        peerGroup.startBlockChainDownload(listener);
        listener.await();

        wallet.addCoinsReceivedEventListener((wallet1, tx, prevBalance, newBalance) -> {
            // Runs in the dedicated "user thread".
            Coin value = tx.getValueSentToMe(wallet1);
            System.out.println("-----> coins resceived: " + tx.getHashAsString());
            System.out.println("received: " + tx.getValue(wallet1));

            System.out.println("Received tx for " + value.toFriendlyString() + ": " + tx);
            System.out.println("Transaction will be forwarded after it confirms.");
            // Wait until it's made it into the block chain (may run immediately if it's already there).
            //
            // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
            // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
            // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
            // case of waiting for a block.
            Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                @Override
                public void onSuccess(TransactionConfidence result) {

                    System.out.println("Confirmed: " + tx);
                }

                @Override
                public void onFailure(Throwable t) {
                    // This kind of future can't fail, just rethrow in case something weird happens.
                    throw new RuntimeException(t);
                }
            });
        });

        for(;;){
            menu();
        }
    }

    /**
     * From WalletTest
     * run test `$ mvn -Dtest=org.bitcoinj.core.WalletTest#basicSpendingFromP2SH test` in bitcoinj/core path
     * @param value
     */
    private static void forwardCoins(Coin value) {
        try {
            System.out.println("Send " + value.toFriendlyString() + " to address: "+ forwardingAddress.toString());
            // Now send the coins back! Send with a small fee attached to ensure rapid confirmation.

            // Prepare to send.
            final Coin amountToSend = value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
            final Wallet.SendResult sendResult = wallet.sendCoins(peerGroup, forwardingAddress, amountToSend);
            checkNotNull(sendResult);  // We should never try to send more coins than we have!

            // Complete the transaction successfully.
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(() -> {
                // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
            }, MoreExecutors.newDirectExecutorService());
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    public static void menu() throws IOException {

        int selection;
        Scanner input = new Scanner(System.in);

        /***************************************************/
        System.out.println("Choose from the following choices");
        System.out.println("-------------------------\n");
        System.out.println("1 - List watching addresses");
        System.out.println("2 - New address");
        System.out.println("3 - View balance");
        System.out.println("4 - List transactions");
        System.out.println("5 - Send coins");
        System.out.println("0 - Quit");

        Address address;
        selection = input.nextInt();
        switch (selection) {
            case 1:
                DeterministicKeyChain chain = wallet.getActiveKeyChain();
                System.out.println(chain.toString(true, params));
                break;
            case 2:
                // New address
                address = wallet.freshReceiveAddress();
                System.out.println("New Address: " + address);
                break;
            case 3:
                // Show balance
                System.out.println("Balance: " + wallet.getBalance());
                System.out.println("Estimated: " + wallet.getBalance(Wallet.BalanceType.ESTIMATED));
                break;
            case 4:
                for(Transaction tx : wallet.getTransactionsByTime()){
                    System.out.print(tx.toString());
                }
                break;
            case 5:
                // Perform "decrypt number" case.
                forwardCoins(Coin.valueOf(100000));
                break;
            case 0:
                wallet.saveToFile(walletFile);
                // Perform "quit" case.
                System.exit(0);
                break;
            default:
                // The user input an unexpected choice.
        }

        wallet.saveToFile(walletFile);
        input.next();
    }
}