package utils;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;

/**
 * Creates a PeerGroup with BlockStore file
 */
public class PeerGroupBuilder {

    public static PeerGroup sincronize(Wallet wallet, NetworkParameters params, boolean inMemory) throws IOException, BlockStoreException {

        // Reset transactions
        wallet.clearTransactions(0);

        BlockChain chain = null;

        /* define block storage: in-memory, SPV store, postgreSQL, etc */
        if(inMemory) {
            // In memory
            BlockStore blockStore = BlockStoreBuilder.createMemoryStore(params);

            /* create blockchain instance with blockstore */
            chain = new BlockChain(params, wallet, blockStore);
        }else {
            // In file
            SPVBlockStore blockStore = BlockStoreBuilder.createSPVStore(params);

            /* create blockchain instance with blockstore */
            chain = new BlockChain(params, wallet, blockStore);
        }

        /* create peer group to download the blockchain */
        PeerGroup peerGroup = new PeerGroup(params, chain);

        /* add wallet to this peer group */
        peerGroup.addWallet(wallet);
        chain.addWallet(wallet);

        /* if regtest, connect the peer group to localhost */
        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            //peerGroup.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        // @link https://code.google.com/p/guava-libraries/wiki/Release15
        // Service guava StartAndWait is deprecated
        peerGroup.startAsync();
        peerGroup.downloadBlockChain();

        return peerGroup;
    }

}
