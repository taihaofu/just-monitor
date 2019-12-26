package org.tron.walletcli.checker;

import java.util.Date;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.DataWord;
import org.tron.protos.Protocol.Account;
import org.tron.sunapi.response.TransactionResponse;
import org.tron.walletcli.WalletApiWrapper;

public class JustChecker extends AbstractChecker {
  protected WalletApiWrapper walletApiWrapper = new WalletApiWrapper(true);
  public String mainContractAddress = "TWjkoz18Y48SgWoxEeGG11ezCCzee8wo1A";

  public void checkStatus() {
    try {
      walletApiWrapper.switch2Main();
      TransactionResponse response = walletApiWrapper
          .callConstantContractRet(mainContractAddress,
              "currentRoundData()","",false,10000000);
      byte[] resp = ByteArray.fromHexString(response.getConstantResult());
      long endTime = ByteArray.toLong(DataWord.getDataWord(resp, 0).getData());
      Date date = new Date(endTime*1000);
      StringBuilder alert = new StringBuilder("Just Game will ends at: ").append(date.toString() + "\n");

      long grandPrize = ByteArray.toLong(DataWord.getDataWord(resp, 32).getData());
      alert.append("grandPrize: ").append(grandPrize+"\n");

      long leaderBonus = ByteArray.toLong(DataWord.getDataWord(resp, 64).getData());
      alert.append("leaderBonus: ").append(leaderBonus+"\n");

      long ticketsBought = ByteArray.toLong(DataWord.getDataWord(resp, 96).getData());
      alert.append("ticketsBought: ").append(ticketsBought+"\n");

      long ticketsRedeemed = ByteArray.toLong(DataWord.getDataWord(resp, 128).getData());
      alert.append("ticketsRedeemed: ").append(ticketsRedeemed+"\n");

      long totalParticipants = ByteArray.toLong(DataWord.getDataWord(resp, 160).getData());
      alert.append("totalParticipants: ").append(totalParticipants+"\n");


      Account account = walletApiWrapper.getAccount(mainContractAddress).getData();
      long balance = account.getBalance();

      alert.append("current contract balance: ").append(balance+"\n");

      sendAlert(alert.toString());
    }
    catch (Exception e) {

    }
  }

}
