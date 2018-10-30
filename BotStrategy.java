// Copyright 2014 theaigames.com (developers@theaigames.com)

//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at

//        http://www.apache.org/licenses/LICENSE-2.0

//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//	
//    For the full copyright and license information, please view the LICENSE
//    file that was distributed with this source code.

package bot;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.File;

import main.Region;
import main.SuperRegion;
import move.AttackTransferMove;
import move.PlaceArmiesMove;

public class BotStrategy implements Bot
{
	class SuperRegionComparator implements Comparator<SuperRegion>
	{
		// Used for sorting in ascending order of
		// roll number
		public int compare(SuperRegion a, SuperRegion b)
		{
			return a.getArmiesReward() - b.getArmiesReward();
		}
	}
	@Override
	/**
	 * A method used at the start of the game to decide which player start with what Regions. 6 Regions are required to be returned.
	 * This example randomly picks 6 regions from the pickable starting Regions given by the engine.
	 * @return : a list of m (m=6) Regions starting with the most preferred Region and ending with the least preferred Region to start with 
	 */
	public ArrayList<Region> getPreferredStartingRegions(BotState state, Long timeOut)
	{
		int size = 6;
		ArrayList<Region> preferredStartingRegions = new ArrayList<Region>();
		ArrayList<Region> pickableregion = state.getPickableStartingRegions();
		Map<Integer, ArrayList<Region>> mapPickableRegion = new HashMap<>();
		LinkedList<SuperRegion> superRegions = state.getFullMap().superRegions;
		for(int i = 0; i < superRegions.size() ;i++)
		{
			mapPickableRegion.put(superRegions.get(i).getId(), new ArrayList<Region>());
		}
		for (Region r : pickableregion)
		{
			int key = r.getSuperRegion().getId();
			ArrayList<Region> list= mapPickableRegion.get(key);
			list.add(r);
		}
		superRegions.sort(new SuperRegionComparator());
		for (int i = 0; i < size / 2; i++) {
			ArrayList<Region> regions = mapPickableRegion.get(superRegions.get(i).getId());
			preferredStartingRegions.addAll(regions);
		}
		System.err.println("StrategyBot preferredStartingRegions: ");
		for (Region r: preferredStartingRegions)
			System.err.println(r.getId());
		return preferredStartingRegions;
	}

	@Override
	/**
	 * This method is called for at first part of each round. This example puts two armies on random regions
	 * until he has no more armies left to place.
	 * @return The list of PlaceArmiesMoves for one round
	 */
	// try to put all armies at edge, and focus on one point
	public ArrayList<PlaceArmiesMove> getPlaceArmiesMoves(BotState state, Long timeOut) 
	{
		ArrayList<PlaceArmiesMove> placeArmiesMoves = new ArrayList<PlaceArmiesMove>();
		String myName = state.getMyPlayerName();
		int armies = 2;
		int armiesLeft = state.getStartingArmies();
		LinkedList<Region> visibleRegions = state.getVisibleMap().getRegions();

		ArrayList<Region> edgeregion = FindEdgeRegions(myName, visibleRegions);
		AssignArmies(myName, armiesLeft, edgeregion, placeArmiesMoves);

		return placeArmiesMoves;
	}

	private ArrayList<Region> FindEdgeRegions(String myName, LinkedList<Region> visibleRegions)
	{
		ArrayList<Region> res = new ArrayList<Region>();

		for (Region r : visibleRegions)
		{
			if (!r.ownedByPlayer(myName))
				continue;
			// my region
			// get neighbers
			boolean bMine = true;
			LinkedList<Region> neighbers = r.getNeighbors();
			// search if there is any region which is not mine
			for (Region n: neighbers)
			{
				if (!n.ownedByPlayer(myName))
				{
					bMine = false;
					break;
				}
			}
			//
			if (!bMine)
				res.add(r);
		}

		return res;
	}

	private void AssignArmies(String myName, int nArmiesLeft, ArrayList<Region> edgeregion, ArrayList<PlaceArmiesMove> placeArmiesMoves)
	{
		int nArmies = (nArmiesLeft*8) / edgeregion.size() / 10; // assign 80% armies

		for (Region r: edgeregion)
		{
			// used all armies
			if (0 == nArmiesLeft)
				return;
			//
			if (nArmiesLeft > nArmies)
			{
				placeArmiesMoves.add(new PlaceArmiesMove(myName, r, nArmies));
				nArmiesLeft -= nArmies;
			}
			else
			{
				placeArmiesMoves.add(new PlaceArmiesMove(myName, r, nArmiesLeft));
				nArmiesLeft = 0;
			}
		}

		// assign the left 20% armies
		if (0 < nArmiesLeft)
		{
			Region r = FindImportantRegion(myName, edgeregion);
			placeArmiesMoves.add(new PlaceArmiesMove(myName, r, nArmiesLeft));
		}
	}

	// find the most important region
	private Region FindImportantRegion(String myName, ArrayList<Region> edgeregion)
	{
		int nAnimies = 0;
		Region region = null;

		for (Region r: edgeregion)
		{
			int nTmp = 0;
			LinkedList<Region> neighbers = r.getNeighbors();
			// find opponent's region count
			for (Region n: neighbers)
			{
				if (!n.ownedByPlayer(myName))
					++nTmp;
			}
			//
			if (nTmp > nAnimies)
			{
				nAnimies = nTmp;
				region = r;
			}
		}

		return region;
	}

	@Override
	/**
	 * This method is called for at the second part of each round. This example attacks if a region has
	 * more than 6 armies on it, and transfers if it has less than 6 and a neighboring owned region.
	 * @return The list of PlaceArmiesMoves for one round
	 */
	public ArrayList<AttackTransferMove> getAttackTransferMoves(BotState state, Long timeOut) 
	{
		ArrayList<AttackTransferMove> attackTransferMoves = new ArrayList<AttackTransferMove>();
		String myName = state.getMyPlayerName();
		int armies = 0;
		int MinArmies=0;

		for(Region fromRegion : state.getVisibleMap().getRegions()) {
			if (fromRegion.ownedByPlayer(myName))
			{
				// get and sort neighbors by armies
				ArrayList<Region> possibleToRegions = new ArrayList<Region>();
				possibleToRegions.addAll(fromRegion.getNeighbors());
				for (int i = 1; i < possibleToRegions.size(); i++) {
					for (int j = 0; j < possibleToRegions.size() - 1; j++) {
						Region region1 = possibleToRegions.get(j);
						Region region2 = possibleToRegions.get(j + 1);
						if (region1.getArmies() > region2.getArmies()) {
							possibleToRegions.set(j, region2);
							possibleToRegions.set(j + 1, region1);
						} else if (region1.getArmies() < region2.getArmies()) {
							possibleToRegions.set(j, region1);
							possibleToRegions.set(j + 1, region2);
						}
					}
				}
				// move
				while (!possibleToRegions.isEmpty()) {
					Region toRegion = possibleToRegions.get(0);

					if (!toRegion.getPlayerName().equals(myName) && fromRegion.getArmies()*6/10 > toRegion.getArmies()) //do an attack
					{
						armies = fromRegion.getArmies() - 1;
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, armies));
						break;
					} else if (toRegion.getPlayerName().equals(myName) && fromRegion.getArmies()*10/7 < toRegion.getArmies()) //transfer to defend
					{
						armies = fromRegion.getArmies();
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, armies));
						break;
					} else
						possibleToRegions.remove(toRegion);  
				}
			}
		}
		return attackTransferMoves;
	}

	public static void main(String[] args)
	{
		BotParser parser = new BotParser(new BotStrategy());
		parser.run();
	}
}
