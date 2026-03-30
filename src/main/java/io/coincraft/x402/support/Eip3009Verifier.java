package io.coincraft.x402.support;

import io.coincraft.x402.api.AuthorizePaymentRequest;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.springframework.stereotype.Component;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * EIP-3009 transferWithAuthorization 서명 검증기.
 *
 * <p>검증 흐름:
 * <ol>
 *   <li>EIP-712 domain separator 계산 (생성 시 1회 캐싱)</li>
 *   <li>TransferWithAuthorization struct hash 계산</li>
 *   <li>EIP-712 최종 digest = keccak256(0x1901 || domainSeparator || structHash)</li>
 *   <li>ecrecover(digest, v, r, s) → 복원된 서명자 주소</li>
 *   <li>복원 주소 == request.from() 검증</li>
 * </ol>
 */
@Component
public class Eip3009Verifier {

    private static final byte[] DOMAIN_TYPEHASH = keccak256(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
    );

    private static final byte[] TRANSFER_WITH_AUTH_TYPEHASH = keccak256(
            "TransferWithAuthorization(address from,address to,uint256 value,uint256 validAfter,uint256 validBefore,bytes32 nonce)"
    );

    private final byte[] domainSeparator;

    public Eip3009Verifier(Eip3009Properties props) {
        this.domainSeparator = buildDomainSeparator(props);
    }

    /**
     * 서명 검증. 실패 시 {@link X402InvalidRequestException} throw.
     */
    public void verify(AuthorizePaymentRequest req) {
        byte[] msgHash = computeDigest(req);

        byte[] r = hexToBytes(strip0x(req.r()));
        byte[] s = hexToBytes(strip0x(req.s()));
        int recId = req.v() - 27;

        if (recId < 0 || recId > 1) {
            throw new X402InvalidRequestException("SIGNATURE_INVALID_V: v must be 27 or 28");
        }

        BigInteger pubKey = Sign.recoverFromSignature(
                recId,
                new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s)),
                msgHash
        );

        if (pubKey == null) {
            throw new X402InvalidRequestException("SIGNATURE_RECOVERY_FAILED");
        }

        String recovered = Keys.getAddress(pubKey).toLowerCase();
        String expected = strip0x(req.from()).toLowerCase();

        if (!recovered.equals(expected)) {
            throw new X402InvalidRequestException("SIGNATURE_MISMATCH");
        }
    }

    /**
     * EIP-712 digest를 hex string으로 반환 (replay guard / 저장 용도).
     */
    public String digest(AuthorizePaymentRequest req) {
        return bytesToHex(computeDigest(req));
    }

    private byte[] computeDigest(AuthorizePaymentRequest req) {
        byte[] structHash = keccak256(abiEncode(
                TRANSFER_WITH_AUTH_TYPEHASH,
                padAddress(req.from()),
                padAddress(req.to()),
                padUint256(req.value()),
                padUint256(BigInteger.valueOf(req.validAfter())),
                padUint256(BigInteger.valueOf(req.validBefore())),
                bytes32(req.nonce())
        ));

        return keccak256(concat(
                new byte[]{0x19, 0x01},
                domainSeparator,
                structHash
        ));
    }

    private static byte[] buildDomainSeparator(Eip3009Properties props) {
        byte[] contractBytes = hexToBytes(strip0x(props.tokenContract()).toLowerCase());
        byte[] encoded = abiEncode(
                DOMAIN_TYPEHASH,
                keccak256(props.tokenName().getBytes(StandardCharsets.UTF_8)),
                keccak256(props.tokenVersion().getBytes(StandardCharsets.UTF_8)),
                padUint256(BigInteger.valueOf(props.chainId())),
                padAddress(contractBytes)
        );
        return keccak256(encoded);
    }

    // ── ABI encoding helpers ──────────────────────────────────────────────────

    private static byte[] abiEncode(byte[]... parts) {
        byte[] result = new byte[parts.length * 32];
        for (int i = 0; i < parts.length; i++) {
            System.arraycopy(parts[i], 0, result, i * 32, 32);
        }
        return result;
    }

    private static byte[] padUint256(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] padded = new byte[32];
        int start = (raw[0] == 0) ? 1 : 0;
        int len = raw.length - start;
        System.arraycopy(raw, start, padded, 32 - len, len);
        return padded;
    }

    /** address string (0x...) → 32-byte left-padded */
    private static byte[] padAddress(String addr) {
        return padAddress(hexToBytes(strip0x(addr).toLowerCase()));
    }

    /** address bytes (20 bytes) → 32-byte left-padded */
    private static byte[] padAddress(byte[] addrBytes) {
        byte[] padded = new byte[32];
        System.arraycopy(addrBytes, 0, padded, 32 - addrBytes.length, addrBytes.length);
        return padded;
    }

    /** bytes32 hex string → 32 bytes */
    private static byte[] bytes32(String hex) {
        byte[] b = hexToBytes(strip0x(hex));
        if (b.length != 32) {
            throw new X402InvalidRequestException("INVALID_NONCE_LENGTH: nonce must be bytes32");
        }
        return b;
    }

    // ── Keccak-256 ───────────────────────────────────────────────────────────

    private static byte[] keccak256(byte[] input) {
        KeccakDigest digest = new KeccakDigest(256);
        digest.update(input, 0, input.length);
        byte[] result = new byte[32];
        digest.doFinal(result, 0);
        return result;
    }

    private static byte[] keccak256(String input) {
        return keccak256(input.getBytes(StandardCharsets.UTF_8));
    }

    // ── Byte utilities ────────────────────────────────────────────────────────

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) len += a.length;
        byte[] result = new byte[len];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String strip0x(String hex) {
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
    }
}
