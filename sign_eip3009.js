/**
 * EIP-3009 transferWithAuthorization 서명 생성 스크립트
 * Usage: node sign_eip3009.js
 */
const { ethers } = require("ethers");

// ── 설정 ──────────────────────────────────────────────
const PRIVATE_KEY  = "0xe0183ffff4ffb416f04a81f53e359373d78be5626bf103da50560cbad5d70e55";
const CHAIN_ID     = 84532n;  // Base Sepolia
const USDC_ADDRESS = "0x036CbD53842c5426634e7929541eC2318f3dCF7e";

// 커맨드라인 인수로 paymentIntentId 받기
const PAYMENT_INTENT_ID = process.argv[2] || "047ef66a-cf0c-4bdf-8400-be5a815c010a";
// ──────────────────────────────────────────────────────

const wallet = new ethers.Wallet(PRIVATE_KEY);

const domain = {
  name: "USDC",
  version: "2",
  chainId: CHAIN_ID,
  verifyingContract: USDC_ADDRESS,
};

const types = {
  TransferWithAuthorization: [
    { name: "from",        type: "address" },
    { name: "to",          type: "address" },
    { name: "value",       type: "uint256" },
    { name: "validAfter",  type: "uint256" },
    { name: "validBefore", type: "uint256" },
    { name: "nonce",       type: "bytes32" },
  ],
};

async function main() {
  const from        = wallet.address;
  const to          = wallet.address;           // 테스트: 자기 자신에게 전송
  const value       = 1000n;                    // 1000 micro-USDC (= $0.001)
  const validAfter  = 0n;
  const validBefore = BigInt(Math.floor(Date.now() / 1000) + 3600); // 1시간 유효
  const nonce       = ethers.hexlify(ethers.randomBytes(32));       // random bytes32

  const message = { from, to, value, validAfter, validBefore, nonce };

  const sig = await wallet.signTypedData(domain, types, message);
  const { v, r, s } = ethers.Signature.from(sig);

  console.log("\n=== EIP-3009 서명 완료 ===");
  console.log(`signer : ${from}`);
  console.log(`to     : ${to}`);
  console.log(`value  : ${value}`);
  console.log(`nonce  : ${nonce}`);
  console.log(`v      : ${v}`);
  console.log(`r      : ${r}`);
  console.log(`s      : ${s}`);

  const body = JSON.stringify({
    from,
    to,
    value: value.toString(),
    validAfter: validAfter.toString(),
    validBefore: validBefore.toString(),
    nonce,
    v,
    r,
    s,
  });

  console.log("\n=== Authorize curl (PowerShell) ===");
  console.log(`Invoke-RestMethod -Method POST -Uri "http://localhost:8081/x402/payment-intents/${PAYMENT_INTENT_ID}/authorize" -ContentType "application/json" -Body '${body}'`);
}

main().catch(console.error);
