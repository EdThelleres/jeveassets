/*
 * Copyright 2009-2022 Contributors (see credits.txt)
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
package net.nikr.eve.jeveasset.io.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.nikr.eve.jeveasset.data.api.accounts.OwnerType;
import net.nikr.eve.jeveasset.data.api.my.MyAccountBalance;
import net.nikr.eve.jeveasset.data.api.my.MyAsset;
import net.nikr.eve.jeveasset.data.api.my.MyContract;
import net.nikr.eve.jeveasset.data.api.my.MyContractItem;
import net.nikr.eve.jeveasset.data.api.my.MyIndustryJob;
import net.nikr.eve.jeveasset.data.api.my.MyJournal;
import net.nikr.eve.jeveasset.data.api.my.MyMarketOrder;
import net.nikr.eve.jeveasset.data.api.my.MyTransaction;
import net.nikr.eve.jeveasset.data.api.raw.RawAccountBalance;
import net.nikr.eve.jeveasset.data.api.raw.RawAsset;
import net.nikr.eve.jeveasset.data.api.raw.RawContract;
import net.nikr.eve.jeveasset.data.api.raw.RawContractItem;
import net.nikr.eve.jeveasset.data.api.raw.RawIndustryJob;
import net.nikr.eve.jeveasset.data.api.raw.RawJournal;
import net.nikr.eve.jeveasset.data.api.raw.RawMarketOrder;
import net.nikr.eve.jeveasset.data.api.raw.RawMarketOrder.Change;
import net.nikr.eve.jeveasset.data.api.raw.RawTransaction;
import net.nikr.eve.jeveasset.data.sde.Item;

public abstract class DataConverter {

	public static List<MyAsset> assetIndustryJob(final Collection<MyIndustryJob> industryJobs, boolean includeManufacturing) {
		List<MyAsset> assets = new ArrayList<>();
		for (MyIndustryJob industryJob : industryJobs) {
			if (!industryJob.isDelivered()) {
				MyAsset asset = new MyAsset(industryJob, false);
				assets.add(asset);
				if (includeManufacturing && industryJob.isManufacturing() && industryJob.getProductTypeID() != null) {
					MyAsset product = new MyAsset(industryJob, true);
					assets.add(product);
				}
			}
		}
		return assets;
	}

	public static List<MyAsset> assetMarketOrder(final Collection<MyMarketOrder> marketOrders, boolean includeSellOrders, boolean includeBuyOrders) {
		List<MyAsset> assets = new ArrayList<>();
		for (MyMarketOrder marketOrder : marketOrders) {
			if (marketOrder.isActive() && marketOrder.getVolumeRemain() > 0
					&& ((!marketOrder.isBuyOrder() && includeSellOrders)
					|| (marketOrder.isBuyOrder() && includeBuyOrders))) {
				MyAsset asset = new MyAsset(marketOrder);
				assets.add(asset);
			}
		}
		return assets;
	}

	public static List<MyAsset> assetContracts(final Collection<MyContractItem> contractItems, final Map<Long, OwnerType> owners, boolean includeSellContracts, boolean includeBuyContracts) {
		List<MyAsset> list = new ArrayList<>();
		//Only includes issuer buying and selling items
		//TODO Could add issuer bought/sold and acceptor bought/sold items to the assets list
		for (MyContractItem contractItem : contractItems) {
			OwnerType issuer = owners.get(contractItem.getContract().getIssuerID());
			/*
			if (contractItem.getContract().isForCorp()) {
				issuer = owners.get(contractItem.getContract().getIssuerCorporationID());
			} else {
				issuer = owners.get(contractItem.getContract().getIssuerID());
			}
			 */
			if (contractItem.getContract().isOpen() //Not completed
					&& issuer != null //Owned
					&& contractItem.getContract().isItemContract() //Not courier
					&& ((contractItem.isIncluded() && includeSellContracts) //Sell
					|| (!contractItem.isIncluded() && includeBuyContracts))) { //Buy
				MyAsset asset = new MyAsset(contractItem, issuer);
				list.add(asset);
			}
		}
		return list;
	}

	public static List<MyAccountBalance> convertRawAccountBalance(List<RawAccountBalance> rawAccountBalances, OwnerType owner) {
		List<MyAccountBalance> accountBalances = new ArrayList<>();
		for (RawAccountBalance rawAccountBalance : rawAccountBalances) { //Lookup by ItemID
			accountBalances.add(toMyAccountBalance(rawAccountBalance, owner));
		}
		return accountBalances;
	}

	public static MyAccountBalance toMyAccountBalance(RawAccountBalance rawAccountBalance, OwnerType owner) {
		return new MyAccountBalance(rawAccountBalance, owner);
	}

	protected static List<MyAsset> convertRawAssets(List<RawAsset> rawAssets, OwnerType owner) {
		List<MyAsset> assets = new ArrayList<>();

		Map<Long, RawAsset> lookup = new HashMap<>();
		Map<Long, List<RawAsset>> childMap = new HashMap<>();
		List<RawAsset> root = new ArrayList<>();
		for (RawAsset rawAsset : rawAssets) { //Lookup by ItemID
			lookup.put(rawAsset.getItemID(), rawAsset);
			childMap.put(rawAsset.getItemID(), new ArrayList<>());
		}
		for (RawAsset rawAsset : rawAssets) { //Create child map
			RawAsset parent = lookup.get(rawAsset.getLocationID());
			if (parent != null) { //Is Child Asset
				childMap.get(parent.getItemID()).add(rawAsset);
			} else { //Is Root Asset
				root.add(rawAsset);
			}
		}
		for (RawAsset rawAsset : root) {
			MyAsset asset = deepAsset(rawAsset, owner, childMap, null);
			if (asset != null) {
				assets.add(asset);
			}
		}
		return assets;
	}

	private static MyAsset deepAsset(RawAsset rawAsset, OwnerType owner, Map<Long, List<RawAsset>> childMap, MyAsset parent) {
		List<MyAsset> parents = new ArrayList<>();
		if (parent != null) {
			parents.addAll(parent.getParents());
			parents.add(parent);
		}
		MyAsset asset = toMyAsset(rawAsset, owner, parents);
		if (asset != null) {
			for (RawAsset child : childMap.get(rawAsset.getItemID())) {
				MyAsset childAsset = deepAsset(child, owner, childMap, asset);
				if (childAsset != null) {
					asset.addAsset(childAsset);
				}
			}
		}
		return asset;
	}

	public static MyAsset toMyAsset(RawAsset rawAsset, OwnerType owner, List<MyAsset> parents) {
		Item item = ApiIdConverter.getItemUpdate(rawAsset.getTypeID());
		if (!parents.isEmpty()) { //Update locationID from ItemID to locationID
			MyAsset rootAsset = parents.get(0);
			rawAsset.setLocationID(rootAsset.getLocationID());
		}
		//Ignore some stuff
		if (ignoreAsset(rawAsset, owner)) {
			return null;
		}
		return new MyAsset(rawAsset, item, owner, parents);
	}

	public static boolean ignoreAsset(RawAsset rawAsset, OwnerType owner) {
		return rawAsset.getItemFlag().getFlagID() == 7 //Skill
				|| rawAsset.getItemFlag().getFlagID() == 61 //Skill In Training
				|| rawAsset.getItemFlag().getFlagID() == 88 //Booster
				|| rawAsset.getItemFlag().getFlagID() == 89 //Implant
				|| rawAsset.getLocationID() == owner.getOwnerID(); //Other stuff
	}

	public static Map<MyContract, List<MyContractItem>> convertRawContracts(List<RawContract> rawContracts, OwnerType owner, boolean saveHistory) {
		Map<MyContract, List<MyContractItem>> contracts = new HashMap<>();
		if (saveHistory) { //Will be overwritten by new contracts
			contracts.putAll(owner.getContracts());
		}
		for (RawContract rawContract : rawContracts) {
			MyContract myContract = toMyContract(rawContract);
			List<MyContractItem> contractItems = owner.getContracts().get(myContract); //Load ContractItems
			if (contractItems == null) { //New
				contractItems = new ArrayList<>();
			} else { //Old, update contract items
				for (MyContractItem contractItem : contractItems) {
					contractItem.setContract(myContract);
				}
			}
			contracts.remove(myContract); //Remove old value (if present)
			contracts.put(myContract, contractItems);
		}
		return contracts;
	}

	public static Map<MyContract, List<MyContractItem>> convertRawContractItems(MyContract contract, List<RawContractItem> rawContractItems, OwnerType owner) {
		Map<MyContract, List<MyContractItem>> contracts = new HashMap<>(owner.getContracts()); //Copy list
		List<MyContractItem> contractItems = new ArrayList<>();
		for (RawContractItem rawContract : rawContractItems) {
			contractItems.add(toMyContractItem(rawContract, contract));
		}
		contracts.remove(contract);
		contracts.put(contract, contractItems);
		return contracts;
	}

	public static MyContract toMyContract(RawContract rawContract) {
		return new MyContract(rawContract);
	}

	public static MyContractItem toMyContractItem(RawContractItem rawContractItem, MyContract contract) {
		Item item = ApiIdConverter.getItemUpdate(rawContractItem.getTypeID());
		return new MyContractItem(rawContractItem, contract, item);
	}

	public static List<MyIndustryJob> convertRawIndustryJobs(List<RawIndustryJob> rawIndustryJobs, OwnerType owner) {
		List<MyIndustryJob> industryJobs = new ArrayList<>();
		for (RawIndustryJob rawIndustryJob : rawIndustryJobs) {
			industryJobs.add(toMyIndustryJob(rawIndustryJob, owner));
		}
		return industryJobs;
	}

	public static MyIndustryJob toMyIndustryJob(RawIndustryJob rawIndustryJob, OwnerType owner) {
		Item item = ApiIdConverter.getItemUpdate(rawIndustryJob.getBlueprintTypeID());
		Item output = ApiIdConverter.getItemUpdate(rawIndustryJob.getProductTypeID());
		return new MyIndustryJob(rawIndustryJob, item, output, owner);
	}

	public static Set<MyJournal> convertRawJournals(List<RawJournal> rawJournals, OwnerType owner, boolean saveHistory) {
		Set<MyJournal> journals = new HashSet<>();
		for (RawJournal rawJournal : rawJournals) {
			journals.add(toMyJournal(rawJournal, owner));
		}
		if (saveHistory) {
			journals.addAll(owner.getJournal());
		}
		return journals;
	}

	public static MyJournal toMyJournal(RawJournal rawJournal, OwnerType owner) {
		return new MyJournal(rawJournal, owner);
	}

	public static Set<MyMarketOrder> convertRawMarketOrders(List<RawMarketOrder> rawMarketOrders, OwnerType owner, boolean saveHistory) {
		Set<MyMarketOrder> marketOrders = new HashSet<>();
		Map<Long, Set<Change>> changed = new HashMap<>();
		for (MyMarketOrder marketOrder : owner.getMarketOrders()) {
			changed.put(marketOrder.getOrderID(), marketOrder.getChanges());
		}
		for (RawMarketOrder rawMarketOrder : rawMarketOrders) {
			MyMarketOrder marketOrder = toMyMarketOrder(rawMarketOrder, owner);
			marketOrders.add(marketOrder);
			marketOrder.addChanges(changed.get(marketOrder.getOrderID()));
		}
		if (saveHistory) {
			for (MyMarketOrder marketOrder : owner.getMarketOrders()) {
				marketOrder.close();
				marketOrders.add(marketOrder);
			}
		}
		return marketOrders;
	}

	public static MyMarketOrder toMyMarketOrder(RawMarketOrder rawMarketOrder, OwnerType owner) {
		Item item = ApiIdConverter.getItemUpdate(rawMarketOrder.getTypeID());
		return new MyMarketOrder(rawMarketOrder, item, owner);
	}

	public static Set<MyTransaction> convertRawTransactions(List<RawTransaction> rawTransactions, OwnerType owner, boolean saveHistory) {
		Set<MyTransaction> myTransactions = new HashSet<>();
		for (RawTransaction rawTransaction : rawTransactions) {
			myTransactions.add(toMyTransaction(rawTransaction, owner));
		}
		if (saveHistory) {
			myTransactions.addAll(owner.getTransactions());
		}
		return myTransactions;
	}

	public static MyTransaction toMyTransaction(RawTransaction rawTransaction, OwnerType owner) {
		Item item = ApiIdConverter.getItemUpdate(rawTransaction.getTypeID());
		return new MyTransaction(rawTransaction, item, owner);
	}
}
