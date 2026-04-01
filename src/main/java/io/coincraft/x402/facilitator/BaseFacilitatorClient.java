package io.coincraft.x402.facilitator;

import io.coincraft.x402.domain.authorization.PaymentAuthorization;
import io.coincraft.x402.support.Eip3009Properties;
import io.coincraft.x402.support.FacilitatorProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base 네트워크용 Facilitator 구현체.
 * EIP-3009 transferWithAuthorization을 실제 블록체인에 브로드캐스트한다.
 *
 * 활성화 조건: x402.facilitator.enabled=true
 *
 * 트랜잭션 경계 주의:
 * 블록체인 브로드캐스트 성공 후 DB 커밋 실패 시 txHash가 유실될 수 있다.
 * 프로덕션에서는 Outbox 패턴 또는 2단계 커밋 적용 필요.
 */
@Component
@ConditionalOnProperty(name = "x402.facilitator.enabled", havingValue = "true")
@Slf4j
public class BaseFacilitatorClient implements FacilitatorClient {

    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(100_000L);

    private final FacilitatorProperties facilitatorProps;
    private final Eip3009Properties eip3009Props;

    private Web3j web3j;
    private Credentials credentials;

    public BaseFacilitatorClient(FacilitatorProperties facilitatorProps, Eip3009Properties eip3009Props) {
        this.facilitatorProps = facilitatorProps;
        this.eip3009Props = eip3009Props;
    }

    @PostConstruct
    public void init() {
        this.web3j = Web3j.build(new HttpService(facilitatorProps.rpcUrl()));
        this.credentials = Credentials.create(facilitatorProps.privateKey());
        log.info("event=facilitator.initialized hotWallet={} rpcUrl={} chainId={}",
                credentials.getAddress(), facilitatorProps.rpcUrl(), eip3009Props.chainId());
    }

    @Override
    public SettleResult settle(PaymentAuthorization authorization) {
        try {
            String encodedData = encodeTransferWithAuthorization(authorization);

            BigInteger nonce = web3j
                    .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                    .send()
                    .getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    GAS_LIMIT,
                    eip3009Props.tokenContract(),
                    BigInteger.ZERO,
                    encodedData
            );

            byte[] signed = TransactionEncoder.signMessage(rawTx, eip3009Props.chainId(), credentials);

            EthSendTransaction response = web3j
                    .ethSendRawTransaction(Numeric.toHexString(signed))
                    .send();

            if (response.hasError()) {
                throw new RuntimeException("on-chain settle failed: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("event=facilitator.settle.broadcasted txHash={} authorizationId={} payer={} to={} value={}",
                    txHash, authorization.getId(), authorization.getPayer(),
                    authorization.getPayee(), authorization.getValue());

            return new SettleResult(txHash);

        } catch (Exception e) {
            log.error("event=facilitator.settle.failed authorizationId={} error={}",
                    authorization.getId(), e.getMessage());
            throw new RuntimeException("Facilitator settle failed", e);
        }
    }

    /**
     * USDC transferWithAuthorization ABI 인코딩.
     *
     * function transferWithAuthorization(
     *     address from, address to, uint256 value,
     *     uint256 validAfter, uint256 validBefore, bytes32 nonce,
     *     uint8 v, bytes32 r, bytes32 s
     * )
     */
    private String encodeTransferWithAuthorization(PaymentAuthorization auth) {
        Function function = new Function(
                "transferWithAuthorization",
                List.of(
                        new Address(auth.getPayer()),
                        new Address(auth.getPayee()),
                        new Uint256(auth.getValue()),
                        new Uint256(BigInteger.valueOf(auth.getValidAfter())),
                        new Uint256(BigInteger.valueOf(auth.getValidBefore())),
                        new Bytes32(hexToBytes32(auth.getNonce())),
                        new Uint8(BigInteger.valueOf(auth.getSigV())),
                        new Bytes32(hexToBytes32(stripHexPrefix(auth.getSigR()))),
                        new Bytes32(hexToBytes32(stripHexPrefix(auth.getSigS())))
                ),
                Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    private byte[] hexToBytes32(String hex) {
        byte[] decoded = Numeric.hexStringToByteArray(hex);
        byte[] padded = new byte[32];
        int offset = 32 - decoded.length;
        System.arraycopy(decoded, 0, padded, offset, decoded.length);
        return padded;
    }

    private String stripHexPrefix(String hex) {
        return (hex != null && (hex.startsWith("0x") || hex.startsWith("0X")))
                ? hex.substring(2)
                : hex;
    }
}
