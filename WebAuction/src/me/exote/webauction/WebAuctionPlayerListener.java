package me.exote.webauction;


import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;


public class WebAuctionPlayerListener extends PlayerListener{
	
	public WebAuctionPlayerListener(final WebAuction plugin) {
    }
	public static double round(double unrounded, int precision, int roundingMode)
	{
	    BigDecimal bd = new BigDecimal(unrounded);
	    BigDecimal rounded = bd.setScale(precision, roundingMode);
	    return rounded.doubleValue();
	}
	@Override
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		String playerName = player.getName();
		WebAuction.playerTimer.put(player, WebAuction.getCurrentMilli());
		String queryMail = "SELECT * FROM WA_Mail WHERE name= '"+playerName+"';";
		String queryAlerts = "SELECT * FROM WA_SaleAlerts WHERE seller= '"+playerName+"' AND alerted= 'false';";
		ResultSet resultMail = null;
		ResultSet resultAlerts = null;
		try {
			resultAlerts = WebAuction.mysql.query(queryAlerts);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			if (resultAlerts != null){
				while (resultAlerts.next()){	
					int id = resultAlerts.getInt("id");
					String buyer = resultAlerts.getString("buyer");
					String item = resultAlerts.getString("item");
					int quantity = resultAlerts.getInt("quantity");
					Double priceEach = resultAlerts.getDouble("price");
					player.sendMessage(WebAuction.logPrefix + "You sold "+quantity+" "+item+" to "+buyer+" for "+priceEach+" each.");
					String updateAlerts = "UPDATE WA_SaleAlerts SET alerted = '1' WHERE id = '"+id+"';";
					WebAuction.mysql.query(updateAlerts);
				}
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		try {
			resultMail = WebAuction.mysql.query(queryMail);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			if ((resultMail != null)  && (resultMail.next())){
				player.sendMessage(WebAuction.logPrefix + "You have new mail!");	
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		String query = "SELECT * FROM WA_Players WHERE name= '"+playerName+"';";
		ResultSet result = null;
		
		try {
			result = WebAuction.mysql.query(query);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			int canBuy = 0;
			int canSell = 0;
			int isAdmin = 0;
			if (WebAuction.permission.has(player, "wa.canbuy")){
				canBuy = 1;
			}
			if (WebAuction.permission.has(player, "wa.cansell")){
				canSell = 1;
			}
			if (WebAuction.permission.has(player, "wa.webadmin")){
				isAdmin = 1;
			}
			if ((result != null)  && (result.next())){
				
				String  updatePermissions = "UPDATE WA_Players SET canBuy = '"+canBuy+"' WHERE name='"+playerName+"';";
				WebAuction.mysql.query(updatePermissions);
				updatePermissions = "UPDATE WA_Players SET canSell = '"+canSell+"' WHERE name='"+playerName+"';";
				WebAuction.mysql.query(updatePermissions);
				updatePermissions = "UPDATE WA_Players SET isAdmin = '"+isAdmin+"' WHERE name='"+playerName+"';";
				WebAuction.mysql.query(updatePermissions);
				WebAuction.log.info(WebAuction.logPrefix + "Player found, canBuy: "+canBuy+" canSell: "+canSell+" isAdmin: "+isAdmin);
				
			} else{
				WebAuction.log.info(WebAuction.logPrefix + "Player not found, creating account");
				//create that person in database
				String queryInsert = "INSERT INTO WA_Players (name, pass, money, canBuy, canSell, isAdmin) VALUES ('" + player.getName() +"', 'Password', " + 0 +", "+ canBuy +", "+ canSell +", "+ isAdmin +");";
				try {
					WebAuction.mysql.query(queryInsert);
				} catch (Exception e) {
					e.printStackTrace();
				}	
			}
		}catch (Exception e){
			e.printStackTrace();
		}
	}
	@SuppressWarnings("deprecation")
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {

		Player player = event.getPlayer();
		String playerName = player.getName();
		Block block = event.getClickedBlock();
		if (block!=null){
			int blockMat = block.getTypeId();
			
			if ((blockMat == 63)||(blockMat == 68)){
				//it's a sign
				Sign sign = (Sign)block.getState();
				String[] lines = sign.getLines();
				if (lines[0].equals("[WebAuction]")){
					event.setCancelled(true);
					if (WebAuction.playerTimer.get(player) < WebAuction.getCurrentMilli()){
						WebAuction.playerTimer.put(player, WebAuction.getCurrentMilli()+ WebAuction.signDelay);
						if (lines[1].equals("Deposit")){
							if (WebAuction.permission.has(player, "wa.use.deposit.money")){
								Double amount = 0.0;
								if (!lines[2].equals("All")){
									amount = Double.parseDouble(lines[2]);
								}
								if(WebAuction.economy.getBalance(playerName)>amount){
									try {
										String query = "SELECT * FROM WA_Players WHERE name = '"+playerName+"'";
										ResultSet result = null;
										result = WebAuction.mysql.query(query);
										if (result.next()) {
											Double currentMoney = result.getDouble("money");
											if (lines[2].equals("All")){
												amount = WebAuction.economy.getBalance(playerName);
											}
											currentMoney+=amount;
											currentMoney = round(currentMoney, 2, BigDecimal.ROUND_HALF_UP);
											player.sendMessage(WebAuction.logPrefix + "Added "+amount+" to auction account, new auction balance: "+ currentMoney);
											String queryUpdate = "UPDATE WA_Players SET money='"+currentMoney+"' WHERE name='"+playerName+"';";
											WebAuction.mysql.query(queryUpdate);
											WebAuction.economy.withdrawPlayer(playerName, amount);
										} else {
											player.sendMessage(WebAuction.logPrefix + "No WebAuction account found, try logging off and back on again");
										}
									} catch (Exception e) {
										e.printStackTrace();
									}	
								}else{
									player.sendMessage(WebAuction.logPrefix + "You do not have enough money in your pocket.");
								}
							}			
						}else if (lines[1].equals("Withdraw")){
							if (WebAuction.permission.has(player, "wa.use.withdraw.money")){
								Double amount = 0.0;
								if (!lines[2].equals("All")){
									amount = Double.parseDouble(lines[2]);
								}
								try {
									String query = "SELECT * FROM WA_Players WHERE name = '"+playerName+"'";
									ResultSet result = null;
									result = WebAuction.mysql.query(query);
									if (result.next()) {
										// Match found!
										Double currentMoney = result.getDouble("money");
										if (lines[2].equals("All")){
											amount = currentMoney;
										}
										if (currentMoney >= amount){
											currentMoney-=amount;
											currentMoney = round(currentMoney, 2, BigDecimal.ROUND_HALF_UP);
											player.sendMessage(WebAuction.logPrefix + "Removed "+amount+" from auction account, new auction balance: "+ currentMoney);
											String queryUpdate = "UPDATE WA_Players SET money='"+currentMoney+"' WHERE name='"+playerName+"';";
											WebAuction.mysql.query(queryUpdate);
											WebAuction.economy.depositPlayer(playerName,amount);
										}else{
											player.sendMessage(WebAuction.logPrefix + "You do not have enough money in your WebAuction account.");
										}
									} else {
										player.sendMessage(WebAuction.logPrefix + "No WebAuction account found, try logging off and back on again");
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							} else {
								player.sendMessage(WebAuction.logPrefix + "You do not have permission to withdraw money");
								event.setCancelled(true);
							}
						}
						else if ((lines[1].equals("MailBox"))||(lines[1].equals("Mailbox"))||(lines[1].equals("Mail Box"))){
							if ((lines[2].equals("Deposit"))&&(WebAuction.permission.has(player, "wa.use.deposit.items"))){
								ItemStack stack = player.getItemInHand();
								try {
									if (stack != null){
										String itemName = Integer.toString(stack.getTypeId());
										if (stack.getTypeId() != 0){
											String itemDamage = "";
											
											if (stack.getDurability() >= 0){
												itemDamage = Integer.toString(stack.getDurability());
											}else{
												itemDamage = "0";
											}
											Map<Enchantment, Integer> itemEnchantments = stack.getEnchantments();
											int quantityInt = stack.getAmount();
										
											String querySelect = "SELECT * FROM WA_Items WHERE player='"+playerName+"' AND name='"+itemName+"' AND damage='"+itemDamage+"';";
											ResultSet result = null;
											result = WebAuction.mysql.query(querySelect);
											Boolean foundMatch = false;
											while ((result != null)  && (result.next())){
												int itemTableIdNumber = result.getInt("id");
												ArrayList<Integer> enchantmentIds = new ArrayList<Integer>();
												ArrayList<Integer> enchantmentIdsStoredTemp = new ArrayList<Integer>();
												for (Map.Entry<Enchantment, Integer> entry : itemEnchantments.entrySet()) {
													Enchantment key = entry.getKey();
												    String enchName = key.getName();
												    //player.sendMessage(enchName);
												    int enchId = key.getId();
												    Integer level = entry.getValue();
												    int enchTableId = -1;
												    while (enchTableId == -1)
												    {
												    	querySelect = "SELECT * FROM WA_Enchantments WHERE enchId='"+enchId+"' AND level='"+level+"' AND enchName='"+enchName+"';";
												    	ResultSet result2 = null;
												    	result2 = WebAuction.mysql.query(querySelect);
												    	if ((result2 != null)  && (result2.next())){
												    		enchTableId = result2.getInt("id");
												    	} else {										    
												    		String queryInsert = "INSERT INTO WA_Enchantments (enchName, enchId, level) VALUES ('" +enchName+"', '" +enchId+"', '"+level+"');";
												    		WebAuction.mysql.query(queryInsert);
												    	}
												    }
												    enchantmentIds.add(enchTableId);
												    //player.sendMessage(enchantmentIds.size()+" part1");
												}
												Collections.sort(enchantmentIds);
												querySelect = "SELECT * FROM WA_EnchantLinks WHERE itemTableId='0' AND itemId='"+result.getInt("id")+"' ORDER BY enchId DESC;";
												ResultSet result3 = null;
												result3 = WebAuction.mysql.query(querySelect);
												while ((result3 != null)  && (result3.next())){
													enchantmentIdsStoredTemp.add(result3.getInt("enchId"));
												}
												Collections.sort(enchantmentIdsStoredTemp);
												
												
												if (enchantmentIds.equals(enchantmentIdsStoredTemp)){
													
													int currentQuantity = result.getInt("quantity");
													currentQuantity += quantityInt;
													String queryUpdate = "UPDATE WA_Items SET quantity='"+currentQuantity+"' WHERE id='"+itemTableIdNumber+"';";
													//event.getPlayer().sendMessage(queryUpdate);
													WebAuction.mysql.query(queryUpdate);
													foundMatch = true;
												}else
												if ((enchantmentIds.isEmpty())&&(enchantmentIdsStoredTemp.isEmpty())){
													
													int currentQuantity = result.getInt("quantity");
													currentQuantity += quantityInt;
													String queryUpdate = "UPDATE WA_Items SET quantity='"+currentQuantity+"' WHERE id='"+itemTableIdNumber+"';";
													//event.getPlayer().sendMessage(queryUpdate);
													WebAuction.mysql.query(queryUpdate);
													foundMatch = true;
									
												}
											}
											if (foundMatch == false){
												
												String queryInsert = "INSERT INTO WA_Items (name, damage, player, quantity) VALUES ('" +itemName+"', '" +itemDamage+"', '"+playerName+"', '"+quantityInt+"');";
												//event.getPlayer().sendMessage(queryInsert);
												WebAuction.mysql.query(queryInsert);
												querySelect = "SELECT * FROM WA_Items WHERE player='"+playerName+"' AND name='"+itemName+"' AND damage='"+itemDamage+"' ORDER BY id DESC;";
												result = null;
												int itemTableId = -1;
												result = WebAuction.mysql.query(querySelect);
												if ((result != null)  && (result.next())){
													itemTableId = result.getInt("id");
												}
												for (Map.Entry<Enchantment, Integer> entry : itemEnchantments.entrySet()) {
												    Enchantment key = entry.getKey();
												    String enchName = key.getName();
												    int enchId = key.getId();
												    Integer level = entry.getValue();
												    
												    //see if exists already
												    int enchTableId = -1;
												    while (enchTableId == -1)
												    {
												    	querySelect = "SELECT * FROM WA_Enchantments WHERE enchId='"+enchId+"' AND level='"+level+"' AND enchName='"+enchName+"';";
												    	result = null;
												    	result = WebAuction.mysql.query(querySelect);
												    	if ((result != null)  && (result.next())){
												    		enchTableId = result.getInt("id");
												    	} else {										    
												    		queryInsert = "INSERT INTO WA_Enchantments (enchName, enchId, level) VALUES ('" +enchName+"', '" +enchId+"', '"+level+"');";
												    		WebAuction.mysql.query(queryInsert);
												    	}
												    }
												    queryInsert = "INSERT INTO WA_EnchantLinks (enchId, itemTableId, itemId) VALUES ('" +enchTableId+"', '0', '"+itemTableId+"');";
												    WebAuction.mysql.query(queryInsert);
												}
											}
											player.sendMessage(WebAuction.logPrefix + "Item stack stored.");
										}
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
								player.setItemInHand(null);
								
							}else{
							if (WebAuction.permission.has(player, "wa.use.withdraw.items")){
								try {
									String query = "SELECT * FROM WA_Mail WHERE player = '"+playerName+"'";
									
									ResultSet result = null;
									boolean invFull = true;
									result = WebAuction.mysql.query(query);
									while (result.next()) {
										// Match found!
										Boolean enchanted = false;
										ArrayList<Integer> enchantments = new ArrayList<Integer>();
										ArrayList<Integer> enchLevels = new ArrayList<Integer>();
										String queryEnchLink = "SELECT * FROM WA_EnchantLinks WHERE itemId = '"+result.getInt("id")+"' AND itemTableId = '2'";
										ResultSet resultEnchLink = WebAuction.mysql.query(queryEnchLink);
										if (player.getInventory().firstEmpty() != -1){
											ItemStack stack = new ItemStack(result.getInt("name"), result.getInt("quantity"), result.getShort("damage"));
											while (resultEnchLink.next()){
												
												String queryEnch = "SELECT * FROM WA_Enchantments WHERE id = '"+resultEnchLink.getInt("enchId")+"'";
												ResultSet resultEnch = WebAuction.mysql.query(queryEnch);
												while (resultEnch.next()){
													Enchantment tempEnch = Enchantment.getById(resultEnch.getInt("enchId"));
													//player.sendMessage(tempEnch.getName()+" "+resultEnch.getInt("level"));
													if (tempEnch.canEnchantItem(stack)){
														//player.sendMessage("Enchanting");
														//stack.addEnchantment(tempEnch, resultEnch.getInt("level"));
														enchantments.add(resultEnch.getInt("enchId"));
														enchLevels.add(resultEnch.getInt("level"));
														enchanted = true;
													}
													else{
														
														//player.sendMessage("Can't enchant for some reason");
													}
													//player.sendMessage(""+stack.containsEnchantment(tempEnch));
												}
											}
											int id = result.getInt("id");
											query = "DELETE FROM WA_Mail WHERE id='"+id+"';";
											WebAuction.mysql.query(query);
											int firstEmpty = -1;
											if (enchanted == true)
											{
												firstEmpty = player.getInventory().firstEmpty();
												
											}
											player.getInventory().addItem(stack);
											if (enchanted == true)
											{
											ItemStack tempStack = player.getInventory().getItem(firstEmpty);
											Iterator<Integer> itr = enchantments.iterator();
											Iterator<Integer> itrl = enchLevels.iterator();
											while ((itr.hasNext())&&(itrl.hasNext())) {
												Enchantment tempEnch = Enchantment.getById(itr.next());
												tempStack.addEnchantment(tempEnch, itrl.next());
												
											}
											}
											player.updateInventory();
											player.sendMessage(WebAuction.logPrefix + "Mail retrieved");
											invFull = false;
										} else {
											player.sendMessage(WebAuction.logPrefix + "Inventory full, cannot get mail");
											invFull = true;
										}
										if (invFull == true){
											break;
										}
									}
									if (invFull == false){
										player.sendMessage(WebAuction.logPrefix + "No mail");
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							} else {
								player.sendMessage(WebAuction.logPrefix + "You do not have permission to use the mailbox");
								event.setCancelled(true);
							}
							}
						}
					}else{
						player.sendMessage(WebAuction.logPrefix + "Please wait a bit before using that again");
					}
				}
			}
		}
		}
	}
}
