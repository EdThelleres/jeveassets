/*
 * Copyright 2009-2016 Contributors (see credits.txt)
 *
 * This file is part of jEveAssets.
 *
 * jEveAssets is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * jEveAssets is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jEveAssets; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package net.nikr.eve.jeveasset.io.local;

import com.beimin.eveapi.model.shared.Blueprint;
import com.beimin.eveapi.model.shared.Contract;
import com.beimin.eveapi.model.shared.ContractAvailability;
import com.beimin.eveapi.model.shared.ContractItem;
import com.beimin.eveapi.model.shared.ContractStatus;
import com.beimin.eveapi.model.shared.ContractType;
import com.beimin.eveapi.model.shared.AccountBalance;
import com.beimin.eveapi.model.shared.IndustryJob;
import com.beimin.eveapi.model.shared.JournalEntry;
import com.beimin.eveapi.model.shared.KeyType;
import com.beimin.eveapi.model.shared.MarketOrder;
import com.beimin.eveapi.model.shared.WalletTransaction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.nikr.eve.jeveasset.data.ItemFlag;
import net.nikr.eve.jeveasset.data.eveapi.EveApiAccount;
import net.nikr.eve.jeveasset.data.MyAccountBalance;
import net.nikr.eve.jeveasset.data.eveapi.EveApiOwner;
import net.nikr.eve.jeveasset.data.ProfileManager;
import net.nikr.eve.jeveasset.data.Settings;
import net.nikr.eve.jeveasset.data.StaticData;
import net.nikr.eve.jeveasset.data.evekit.EveKitOwner;
import net.nikr.eve.jeveasset.data.api.OwnerType;
import net.nikr.eve.jeveasset.gui.tabs.assets.MyAsset;
import net.nikr.eve.jeveasset.gui.tabs.journal.MyJournal;
import net.nikr.eve.jeveasset.gui.tabs.transaction.MyTransaction;
import net.nikr.eve.jeveasset.io.shared.ApiConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public final class ProfileReader extends AbstractXmlReader {

	private static final Logger LOG = LoggerFactory.getLogger(ProfileReader.class);

	private ProfileReader() { }

	public static boolean load(ProfileManager profileManager, final String filename) {
		ProfileReader reader = new ProfileReader();
		return reader.read(profileManager, filename);
	}

	private boolean read(ProfileManager profileManager, final String filename) {
		try {
			Element element = getDocumentElement(filename, true);
			parseSettings(element, profileManager);
		} catch (IOException ex) {
			LOG.info("Profile not loaded");
			return false;
		} catch (XmlException ex) {
			LOG.error("Profile not loaded: (" + filename + ") " + ex.getMessage(), ex);
			return false;
		}
		LOG.info("Profile loaded");
		return true;
	}

	private void parseSettings(final Element element, ProfileManager profileManager) throws XmlException {
		if (!element.getNodeName().equals("assets")) {
			throw new XmlException("Wrong root element name.");
		}
		//Accounts
		NodeList accountNodes = element.getElementsByTagName("accounts");
		if (accountNodes.getLength() == 1) {
			Element accountsElement = (Element) accountNodes.item(0);
			parseAccounts(accountsElement, profileManager.getAccounts());
		}
		//Accounts
		NodeList eveKitOwnersNodes = element.getElementsByTagName("evekitowners");
		if (eveKitOwnersNodes.getLength() == 1) {
			Element eveKitOwnersElement = (Element) eveKitOwnersNodes.item(0);
			parseEveKitOwners(eveKitOwnersElement, profileManager.getEveKitOwners());
		}
	}

	private void parseEveKitOwners(final Element element, final List<EveKitOwner> eveKitOwners) {
		NodeList ownerNodes =  element.getElementsByTagName("evekitowner");
		for (int i = 0; i < ownerNodes.getLength(); i++) {
			Element currentNode = (Element) ownerNodes.item(i);
			int accessKey = AttributeGetters.getInt(currentNode, "accesskey");
			String accessCred = AttributeGetters.getString(currentNode, "accesscred");
			Date expire = null;
			if (AttributeGetters.haveAttribute(currentNode, "expire")) {
				expire = AttributeGetters.getDate(currentNode, "expire");
			}
			long accessmask = AttributeGetters.getLong(currentNode, "accessmask");
			boolean corporation = AttributeGetters.getBoolean(currentNode, "corporation");
			Date limit = null;
			if (AttributeGetters.haveAttribute(currentNode, "limit")) {
				limit = AttributeGetters.getDate(currentNode, "limit");
			}
			String accountName = AttributeGetters.getString(currentNode, "accountname");
			//ContID
			Long journalCID = null;
			if (AttributeGetters.haveAttribute(currentNode, "journalcid")) {
				journalCID = AttributeGetters.getLong(currentNode, "journalcid");
			}
			Long transactionsCID = null;
			if (AttributeGetters.haveAttribute(currentNode, "transactionscid")) {
				transactionsCID = AttributeGetters.getLong(currentNode, "transactionscid");
			}
			Long contractsCID = null;
			if (AttributeGetters.haveAttribute(currentNode, "contractscid")) {
				contractsCID = AttributeGetters.getLong(currentNode, "contractscid");
			}
			Long industryJobsCID = null;
			if (AttributeGetters.haveAttribute(currentNode, "industryjobscid")) {
				industryJobsCID = AttributeGetters.getLong(currentNode, "industryjobscid");
			}
			Long marketOrdersCID = null;
			if (AttributeGetters.haveAttribute(currentNode, "marketorderscid")) {
				marketOrdersCID = AttributeGetters.getLong(currentNode, "marketorderscid");
			}
			EveKitOwner owner = new EveKitOwner(accessKey, accessCred, expire, accessmask, corporation, limit, accountName);
			owner.setJournalCID(journalCID);
			owner.setTransactionsCID(transactionsCID);
			owner.setContractsCID(contractsCID);
			owner.setIndustryJobsCID(industryJobsCID);
			owner.setMarketOrdersCID(marketOrdersCID);
			parseOwnerType(currentNode, owner);
			eveKitOwners.add(owner);
		}
	}

	private void parseAccounts(final Element element, final List<EveApiAccount> accounts) {
		NodeList accountNodes = element.getElementsByTagName("account");
		for (int i = 0; i < accountNodes.getLength(); i++) {
			Element currentNode = (Element) accountNodes.item(i);
			EveApiAccount account = parseAccount(currentNode);
			parseOwners(currentNode, account);
			accounts.add(account);
		}
	}

	private EveApiAccount parseAccount(final Node node) {
		int keyID;
		if (AttributeGetters.haveAttribute(node, "keyid")) {
			keyID = AttributeGetters.getInt(node, "keyid");
		} else {
			keyID = AttributeGetters.getInt(node, "userid");
		}
		String vCode;
		if (AttributeGetters.haveAttribute(node, "vcode")) {
			vCode = AttributeGetters.getString(node, "vcode");
		} else {
			vCode = AttributeGetters.getString(node, "apikey");
		}
		Date nextUpdate = AttributeGetters.getDate(node, "charactersnextupdate");
		String name = Integer.toString(keyID);
		if (AttributeGetters.haveAttribute(node, "name")) {
			name = AttributeGetters.getString(node, "name");
		}
		long accessMask = 0;
		if (AttributeGetters.haveAttribute(node, "accessmask")) {
			accessMask = AttributeGetters.getLong(node, "accessmask");
		}
		KeyType type = null;
		if (AttributeGetters.haveAttribute(node, "type")) {
			type = KeyType.valueOf(AttributeGetters.getString(node, "type").toUpperCase());
		}
		Date expires = null;
		if (AttributeGetters.haveAttribute(node, "expires")) {
			long i = AttributeGetters.getLong(node, "expires");
			if (i != 0) {
				expires = new Date(i);
			}
		}
		boolean invalid = false;
		if (AttributeGetters.haveAttribute(node, "invalid")) {
			invalid = AttributeGetters.getBoolean(node, "invalid");
		}
		return new EveApiAccount(keyID, vCode, name, nextUpdate, accessMask, type, expires, invalid);
	}

	private void parseOwners(final Element element, final EveApiAccount account) {
		NodeList ownerNodes =  element.getElementsByTagName("human");
		for (int i = 0; i < ownerNodes.getLength(); i++) {
			Element currentNode = (Element) ownerNodes.item(i);
			EveApiOwner owner = new EveApiOwner(account);
			parseOwnerType(currentNode, owner);
			account.getOwners().add(owner);
		}
	}

	private void parseOwnerType(final Element node, OwnerType owner) {
		String ownerName = AttributeGetters.getString(node, "name");
		long ownerID = AttributeGetters.getLong(node, "id");
		Date assetsNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "assetsnextupdate")) {
			assetsNextUpdate = AttributeGetters.getDate(node, "assetsnextupdate");
		}
		Date assetsLastUpdate = null;
		if (AttributeGetters.haveAttribute(node, "assetslastupdate")) {
			assetsLastUpdate = AttributeGetters.getDate(node, "assetslastupdate");
		}
		Date balanceNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "balancenextupdate")) {
			balanceNextUpdate = AttributeGetters.getDate(node, "balancenextupdate");
		}
		Date balanceLastUpdate = null;
		if (AttributeGetters.haveAttribute(node, "balancelastupdate")) {
			balanceLastUpdate = AttributeGetters.getDate(node, "balancelastupdate");
		}
		boolean showOwner = true;
		if (AttributeGetters.haveAttribute(node, "show")) {
			showOwner = AttributeGetters.getBoolean(node, "show");
		}
		Date marketOrdersNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "marketordersnextupdate")) {
			marketOrdersNextUpdate = AttributeGetters.getDate(node, "marketordersnextupdate");
		}
		Date journalNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "journalnextupdate")) {
			journalNextUpdate = AttributeGetters.getDate(node, "journalnextupdate");
		}
		Date transactionsNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "wallettransactionsnextupdate")) {
			transactionsNextUpdate =AttributeGetters.getDate(node, "wallettransactionsnextupdate");
		}
		Date industryJobsNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "industryjobsnextupdate")) {
			industryJobsNextUpdate = AttributeGetters.getDate(node, "industryjobsnextupdate");
		}
		Date contractsNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "contractsnextupdate")) {
			contractsNextUpdate = AttributeGetters.getDate(node, "contractsnextupdate");
		}
		Date locationsNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "locationsnextupdate")) {
			locationsNextUpdate = AttributeGetters.getDate(node, "locationsnextupdate");
		}
		Date blueprintsNextUpdate = Settings.getNow();
		if (AttributeGetters.haveAttribute(node, "blueprintsnextupdate")) {
			blueprintsNextUpdate = AttributeGetters.getDate(node, "blueprintsnextupdate");
		}
		owner.setOwnerName(ownerName);
		owner.setOwnerID(ownerID);
		owner.setAssetNextUpdate(assetsNextUpdate);
		owner.setAssetLastUpdate(assetsLastUpdate);
		owner.setBalanceNextUpdate(balanceNextUpdate);
		owner.setBalanceLastUpdate(balanceLastUpdate);
		owner.setShowOwner(showOwner);
		owner.setMarketOrdersNextUpdate(marketOrdersNextUpdate);
		owner.setJournalNextUpdate(journalNextUpdate);
		owner.setTransactionsNextUpdate(transactionsNextUpdate);
		owner.setIndustryJobsNextUpdate(industryJobsNextUpdate);
		owner.setContractsNextUpdate(contractsNextUpdate);
		owner.setLocationsNextUpdate(locationsNextUpdate);
		owner.setBlueprintsNextUpdate(blueprintsNextUpdate);

		NodeList assetNodes = node.getElementsByTagName("assets");
		if (assetNodes.getLength() == 1) {
			parseAssets(assetNodes.item(0), owner, owner.getAssets(), null);
		}
		parseContracts(node, owner);
		parseBalances(node, owner);
		parseMarketOrders(node, owner);
		parseJournals(node, owner);
		parseTransactions(node, owner);
		parseIndustryJobs(node, owner);
		parseBlueprints(node, owner);
	}

	private void parseContracts(final Element element, final OwnerType owner) {
		NodeList contractsNodes = element.getElementsByTagName("contracts");
		Map<Contract, List<ContractItem>> eveContracts = new HashMap<Contract, List<ContractItem>>();
		for (int a = 0; a < contractsNodes.getLength(); a++) {
			Element contractsNode = (Element) contractsNodes.item(a);
			NodeList contractNodes = contractsNode.getElementsByTagName("contract");
			for (int b = 0; b < contractNodes.getLength(); b++) {
				Element contractNode = (Element) contractNodes.item(b);
				Contract contract = parseContract(contractNode);
				NodeList itemNodes = contractNode.getElementsByTagName("contractitem");
				List<ContractItem> contractItems = new ArrayList<ContractItem>();
				for (int c = 0; c < itemNodes.getLength(); c++) {
					Element currentNode = (Element) itemNodes.item(c);
					ContractItem contractItem = parseContractItem(currentNode);
					contractItems.add(contractItem);
				}
				eveContracts.put(contract, contractItems);
			}
		}
		owner.setContracts(ApiConverter.convertContracts(eveContracts));
	}

	private Contract parseContract(final Element element) {
		Contract contract = new Contract();
		long acceptorID = AttributeGetters.getLong(element, "acceptorid");
		long assigneeID = AttributeGetters.getLong(element, "assigneeid");
		ContractAvailability availability
				= ContractAvailability.valueOf(AttributeGetters.getString(element, "availability"));
		double buyout = AttributeGetters.getDouble(element, "buyout");
		double collateral = AttributeGetters.getDouble(element, "collateral");
		long contractID = AttributeGetters.getLong(element, "contractid");
		Date dateAccepted;
		if (AttributeGetters.haveAttribute(element, "dateaccepted")) {
			dateAccepted = AttributeGetters.getDate(element, "dateaccepted");
		} else {
			dateAccepted = null;
		}
		Date dateCompleted;
		if (AttributeGetters.haveAttribute(element, "datecompleted")) {
			dateCompleted = AttributeGetters.getDate(element, "datecompleted");
		} else {
			dateCompleted = null;
		}
		Date dateExpired = AttributeGetters.getDate(element, "dateexpired");
		Date dateIssued = AttributeGetters.getDate(element, "dateissued");
		long endStationID = AttributeGetters.getLong(element, "endstationid");
		long issuerCorpID = AttributeGetters.getLong(element, "issuercorpid");
		long issuerID = AttributeGetters.getLong(element, "issuerid");
		int numDays = AttributeGetters.getInt(element, "numdays");
		double price = AttributeGetters.getDouble(element, "price");
		double reward = AttributeGetters.getDouble(element, "reward");
		long startStationID = AttributeGetters.getLong(element, "startstationid");
		ContractStatus status = ContractStatus.valueOf(AttributeGetters.getString(element, "status"));
		String title = AttributeGetters.getString(element, "title");
		ContractType type = ContractType.valueOf(AttributeGetters.getString(element, "type"));
		double volume = AttributeGetters.getDouble(element, "volume");
		boolean forCorp = AttributeGetters.getBoolean(element, "forcorp");

		contract.setAcceptorID(acceptorID);
		contract.setAssigneeID(assigneeID);
		contract.setAvailability(availability);
		contract.setBuyout(buyout);
		contract.setCollateral(collateral);
		contract.setContractID(contractID);
		contract.setDateAccepted(dateAccepted);
		contract.setDateCompleted(dateCompleted);
		contract.setDateExpired(dateExpired);
		contract.setDateIssued(dateIssued);
		contract.setEndStationID(endStationID);
		contract.setForCorp(forCorp);
		contract.setIssuerCorpID(issuerCorpID);
		contract.setIssuerID(issuerID);
		contract.setNumDays(numDays);
		contract.setPrice(price);
		contract.setReward(reward);
		contract.setStartStationID(startStationID);
		contract.setStatus(status);
		contract.setTitle(title);
		contract.setType(type);
		contract.setVolume(volume);

		return contract;
	}

	private ContractItem parseContractItem(final Element element) {
		ContractItem contractItem = new ContractItem();
		boolean included = AttributeGetters.getBoolean(element, "included");
		long quantity = AttributeGetters.getLong(element, "quantity");
		long recordID = AttributeGetters.getLong(element, "recordid");
		boolean singleton = AttributeGetters.getBoolean(element, "singleton");
		int typeID = AttributeGetters.getInt(element, "typeid");
		Long rawQuantity = null;
		if (AttributeGetters.haveAttribute(element, "rawquantity")) {
			rawQuantity = AttributeGetters.getLong(element, "rawquantity");
		}
		contractItem.setIncluded(included);
		contractItem.setQuantity(quantity);
		contractItem.setRecordID(recordID);
		contractItem.setSingleton(singleton);
		contractItem.setTypeID(typeID);
		contractItem.setRawQuantity(rawQuantity);

		return contractItem;
	}

	private void parseBalances(final Element element, final OwnerType owner) {
		List<MyAccountBalance> accountBalances = new ArrayList<MyAccountBalance>();
		NodeList balancesNodes = element.getElementsByTagName("balances");
		for (int a = 0; a < balancesNodes.getLength(); a++) {
			Element currentBalancesNode = (Element) balancesNodes.item(a);
			NodeList balanceNodes = currentBalancesNode.getElementsByTagName("balance");
			for (int b = 0; b < balanceNodes.getLength(); b++) {
				Element currentNode = (Element) balanceNodes.item(b);
				AccountBalance accountBalance = parseBalance(currentNode);
				accountBalances.add(new MyAccountBalance(accountBalance, owner));
			}
		}
		owner.setAccountBalances(accountBalances);
	}

	private AccountBalance parseBalance(final Element element) {
		AccountBalance accountBalance = new AccountBalance();
		int accountID = AttributeGetters.getInt(element, "accountid");
		int accountKey = AttributeGetters.getInt(element, "accountkey");
		double balance = AttributeGetters.getDouble(element, "balance");
		accountBalance.setAccountID(accountID);
		accountBalance.setAccountKey(accountKey);
		accountBalance.setBalance(balance);
		return accountBalance;
	}

	private void parseMarketOrders(final Element element, final OwnerType owner) {
		NodeList marketOrdersNodes = element.getElementsByTagName("markerorders");
		List<MarketOrder> marketOrders = new ArrayList<MarketOrder>();
		for (int a = 0; a < marketOrdersNodes.getLength(); a++) {
			Element currentMarketOrdersNode = (Element) marketOrdersNodes.item(a);
			NodeList marketOrderNodes = currentMarketOrdersNode.getElementsByTagName("markerorder");
			for (int b = 0; b < marketOrderNodes.getLength(); b++) {
				Element currentNode = (Element) marketOrderNodes.item(b);
				MarketOrder apiMarketOrder = parseMarketOrder(currentNode);
				marketOrders.add(apiMarketOrder);
			}
		}
		owner.setMarketOrders(ApiConverter.convertMarketOrders(marketOrders, owner));
	}

	private MarketOrder parseMarketOrder(final Element element) {
		MarketOrder apiMarketOrder = new MarketOrder();
		long orderID = AttributeGetters.getLong(element, "orderid");
		long charID = AttributeGetters.getLong(element, "charid");
		long stationID = AttributeGetters.getLong(element, "stationid");
		int volEntered = AttributeGetters.getInt(element, "volentered");
		int volRemaining = AttributeGetters.getInt(element, "volremaining");
		int minVolume = AttributeGetters.getInt(element, "minvolume");
		int orderState = AttributeGetters.getInt(element, "orderstate");
		int typeID = AttributeGetters.getInt(element, "typeid");
		int range = AttributeGetters.getInt(element, "range");
		int accountKey = AttributeGetters.getInt(element, "accountkey");
		int duration = AttributeGetters.getInt(element, "duration");
		double escrow = AttributeGetters.getDouble(element, "escrow");
		double price = AttributeGetters.getDouble(element, "price");
		int bid = AttributeGetters.getInt(element, "bid");
		Date issued = AttributeGetters.getDate(element, "issued");
		apiMarketOrder.setOrderID(orderID);
		apiMarketOrder.setCharID(charID);
		apiMarketOrder.setStationID(stationID);
		apiMarketOrder.setVolEntered(volEntered);
		apiMarketOrder.setVolRemaining(volRemaining);
		apiMarketOrder.setMinVolume(minVolume);
		apiMarketOrder.setOrderState(orderState);
		apiMarketOrder.setTypeID(typeID);
		apiMarketOrder.setRange(range);
		apiMarketOrder.setAccountKey(accountKey);
		apiMarketOrder.setDuration(duration);
		apiMarketOrder.setEscrow(escrow);
		apiMarketOrder.setPrice(price);
		apiMarketOrder.setBid(bid);
		apiMarketOrder.setIssued(issued);
		return apiMarketOrder;
	}

	private void parseJournals(final Element element, final OwnerType owner) {
		NodeList journalsNodes = element.getElementsByTagName("journals");
		Set<MyJournal> journals = new HashSet<MyJournal>();
		for (int a = 0; a < journalsNodes.getLength(); a++) {
			Element currentAalletJournalsNode = (Element) journalsNodes.item(a);
			NodeList journalNodes = currentAalletJournalsNode.getElementsByTagName("journal");
			for (int b = 0; b < journalNodes.getLength(); b++) {
				Element currentNode = (Element) journalNodes.item(b);
				MyJournal journal = parseJournal(currentNode, owner);
				journals.add(journal);
			}
		}
		owner.setJournal(journals);
	}

	private MyJournal parseJournal(final Element element, final OwnerType owner) {
		//Base
		JournalEntry apiJournalEntry = new JournalEntry();
		double amount = AttributeGetters.getDouble(element, "amount");
		long argID1 = AttributeGetters.getLong(element, "argid1");
		String argName1 = AttributeGetters.getString(element, "argname1");
		double balance = AttributeGetters.getDouble(element, "balance");
		Date date = AttributeGetters.getDate(element, "date");
		long ownerID1 = AttributeGetters.getLong(element, "ownerid1");
		long ownerID2 = AttributeGetters.getLong(element, "ownerid2");
		String ownerName1 = AttributeGetters.getString(element, "ownername1");
		String ownerName2 = AttributeGetters.getString(element, "ownername2");
		String reason = AttributeGetters.getString(element, "reason");
		long refID = AttributeGetters.getLong(element, "refid");
		int refTypeId = AttributeGetters.getInt(element, "reftypeid");
		Double taxAmount = null;
		if (AttributeGetters.haveAttribute(element, "taxamount")) {
			taxAmount = AttributeGetters.getDouble(element, "taxamount");
		}
		Long taxReceiverID = null;
		if (AttributeGetters.haveAttribute(element, "taxreceiverid")) {
			taxReceiverID = AttributeGetters.getLong(element, "taxreceiverid");
		}
		//New
		int owner1TypeID = 0;
		if (AttributeGetters.haveAttribute(element, "owner1typeid")) {
			owner1TypeID = AttributeGetters.getInt(element, "owner1typeid");
		}
		int owner2TypeID = 0;
		if (AttributeGetters.haveAttribute(element, "owner2typeid")) {
			owner2TypeID = AttributeGetters.getInt(element, "owner2typeid");
		}
		//Extra
		int accountKey = AttributeGetters.getInt(element, "accountkey");

		apiJournalEntry.setAmount(amount);
		apiJournalEntry.setArgID1(argID1);
		apiJournalEntry.setArgName1(argName1);
		apiJournalEntry.setBalance(balance);
		apiJournalEntry.setDate(date);
		apiJournalEntry.setOwnerID1(ownerID1);
		apiJournalEntry.setOwnerID2(ownerID2);
		apiJournalEntry.setOwnerName1(ownerName1);
		apiJournalEntry.setOwnerName2(ownerName2);
		apiJournalEntry.setReason(reason);
		apiJournalEntry.setRefID(refID);
		apiJournalEntry.setRefTypeID(refTypeId);
		apiJournalEntry.setTaxAmount(taxAmount);
		apiJournalEntry.setTaxReceiverID(taxReceiverID);
		return ApiConverter.convertJournal(apiJournalEntry, owner, accountKey);
	}

	private void parseTransactions(final Element element, final OwnerType owner) {
		NodeList transactionsNodes = element.getElementsByTagName("wallettransactions");
		Set<MyTransaction> transactions = new HashSet<MyTransaction>();
		for (int a = 0; a < transactionsNodes.getLength(); a++) {
			Element currentTransactionsNode = (Element) transactionsNodes.item(a);
			NodeList transactionNodes = currentTransactionsNode.getElementsByTagName("wallettransaction");
			for (int b = 0; b < transactionNodes.getLength(); b++) {
				Element currentNode = (Element) transactionNodes.item(b);
				MyTransaction transaction = parseTransaction(currentNode, owner);
				transactions.add(transaction);
			}
		}
		owner.setTransactions(transactions);
	}

	private MyTransaction parseTransaction(final Element element, final OwnerType owner) {
		WalletTransaction apiTransaction = new WalletTransaction();
		Date transactionDateTime = AttributeGetters.getDate(element, "transactiondatetime");
		Long transactionID = AttributeGetters.getLong(element, "transactionid");
		int quantity = AttributeGetters.getInt(element, "quantity");
		String typeName = AttributeGetters.getString(element, "typename");
		int typeID = AttributeGetters.getInt(element, "typeid");
		Double price = AttributeGetters.getDouble(element, "price");
		Long clientID = AttributeGetters.getLong(element, "clientid");
		String clientName = AttributeGetters.getString(element, "clientname");
		Long characterID = null;
		if (AttributeGetters.haveAttribute(element, "characterid")) {
			characterID = AttributeGetters.getLong(element, "characterid");
		}
		String characterName = null;
		if (AttributeGetters.haveAttribute(element, "charactername")) {
			characterName = AttributeGetters.getString(element, "charactername");
		}
		long stationID = AttributeGetters.getLong(element, "stationid");
		String stationName = AttributeGetters.getString(element, "stationname");
		String transactionType = AttributeGetters.getString(element, "transactiontype");
		String transactionFor = AttributeGetters.getString(element, "transactionfor");

		//New
		long journalTransactionID = 0;
		if (AttributeGetters.haveAttribute(element, "journaltransactionid")) {
			journalTransactionID = AttributeGetters.getLong(element, "journaltransactionid");
		}
		int clientTypeID = 0;
		if (AttributeGetters.haveAttribute(element, "clienttypeid")) {
			clientTypeID = AttributeGetters.getInt(element, "clienttypeid");
		}
		//Extra
		int accountKey = 1000;
		if (AttributeGetters.haveAttribute(element, "accountkey")) {
			accountKey = AttributeGetters.getInt(element, "accountkey");
		}
		
		apiTransaction.setTransactionDateTime(transactionDateTime);
		apiTransaction.setTransactionID(transactionID);
		apiTransaction.setQuantity(quantity);
		apiTransaction.setTypeName(typeName);
		apiTransaction.setTypeID(typeID);
		apiTransaction.setPrice(price);
		apiTransaction.setClientID(clientID);
		apiTransaction.setClientName(clientName);
		apiTransaction.setCharacterID(characterID);
		apiTransaction.setCharacterName(characterName);
		apiTransaction.setStationID(stationID);
		apiTransaction.setStationName(stationName);
		apiTransaction.setTransactionType(transactionType);
		apiTransaction.setTransactionFor(transactionFor);
		apiTransaction.setTransactionID(journalTransactionID);
		apiTransaction.setClientID(clientTypeID);
		return ApiConverter.convertTransaction(apiTransaction, owner, accountKey);
	}

	private void parseIndustryJobs(final Element element, final OwnerType owner) {
		NodeList industryJobsNodes = element.getElementsByTagName("industryjobs");
		List<IndustryJob> industryJobs = new ArrayList<IndustryJob>();
		for (int a = 0; a < industryJobsNodes.getLength(); a++) {
			Element currentIndustryJobsNode = (Element) industryJobsNodes.item(a);
			NodeList industryJobNodes = currentIndustryJobsNode.getElementsByTagName("industryjob");
			for (int b = 0; b < industryJobNodes.getLength(); b++) {
				Element currentNode = (Element) industryJobNodes.item(b);
				if (AttributeGetters.haveAttribute(currentNode, "blueprintid")) {
					IndustryJob apiIndustryJob = parseIndustryJob(currentNode);
					industryJobs.add(apiIndustryJob);
				}
			}
		}
		owner.setIndustryJobs(ApiConverter.convertIndustryJobs(industryJobs, owner));
	}

	private IndustryJob parseIndustryJob(final Element element) {
		IndustryJob apiIndustryJob = new IndustryJob();
		long jobID = AttributeGetters.getLong(element, "jobid");
		long installerID = AttributeGetters.getLong(element, "installerid");
		String installerName = AttributeGetters.getString(element, "installername");
		long facilityID = AttributeGetters.getLong(element, "facilityid");
		long solarSystemID = AttributeGetters.getLong(element, "solarsystemid");
		String solarSystemName = AttributeGetters.getString(element, "solarsystemname");
		long stationID = AttributeGetters.getLong(element, "stationid");
		int activityID = AttributeGetters.getInt(element, "activityid");
		long blueprintID = AttributeGetters.getLong(element, "blueprintid");
		int blueprintTypeID = AttributeGetters.getInt(element, "blueprinttypeid");
		String blueprintTypeName = AttributeGetters.getString(element, "blueprinttypename");
		long blueprintLocationID = AttributeGetters.getLong(element, "blueprintlocationid");
		long outputLocationID = AttributeGetters.getLong(element, "outputlocationid");
		int runs = AttributeGetters.getInt(element, "runs");
		double cost = AttributeGetters.getDouble(element, "cost");
		long teamID = AttributeGetters.getLong(element, "teamid");
		int licensedRuns = AttributeGetters.getInt(element, "licensedruns");
		double probability = AttributeGetters.getDouble(element, "probability");
		int productTypeID = AttributeGetters.getInt(element, "producttypeid");
		String productTypeName = AttributeGetters.getString(element, "producttypename");
		int status = AttributeGetters.getInt(element, "status");
		int timeInSeconds = AttributeGetters.getInt(element, "timeinseconds");
		Date startDate = AttributeGetters.getDate(element, "startdate");
		Date endDate = AttributeGetters.getDate(element, "enddate");
		Date pauseDate = AttributeGetters.getDate(element, "pausedate");
		Date completedDate = AttributeGetters.getDate(element, "completeddate");
		long completedCharacterID = AttributeGetters.getLong(element, "completedcharacterid");

		apiIndustryJob.setJobID(jobID);
		apiIndustryJob.setInstallerID(installerID);
		apiIndustryJob.setInstallerName(installerName);
		apiIndustryJob.setFacilityID(facilityID);
		apiIndustryJob.setSolarSystemID(solarSystemID);
		apiIndustryJob.setSolarSystemName(solarSystemName);
		apiIndustryJob.setStationID(stationID);
		apiIndustryJob.setActivityID(activityID);
		apiIndustryJob.setBlueprintID(blueprintID);
		apiIndustryJob.setBlueprintTypeID(blueprintTypeID);
		apiIndustryJob.setBlueprintTypeName(blueprintTypeName);
		apiIndustryJob.setBlueprintLocationID(blueprintLocationID);
		apiIndustryJob.setOutputLocationID(outputLocationID);
		apiIndustryJob.setRuns(runs);
		apiIndustryJob.setCost(cost);
		apiIndustryJob.setTeamID(teamID);
		apiIndustryJob.setLicensedRuns(licensedRuns);
		apiIndustryJob.setProbability(probability);
		apiIndustryJob.setProductTypeID(productTypeID);
		apiIndustryJob.setProductTypeName(productTypeName);
		apiIndustryJob.setStatus(status);
		apiIndustryJob.setTimeInSeconds(timeInSeconds);
		apiIndustryJob.setStartDate(startDate);
		apiIndustryJob.setEndDate(endDate);
		apiIndustryJob.setPauseDate(pauseDate);
		apiIndustryJob.setCompletedDate(completedDate);
		apiIndustryJob.setCompletedCharacterID(completedCharacterID);

		return apiIndustryJob;
	}

	private void parseAssets(final Node node, final OwnerType owner, final List<MyAsset> assets, final MyAsset parentAsset) {
		NodeList assetsNodes = node.getChildNodes();
		for (int i = 0; i < assetsNodes.getLength(); i++) {
			Node currentNode = assetsNodes.item(i);
			if (currentNode.getNodeName().equals("asset")) {
				MyAsset asset = parseAsset(currentNode, owner, parentAsset);
				if (parentAsset == null) {
					assets.add(asset);
				} else {
					parentAsset.addAsset(asset);
				}
				parseAssets(currentNode, owner, assets, asset);
			}
		}
	}

	private MyAsset parseAsset(final Node node, final OwnerType owner, final MyAsset parentAsset) {
		long count = AttributeGetters.getLong(node, "count");

		long itemId = AttributeGetters.getLong(node, "id");
		int typeID = AttributeGetters.getInt(node, "typeid");
		long locationID = AttributeGetters.getLong(node, "locationid");
		if (locationID == 0 && parentAsset != null) {
			locationID = parentAsset.getLocation().getLocationID();
		}
		boolean singleton = AttributeGetters.getBoolean(node, "singleton");
		int rawQuantity = 0;
		if (AttributeGetters.haveAttribute(node, "rawquantity")) {
			rawQuantity = AttributeGetters.getInt(node, "rawquantity");
		}
		int flagID = 0;
		if (AttributeGetters.haveAttribute(node, "flagid")) {
			flagID = AttributeGetters.getInt(node, "flagid");
		} else { //Workaround for the old system
			String flag = AttributeGetters.getString(node, "flag");
			for (ItemFlag itemFlag : StaticData.get().getItemFlags().values()) {
				if (flag.equals(itemFlag.getFlagName())) {
					flagID = itemFlag.getFlagID();
					break;
				}
			}
		}
		return ApiConverter.createAsset(parentAsset, owner, count, flagID, itemId, typeID, locationID, singleton, rawQuantity, null);
	}

	private void parseBlueprints(final Element element, final OwnerType owners) {
		Map<Long, Blueprint> blueprints = new HashMap<Long, Blueprint>();
		NodeList blueprintsNodes = element.getElementsByTagName("blueprints");
		for (int a = 0; a < blueprintsNodes.getLength(); a++) {
			Element currentBlueprintsNode = (Element) blueprintsNodes.item(a);
			NodeList blueprintNodes = currentBlueprintsNode.getElementsByTagName("blueprint");
			for (int b = 0; b < blueprintNodes.getLength(); b++) {
				Element currentNode = (Element) blueprintNodes.item(b);
				Blueprint blueprint = parseBlueprint(currentNode);
				blueprints.put(blueprint.getItemID(), blueprint);
			}
		}
		owners.setBlueprints(blueprints);
	}

	private Blueprint parseBlueprint(final Node node) {
		Blueprint blueprint = new Blueprint();
		long itemID = AttributeGetters.getLong(node, "itemid");
		long locationID = AttributeGetters.getLong(node, "locationid");
		int typeID = AttributeGetters.getInt(node, "typeid");
		String typeName = AttributeGetters.getString(node, "typename");
		int flagID = AttributeGetters.getInt(node, "flagid");
		int quantity = AttributeGetters.getInt(node, "quantity");
		int timeEfficiency = AttributeGetters.getInt(node, "timeefficiency");
		int materialEfficiency = AttributeGetters.getInt(node, "materialefficiency");
		int runs = AttributeGetters.getInt(node, "runs");

		blueprint.setItemID(itemID);
		blueprint.setLocationID(locationID);
		blueprint.setTypeID(typeID);
		blueprint.setTypeName(typeName);
		blueprint.setFlagID(flagID);
		blueprint.setQuantity(quantity);
		blueprint.setTimeEfficiency(timeEfficiency);
		blueprint.setMaterialEfficiency(materialEfficiency);
		blueprint.setRuns(runs);

		return blueprint;
	}
}
