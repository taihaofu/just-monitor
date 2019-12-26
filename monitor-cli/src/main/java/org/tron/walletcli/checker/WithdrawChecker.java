package org.tron.walletcli.checker;

import java.nio.ByteBuffer;
import java.util.Set;
import org.tron.common.crypto.Hash;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.ByteUtil;
import org.tron.common.utils.DataWord;
import org.tron.sunapi.response.TransactionResponse;
import org.tron.walletcli.db.WithdrawFailedStore;
import org.tron.walletcli.db.WithdrawStore;
import org.tron.walletcli.utils.GatewayUtils;

public class WithdrawChecker extends ContractChecker {

  private WithdrawStore store;
  private WithdrawFailedStore failedStore;

  public WithdrawChecker() {
    this.store = WithdrawStore.getInstance();
    this.failedStore = WithdrawFailedStore.getInstance();
  }

  public void checkWithdraw() {
    while (true) {
      try {
        byte[] data = store.getData("next_nonce".getBytes());
        long nextNonce = ByteArray.toLong(data);
        walletApiWrapper.switch2Side();
        TransactionResponse response = walletApiWrapper
            .callConstantContractRet(sideChainGateway, "getWithdrawMsg(uint256)",
                String.valueOf(nextNonce), false, 10000000);
        if (response.result) {
          byte[] msgBytes = ByteArray.fromHexString(response.getConstantResult());
          store.putData(ByteArray.fromLong(nextNonce),
              ByteArray.fromHexString(response.getConstantResult()));

          walletApiWrapper.switch2Main();
          String argsStr =
              "\"" + GatewayUtils.getWithdrawMsgHash(msgBytes, nextNonce) + "\"," + nextNonce;
          System.out.println("check withdraw args is " + argsStr);
          response = walletApiWrapper
              .callConstantContractRet(mainChainGateway, "withdrawDone(bytes32,uint256)",
                  argsStr, false, 10000000);
          if (!response.result) {
            failedStore
                .putData(ByteArray.fromLong(nextNonce),
                    ByteArray.fromLong(System.currentTimeMillis()));
          } else {
            byte[] resp = ByteArray.fromHexString(response.getConstantResult());
            boolean result = GatewayUtils.unpackBoolean(resp);
            if (!result) {
              failedStore.putData(ByteArray.fromLong(nextNonce),
                  ByteArray.fromLong(System.currentTimeMillis()));
            }
          }

          store.putData("next_nonce".getBytes(), ByteArray.fromLong(++nextNonce));
          logger.info("next withdraw nonce is {}", nextNonce);
          Thread.sleep(1 * 1000);

        } else {
          logger.info("check withdraw sleep 5 minutes. next is {}.", nextNonce);
          Thread.sleep(5 * 60 * 1000);
        }
      } catch (Exception e) {
        try {
          Thread.sleep(5 * 1000);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
        e.printStackTrace();
      }
    }
  }

  public void checkFailedWithdraw() {
    Set<ByteBuffer> allFailed = failedStore.allKeys();
    logger.info("check fail withdraw size is {}.", allFailed.size());
    allFailed.forEach(nonceBuffer -> {
      byte[] targetNonce = nonceBuffer.array();
      byte[] msgData = store.getData(targetNonce);
      long nonce = ByteArray.toLong(targetNonce);
      walletApiWrapper.switch2Main();
      String argsStr =
          "\"" + GatewayUtils.getWithdrawMsgHash(msgData, nonce) + "\"," + nonce;
      logger.info("check failed withdraw is {} .", argsStr);
      TransactionResponse response = walletApiWrapper
          .callConstantContractRet(mainChainGateway, "withdrawDone(bytes32,uint256)",
              argsStr, false, 10000000);
      byte[] resp = ByteArray.fromHexString(response.getConstantResult());
      boolean result = GatewayUtils.unpackBoolean(resp);
      if (!result) {
        failedStore.putData(targetNonce, ByteArray.fromLong(System.currentTimeMillis()));
      } else {
        failedStore.deleteData(targetNonce);
      }
      try {
        Thread.sleep(5 * 1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    });
  }

  public void println() {
    Set<ByteBuffer> allFailed = failedStore.allKeys();
    logger.info("print fail withdraw size is {}.", allFailed.size());
    if (allFailed.isEmpty()) {
      sendAlert("All withdraw succeed.");
      return;
    }
    StringBuilder str = new StringBuilder("Failed withdraw nonces are ");
    allFailed.forEach(nonceBuffer -> {
      byte[] targetNonce = nonceBuffer.array();
      str.append(ByteArray.toLong(targetNonce)).append(" ");
    });
    str.append(".");
    System.out.println(str);
    sendAlert(str.toString());
  }

  public void checkWithdrawByNonce(long nextNonce) {
    try {
      walletApiWrapper.switch2Side();
      TransactionResponse response = walletApiWrapper
          .callConstantContractRet(sideChainGateway, "getWithdrawMsg(uint256)",
              String.valueOf(nextNonce), false, 10000000);
      if (response.result) {
        byte[] msgBytes = ByteArray.fromHexString(response.getConstantResult());

        walletApiWrapper.switch2Main();
        String argsStr =
            "\"" + GatewayUtils.getWithdrawMsgHash(msgBytes, nextNonce) + "\"," + nextNonce;
        System.out.println("check withdraw args is " + argsStr);
        response = walletApiWrapper
            .callConstantContractRet(mainChainGateway, "withdrawDone(bytes32,uint256)",
                argsStr, false, 10000000);
        if (!response.result) {
          logger.info(" withdraw error1 nonce is {}", nextNonce);
        } else {
          byte[] resp = ByteArray.fromHexString(response.getConstantResult());
          boolean result = GatewayUtils.unpackBoolean(resp);
          if (!result) {
            logger.info(" withdraw error2 nonce is {}", nextNonce);
          }
        }

        logger.info("next withdraw nonce is {}", nextNonce);
        Thread.sleep(1 * 1000);

      } else {
        logger.info("check null message next is {}.", nextNonce);
        logger.info("check withdraw sleep 5 minutes. next is {}.", nextNonce);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void checkWithdrawByMsg(String from, Long value, Long nonce) {
    try {
      byte[] fromBytes = ByteArray.fromHexString(from);
      byte[] valueBytes = new DataWord(value).getData();
      byte[] nonceBytes = new DataWord(ByteArray.fromLong(nonce)).getData();
      byte[] data = ByteUtil.merge(fromBytes, valueBytes, nonceBytes);
      walletApiWrapper.switch2Main();
      String argsStr =
          "\"" + ByteArray.toHexString(Hash.sha3(data)) + "\"," + nonce;
      System.out.println("check withdraw args is " + argsStr);
      TransactionResponse response = walletApiWrapper
          .callConstantContractRet(mainChainGateway, "withdrawDone(bytes32,uint256)",
              argsStr, false, 10000000);
      if (!response.result) {
        logger.info(" withdraw error1 nonce is {}", nonce);
      } else {
        byte[] resp = ByteArray.fromHexString(response.getConstantResult());
        boolean result = GatewayUtils.unpackBoolean(resp);
        if (!result) {
          logger.info(" withdraw error2 nonce is {}", nonce);
        }
      }

      logger.info("next withdraw nonce is {}", nonce);
      Thread.sleep(1 * 1000);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
