import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import domain.WalletEntity;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import utils.FileUtils;
import utils.JsonConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * A Sync sample to receive from a wallet waiting to receive some bitcoins to an specific address.
 * You will need to run a Bitcoind node in regtest mode:
 *  ~$ ./bin/bitcoind -regtest -daemon
 */
public class ReceiveTransaction {

    private static WalletAppKit kit;
    private static NetworkParameters params;

    public static void main(String[] args) throws UnreadableWalletException, IOException, BlockStoreException {

        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.init();

        params = RegTestParams.get();

        // Import data:
                /* recover data from Json*/
        WalletEntity walletEntityEntity = JsonConverter.fromJson(FileUtils.
                readFile(CreateHDKeys.WALLET_JSON, StandardCharsets.UTF_8));

        Wallet wallet = Wallet.loadFromFile(new File("wallets/" + CreateMarriedWallet.WALLET_FILENAME + ".wallet"));
        MarriedKeyChain chain1 = (MarriedKeyChain) wallet.getActiveKeyChain();
        System.out.println("TO STRING WITH PRIV:\n " + chain1.toString(true, params));


        // Start up a basic app using a class that automates some boilerplate.
        kit = new WalletAppKit(params, new File("wallets"), CreateMarriedWallet.WALLET_FILENAME);

        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();

        kit.wallet().addCoinsReceivedEventListener((wallet1, tx, prevBalance, newBalance) -> {
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

        kit.wallet().addTransactionConfidenceEventListener((wallet1, tx) -> {
            System.out.println("-----> confidence changed: " + tx.getHashAsString());
            TransactionConfidence confidence = tx.getConfidence();
            System.out.println("new block depth: " + confidence.getDepthInBlocks());
        });

        // Ready to run. The kit syncs the blockchain and our wallet event listener gets notified when something happens.
        // To test everything we create and print a fresh receiving address. Send some coins to that address and see if everything works.
        Address sendToAddress = kit.wallet().freshReceiveAddress();
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Balance: " + kit.wallet().getBalance());
        System.out.println("Balance estimado: " + kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED));
        System.out.println("is multisig: " + sendToAddress.isP2SHAddress());
        System.out.println("Wallet is encrypted?: " + kit.wallet().isEncrypted());

        for(;;){
            menu();
        }
    }

    /**
     * Show a cli menu
     */
    public static void menu() {

        int selection;
        Scanner input = new Scanner(System.in);

        /***************************************************/

        System.out.println("Choose from these choices");
        System.out.println("-------------------------\n");
        System.out.println("1 - List watching addresses");
        System.out.println("2 - New address");
        System.out.println("3 - View balance");
        System.out.println("4 - Send coins");
        System.out.println("0 - Quit");

        Address address;
        selection = input.nextInt();
        switch (selection) {
            case 1:
                DeterministicKeyChain chain = kit.wallet().getActiveKeyChain();
                System.out.println("Is Following: " + chain.isFollowing());
                System.out.println("Is Married: " + chain.isMarried());
                System.out.println("Is Watching: " + chain.isWatching());
                System.out.println("Num keys: " + chain.numKeys());
                System.out.println("Num keys issued: " + chain.numLeafKeysIssued());
                System.out.println("TO STRING WITH PRIV:\n " + chain.toString(true, params));
                System.out.println("");
                address = kit.wallet().currentReceiveAddress();
                System.out.println("Address: " + address);
                System.out.println("is multisig: " + address.isP2SHAddress());
                break;
            case 2:
                // New address
                address = kit.wallet().freshReceiveAddress();
                System.out.println("New Address: acpi " + address);
                break;
            case 3:
                // Show balance
                System.out.println("Balance: " + kit.wallet().getBalance());
                System.out.println("Balance estimado: " + kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED));
                break;
            case 4:
                // Perform "decrypt number" case.
                System.out.println("Send coins not implemented");
                break;
            case 0:
                // Perform "quit" case.
                System.exit(0);
                break;
            default:
                // The user input an unexpected choice.
        }

        System.out.println("Pulse enter para continuar...");
        input.next();
    }
}