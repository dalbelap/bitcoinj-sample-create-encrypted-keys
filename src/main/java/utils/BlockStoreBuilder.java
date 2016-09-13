package utils;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.store.SPVBlockStore;

import java.io.File;
import java.io.IOException;


/**
 * Created by david on 14/07/15.
 */
public class BlockStoreBuilder {
    public static SPVBlockStore createSPVStore(NetworkParameters params) throws BlockStoreException, IOException {

        //File chainFile = new File(".", "blockstore" + ".spvchain");
        File chainFile = new File("blockstore.spvchain");
        if (chainFile.exists()) {
            chainFile.delete();
        }

        // Setting up the BlochChain, the BlocksStore and connecting to the network.
        SPVBlockStore chainStore = new SPVBlockStore(params, chainFile);

        return chainStore;
    }

    public static BlockStore createMemoryStore(NetworkParameters params) {
        return new MemoryBlockStore(params);
    }

}
