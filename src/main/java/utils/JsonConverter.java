package utils;

import com.google.gson.*;
import domain.DeterministicKeyChainEntity;
import domain.WalletEntity;
import domain.mapper.KeyMapper;
import domain.mapper.WalletMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Base64;

/**
 * Created by david on 5/07/15.
 */
public class JsonConverter {

    private static final Logger log = LoggerFactory.getLogger(JsonConverter.class);

    /**
     * Convert encrypted key to JSON
     * @param fileName
     * @param deterministicKeyChainEntity
     * @return
     */
    public static void toJson(String fileName, DeterministicKeyChainEntity deterministicKeyChainEntity, boolean addEncryptedData) {

        String json = new GsonBuilder().disableHtmlEscaping().create().toJson(new KeyMapper(deterministicKeyChainEntity, addEncryptedData));

        FileUtils.create(fileName, json);

        log.debug(json);
    }

    /**
     * Convert wallet info to JSON
     * @param walletEntity
     * @param fileName
     */
    public static void toJson(WalletEntity walletEntity, String fileName) {

        String json = new GsonBuilder().disableHtmlEscaping().create().toJson(new WalletMapper(walletEntity));

        FileUtils.create(fileName, json);

        log.debug(json);
    }

    public static DeterministicKeyChainEntity keyFromJson(String json){

        Gson gson = new GsonBuilder().disableHtmlEscaping().registerTypeHierarchyAdapter(byte[].class,
                new ByteArrayToBase64TypeAdapter())
                .create();

        return gson.fromJson(json, DeterministicKeyChainEntity.class);
    }

    public static WalletEntity fromJson(String json){

        Gson gson = new GsonBuilder().disableHtmlEscaping().registerTypeHierarchyAdapter(byte[].class,
                new ByteArrayToBase64TypeAdapter())
                .create();

        return gson.fromJson(json, WalletEntity.class);
    }

    /**
     * @link https://gist.github.com/orip/3635246
     * Using Android's base64 libraries. This can be replaced with any base64 library.
     */
    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

            Base64.Decoder decoder = Base64.getDecoder();

            //return decoder.decode(json.getAsString(), decoder.NO_WRAP);
            return decoder.decode(json.getAsString());
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {

            Base64.Encoder encoder = Base64.getEncoder();

            return new JsonPrimitive(encoder.encodeToString(src));
        }
    }

}
