package su.hitori.ux.storage.serialize;

public record JSONCodec<E>(Encoder<E> encoder, Decoder<E> decoder) {

    public Object encode(E value) {
        return encoder.apply(value);
    }

    public E decode(Object raw) {
        return decoder.apply(raw);
    }

}
