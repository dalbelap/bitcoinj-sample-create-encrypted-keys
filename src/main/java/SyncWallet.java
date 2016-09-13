import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicKeyChain;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Coin.CENT;

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

        // RegTest
        params = RegTestParams.get();

        // Address to send
        forwardingAddress = new Address(params, args[0]);

        File directory = new File("wallets");
        walletFile = new File(directory, CreateMarriedWallet.WALLET_FILENAME + ".wallet");

        // Load wallet
        // TODO why changes "Following chain" after Wallet.loadFromFile?
        wallet = Wallet.loadFromFile(walletFile);

        // print info
        System.out.println("STRING WITH KEYS:\n " + wallet.getActiveKeychain().toString(true, params));
        System.exit(-1);

        // Set autosave
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

        wallet.addEventListener(new AbstractWalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                // Runs in the dedicated "user thread".
                Coin value = tx.getValueSentToMe(w);
                System.out.println("-----> coins resceived: " + tx.getHashAsString());
                System.out.println("received: " + tx.getValue(w));

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
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                System.out.println("-----> confidence changed: " + tx.getHashAsString());
                TransactionConfidence confidence = tx.getConfidence();
                System.out.println("new block depth: " + confidence.getDepthInBlocks());
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                Coin value = tx.getValueSentToMe(wallet);
                System.out.println("coins sent from wallet " + wallet.getDescription() + " from value " + value.toFriendlyString());
            }

            @Override
            public void onReorganize(Wallet wallet) {
                System.out.println("Wallet " + wallet.getDescription() + " was reorganizated");
            }

            @Override
            public void onWalletChanged(Wallet wallet) {
                System.out.println("Wallet " + wallet.getDescription() + " has changed");
            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {
                System.out.println("new key added");
            }

            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                System.out.println("new script added");
            }
        });

        for(;;){ // avoids while true check

            // Show menu
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
            Wallet.SendRequest req = Wallet.SendRequest.to(forwardingAddress, value);
            req.fee = CENT;

            // Complete the transaction successfully.
            req.shuffleOutputs = false;
            wallet.completeTx(req);
            Transaction t2 = req.tx;

            // Broadcast the transaction and commit.
            final LinkedList<Transaction> txns = Lists.newLinkedList();
            wallet.addEventListener(new AbstractWalletEventListener() {
                @Override
                public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    txns.add(tx);
                }
            });
            wallet.commitTx(t2);
            Threading.waitForUserCode();

            final Wallet.SendResult sendResult = wallet.sendCoins(peerGroup, forwardingAddress, value);
            checkNotNull(sendResult);  // We should never try to send more coins than we have!
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.sameThreadExecutor());
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    public static void menu() throws IOException {

        int selection;
        Scanner input = new Scanner(System.in);

        /***************************************************/
        System.out.println("Choose from these choices");
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
                DeterministicKeyChain chain = wallet.getActiveKeychain();
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
                System.out.println("Balance estimado: " + wallet.getBalance(Wallet.BalanceType.ESTIMATED));
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