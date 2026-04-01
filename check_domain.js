/**
 * USDC 컨트랙트의 실제 EIP-712 도메인 정보 조회
 */
const { ethers } = require("ethers");

const RPC_URL      = "https://sepolia.base.org";
const USDC_ADDRESS = "0x036CbD53842c5426634e7929541eC2318f3dCF7e";

const ABI = [
  "function name() view returns (string)",
  "function version() view returns (string)",
  "function DOMAIN_SEPARATOR() view returns (bytes32)",
];

async function main() {
  const provider = new ethers.JsonRpcProvider(RPC_URL);
  const usdc = new ethers.Contract(USDC_ADDRESS, ABI, provider);

  const name    = await usdc.name();
  const version = await usdc.version();
  const ds      = await usdc.DOMAIN_SEPARATOR();

  console.log("name   :", name);
  console.log("version:", version);
  console.log("DOMAIN_SEPARATOR:", ds);

  // 우리가 계산한 도메인 세퍼레이터
  const computed = ethers.TypedDataEncoder.hashDomain({
    name,
    version,
    chainId: 84532n,
    verifyingContract: USDC_ADDRESS,
  });
  console.log("computed DS     :", computed);
  console.log("match:", ds === computed);
}

main().catch(console.error);
