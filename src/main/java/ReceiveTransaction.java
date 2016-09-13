import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import domain.WalletEntity;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.testing.KeyChainTransactionSigner;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.MarriedKeyChain;
import utils.FileUtils;
import utils.JsonConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
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
        MarriedKeyChain chain1 = (MarriedKeyChain) wallet.getActiveKeychain();
        // Print info
        System.out.println("TO STRING WITH PRIV:\n " + chain1.toString(true, params));


        // Start up a basic app using a class that automates some boilerplate.
        kit = new WalletAppKit(params, new File("wallets"), CreateMarriedWallet.WALLET_FILENAME);

        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // TODO error kit.wallet() no se puede llamar hasta que se haga start()

        // Download the block chain and wait until it's done.
        kit.startAsync();
        // TODO error nullpointer exception para kit.wallet().getActiveKeychain()
        kit.awaitRunning();

        // TODO ver porque la married cambia las Following Chain si existe y después de ejecutarse el startAsync
        System.out.println("TO STRING WITH PRIV:\n " + kit.wallet().getActiveKeychain().toString(true, params));

        /* create wallet with 2 signers by default */
        //createMarriedWallet(walletEntity, kit.wallet());


        // TODO despúes de sincronizar las "Following chain" (las direcciones de user y backup) cambian!! Pero las multisig se mantienen igual...
        // Si volvemos a ejecutar: se mantienen las nuevas "Following Chain"

        kit.wallet().addEventListener(new AbstractWalletEventListener() {
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


        // To observe wallet events (like coins received) we implement a EventListener class that extends the AbstractWalletEventListener bitcoinj then calls the different functions from the EventListener class
        //WalletListener wListener = new WalletListener();
        //kit.wallet().addEventListener(wListener);

        // Ready to run. The kit syncs the blockchain and our wallet event listener gets notified when something happens.
        // To test everything we create and print a fresh receiving address. Send some coins to that address and see if everything works.
        Address sendToAddress = kit.wallet().freshReceiveAddress();
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Balance: " + kit.wallet().getBalance());
        System.out.println("Balance estimado: " + kit.wallet().getBalance(Wallet.BalanceType.ESTIMATED));
        System.out.println("is multisig: " + sendToAddress.isP2SHAddress());
        System.out.println("Wallet is encrypted?: " + kit.wallet().isEncrypted());

        for(;;){ // avoids while true check
            // Show menu
            menu();
        }
/*
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}
*/
    }

    /**
     * Create a married wallet 3-of-2 multisig
     * @param walletEntityEntity
     * @param wallet
     */
    private static void createMarriedWallet(WalletEntity walletEntityEntity, Wallet wallet) {
        final DeterministicKey partnerKey = DeterministicKey.deserialize(params, walletEntityEntity.getKey().getPub());

        // add addSigners from user key
        DeterministicKeyChain keyChain1 = new DeterministicKeyChain(partnerKey, walletEntityEntity.getKey().getCreated());
        wallet.addTransactionSigner(new KeyChainTransactionSigner(keyChain1));

        final DeterministicKey backupKey = DeterministicKey.deserialize(params, walletEntityEntity.getBackup().getPub());

        /* married wallet HDM */
        MarriedKeyChain chain = MarriedKeyChain.builder()
                .random(new SecureRandom(), CreateMarriedWallet.ENTROPY_BITS)
                .followingKeys(partnerKey, backupKey)
                .build();

        // Wallet activate married key chain
        wallet.addAndActivateHDChain(chain);
    }

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
                DeterministicKeyChain chain = kit.wallet().getActiveKeychain();
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