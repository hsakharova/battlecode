package qualifying;
import java.util.ArrayList;

import java.util.List;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;
import qualifying.RobotPlayer;

///TODO:

/**
 * Make soldiers dodge bullets
 * Improve soldiers priorities
 * Improve starting farmer / archon decisions!!!!
 * Have farmers plant more trees
 * Have archons create more farmers
 * Check if ljs avoid each other well enough
 * Make scouts flee friends first?
 * Have soldiers strafe
 * Have everyone dodge bullets???
 * Have soldiers not shoot / approach scouts???
 * Would be good to have farmers not crowd themselves if too many trees are present.
 * 
 *Farmer distress channel?
 *Robots can't get past trees channel?
 */

public strictfp class RobotPlayer {
    static RobotController rc;
    static double dirNumber = 6; //directions to face in when building 
    
    //channels -> builders = 4, heavy = 3, soldier = 2, scout = 1
      static final int builderChannel = 4;
      static final int heavyChannel = 3;
      static final int soldierChannel = 2;
      static final int scoutChannel = 1;
      static final int donationChannel = 11;
      static final float farmerBulletLimit = 140;
      static final float maxBullets = farmerBulletLimit + 25;
      
      
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc; 
        
        
        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case TANK:
            	runTank();
            	break;
            case SCOUT:
            	runScout();
            	break;
        }
	}

    private static MapLocation findFarthestArchon() {
		Team myTeam = rc.getTeam();
		Team opponent = rc.getTeam().opponent();
		MapLocation[] myArchons = rc.getInitialArchonLocations(myTeam);
		
		MapLocation[] enemyArchons = rc.getInitialArchonLocations(opponent);
		MapLocation farthestArchon = myArchons[0];
		float farthestDist = 0;
		for (MapLocation archon : myArchons) {
			float worstDist = archon.distanceTo(enemyArchons[0]);
			for (MapLocation enemy : enemyArchons) {
				float dist = archon.distanceTo(enemy);
				if (dist < worstDist) {
					worstDist = dist;
				}
			}
			if (worstDist > farthestDist) {
				farthestDist = worstDist;
				farthestArchon = archon;
			}
		}
		return farthestArchon;
	}

	static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");
        Team enemy = rc.getTeam().opponent();
        MapLocation[] archons = rc.getInitialArchonLocations(rc.getTeam());
		int archonNum = archons.length;
        MapLocation startingLocation = rc.getLocation();
        boolean isFarthest = false;
        if (startingLocation.isWithinDistance(findFarthestArchon(), 1)) {
        	isFarthest = true;
        }
        boolean randomFarmerBuild = false;
        boolean turnCountLimit = false;
        Direction currentDirection = rc.getInitialArchonLocations(enemy)[0].directionTo(startingLocation);

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	
            	MapLocation myLocation = rc.getLocation();
            
            	//if there are enough bullets, build a farmer
            	float bullets = rc.getTeamBullets();
            	if ((bullets > farmerBulletLimit && (rc.getRoundNum() > 3 || isFarthest) ) || 
            			((bullets > 101) && randomFarmerBuild)) {
            		//turn until you can place a farmer
            		for (double i = 0; i<18; i++) {
            			Direction dir = new Direction( (float) (Math.PI * 2 / 18 * i));
            			if (rc.canHireGardener(dir)) {
                            rc.hireGardener(dir);
                            randomFarmerBuild = false;
                            turnCountLimit = true;
                        }
            		}
            	}
            	
            	//with a small chance, decide to build a random farmer when you can
            	//... every 400 turns?
            	//or every 150 turns if there is space
            	if (rc.getRoundNum() % 500 == 0) {randomFarmerBuild = true;}
            	if (rc.getRoundNum() > 100 && rc.getRoundNum() % (200 + archonNum * 50)  == 0) {turnCountLimit = true;}
            	TreeInfo[] nearbyTrees = rc.senseNearbyTrees(4f);
            	if (nearbyTrees.length == 0 && turnCountLimit) {
            		randomFarmerBuild = true;
            	}
            	
            	//run away from enemies
            	RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
            	if (robots.length > 0) {
            		tryMove(robots[0].getLocation().directionTo(rc.getLocation()));
            	}
            	
            	RobotInfo[] friends = rc.senseNearbyRobots(5, rc.getTeam());
            	List<RobotInfo> gardeners = new ArrayList<>();
            	for (RobotInfo friend : friends) {
            		if (friend.getType().equals(RobotType.GARDENER)) {
            			gardeners.add(friend);
            		}
            	}
            	
            	boolean shouldMove = true;
            	//move in the current direction, randomly turning some amount
            	// and trying to move away from walls and farmers
            	if (!rc.onTheMap(myLocation.add(Direction.getNorth(), 4f))) {
        			currentDirection = Direction.getSouth();
        		} else if (!rc.onTheMap(myLocation.add(Direction.getSouth(), 4f))) {
        			currentDirection = Direction.getNorth();
        		} else if (!rc.onTheMap(myLocation.add(Direction.getEast(), 4f))) {
        			currentDirection = Direction.getWest();
        		}  else if (!rc.onTheMap(myLocation.add(Direction.getWest(), 4f))) {
        			currentDirection = Direction.getEast();
        		} else if (gardeners.size() > 0) {
        			MapLocation loc = gardeners.get(0).getLocation();
        			Direction toGardener = myLocation.directionTo(loc);
        			float offset = currentDirection.degreesBetween(toGardener);
        			if (Math.abs(offset) < 90) {
        				if (offset > 0) {
        					currentDirection = currentDirection.rotateRightDegrees(70);
        				} else {
        					currentDirection = currentDirection.rotateLeftDegrees(70);
        				}
        			}
        		} else {
        			shouldMove = false;
        		}
            	
            	//randomize current Direction
            	int random = (int) ((Math.random() - 0.5) * 40.0);
            	currentDirection = currentDirection.rotateLeftDegrees(random);
            	if (shouldMove) {tryMove(currentDirection);}
            	
            	
            	
            	// Move randomly
                if (!rc.hasMoved()) {tryMove(randomDirection());}
                buyVictoryPoints();
                
            	Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");
        Team enemy = rc.getTeam().opponent();
        Team myTeam = rc.getTeam();
        MapLocation farmLocation = rc.getLocation();
        Direction toEnemy = farmLocation.directionTo(rc.getInitialArchonLocations(enemy)[0]);
        boolean farming = false;
        
        // The code you want your robot to perform every round should be in this loop
        while (true) {
        	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	MapLocation myLocation = rc.getLocation();
            	
            	//if it is the first turn, build a scout 
            	//should you move the farm to a better position first? maybe (not for now)
            	
            	//
            	if (rc.getRoundNum() < 5) {
            		for (double i = 0; i<18; i++) {
            			Direction dir = new Direction( (float) (Math.PI * 2 / 18 * i));
            			if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                            rc.buildRobot(RobotType.SCOUT, dir);
                            break;
                        }
            		}
            	}
            	
            	boolean overlyCloseTrees = false;
            	
            	//check that far enough from edge, move away if not 
            	if (!farming) {
            		if (!rc.onTheMap(myLocation.add(Direction.getNorth(), 3.1f))) {
            			tryMove(Direction.getSouth());
            		} else if (!rc.onTheMap(myLocation.add(Direction.getSouth(), 3.1f))) {
            			tryMove(Direction.getNorth());
            		} else if (!rc.onTheMap(myLocation.add(Direction.getEast(), 3.1f))) {
            			tryMove(Direction.getWest());
            		}  else if (!rc.onTheMap(myLocation.add(Direction.getWest(), 3.1f))) {
            			tryMove(Direction.getEast());
            		} else {
            			//check that there is space for a farm
            			TreeInfo[] closeTrees = rc.senseNearbyTrees(3.3f, Team.NEUTRAL);
                		if (closeTrees.length == 0) {
                			farming = true;
                		} else {
                			overlyCloseTrees = true;
                			tryMove(randomDirection());
                		}
            		}
            	}
            	
            	boolean safe = true;
            	RobotInfo[] enemies = rc.senseNearbyRobots(3f, enemy);
            	if (enemies.length > 0) {
            		safe = false;
            	}
            
            	//Starting from the direction facing the enemy, rotate and plant 5 trees around you if you can
            	if (farming) {
            		TreeInfo[] treesNearby = rc.senseNearbyTrees(2.5f, myTeam);
            		if(safe) {
		            	//can adjust tree planting limit here
		            	if (treesNearby.length < 5 && rc.getTreeCount() < 30 ) {
		            		for (int i = 1; i < 6; i++) {
		            			Direction plantDir = toEnemy.rotateLeftRads((float) (Math.PI * 2 / dirNumber * i));
		            			if (rc.canPlantTree(plantDir)) {
		            				rc.plantTree(plantDir);
		            			}
		            		}
		            	}
            		}
	            	//if you see a tree that needs to be watered, water it (do you need to move? don't think so)
	            	if (treesNearby.length > 0) {
		            	float treeHealth = 50;
		            	MapLocation treeLoc = treesNearby[0].getLocation();
		            	for (TreeInfo tree : treesNearby) {
		            		if (tree.getHealth() < treeHealth)  {
		            			treeHealth = tree.getHealth();
		            			treeLoc = tree.getLocation();
		            		}
		            	}
		            	if (rc.canWater(treeLoc)) {rc.water(treeLoc);}
	            	}
            	}
            	
            	//should every farmer do this?
            	//I think so, can play around with it
            	//maybe have "up to current max tree planting"?
            	
            	//if you see an enemy, run?
            	//RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
            	//if (enemies.length > 0) {tryMove(enemies[0].getLocation().directionTo(myLocation));}
            	
            	
            	
            	
            	
            	//if there are many trees around you, build more lumberjacks
            	TreeInfo[] neutralTrees = rc.senseNearbyTrees(5f, Team.NEUTRAL);
            	boolean manyTrees = false;
            	if (neutralTrees.length > 4) {
            		manyTrees = true;
            	}
            	
            	//if it is later in the game, build more soldiers
            	boolean lateGame = false;
            	if (((double) rc.getRoundNum()) / ((double)rc.getRoundLimit()) > 0.3 ) {
            		lateGame = true;
            	}
            	
            	if (rc.getRoundNum() > 5 && rc.getTeamBullets() > 100) {
		        	double random = Math.random() * 100;
		        	if (overlyCloseTrees) {
		        		buildLumberjack(toEnemy);
            		} else if (manyTrees) {
		        		if (random < 70) {
		        			buildLumberjack(toEnemy);
		        		} else if (random < 80) {
		        			buildScout(toEnemy);
		        		} else {
		        			buildSoldier(toEnemy);
		        		}
		        	} else if (lateGame) {
		        		if (random < 95) {
		        			buildSoldier(toEnemy);
		        		} else {
		        			buildLumberjack(toEnemy);
		        		}
		        	} else {
		        		if (random < 85) {
		        			buildSoldier(toEnemy);
		        		} else if (random < 95) {
		        			buildLumberjack(toEnemy);
		        		} else {
		        			buildScout(toEnemy);
		        		}
		        	}
            	}
            	
            buyVictoryPoints();
            
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                rc.setIndicatorDot(rc.getLocation(), 200, 200, 200);
                e.printStackTrace();
            }
        }
    }

    private static void buyVictoryPoints() throws GameActionException {
    	//can't get actual function? use 7.5 + (round)*12.5 / 3000
    	//TODO: make the actual function work
    	float pointCost =  (float) (7.5 + rc.getRoundNum() * 12.5 / 3000 + 0.25);
    	
		if (rc.getTeamBullets() > maxBullets) {
			rc.donate(pointCost);
		} 
		//if alone & not archon or gardener
		if (rc.getRobotCount() == 1 
				&& rc.getRoundNum() > 10 
				&& rc.getTeamBullets() > pointCost 
				&& !rc.getType().equals(RobotType.ARCHON)
				&& !rc.getType().equals(RobotType.GARDENER)) {
			rc.donate((float) (pointCost * Math.floor(rc.getTeamBullets() / pointCost)));
			return;
		} 
		//if we're reaching the end, donaaaaate
		if (rc.getRoundNum() + 50 > rc.getRoundLimit()) {
			rc.donate((float) (pointCost * Math.floor(rc.getTeamBullets() / pointCost)));
			return;
		}
		//this code would constantly donate some small portion
		/**
		if (!rc.getType().equals(RobotType.ARCHON) 
				&& !rc.getType().equals(rc.getType().equals(RobotType.GARDENER))) {
			int lastDonationRound = rc.readBroadcast(donationChannel);
			if (lastDonationRound + 20 < rc.getRoundNum()) {
				RobotInfo[] friends = rc.senseNearbyRobots(5, rc.getTeam());
				for (RobotInfo friend : friends) {
					if (friend.getType().equals(RobotType.GARDENER) || friend.getType().equals(RobotType.ARCHON)) {
						return;
					}
				}
				float donation = (float) (pointCost * Math.floor(rc.getTreeCount() * 20.0 / 3.0 / pointCost));
				if (donation < rc.getTeamBullets()) {
					rc.donate(donation);
					rc.broadcast(donationChannel, rc.getRoundNum());
				}
			}
		}
		*/
				
		
	}

	private static void buildSoldier(Direction toEnemy) throws GameActionException {
    	for (int i = 0; i < 18; i++) {
			Direction buildDir = toEnemy.rotateLeftRads((float) (Math.PI * 2 / 18 * i));
			if (rc.canBuildRobot(RobotType.SOLDIER, buildDir)) {
				rc.buildRobot(RobotType.SOLDIER, buildDir);
			}
		}
		
	}

	private static void buildScout(Direction toEnemy) throws GameActionException {
		for (int i = 0; i < 18; i++) {
			Direction buildDir = toEnemy.rotateLeftRads((float) (Math.PI * 2 / 18 * i));
			if (rc.canBuildRobot(RobotType.SCOUT, buildDir)) {
				rc.buildRobot(RobotType.SCOUT, buildDir);
			}
		}
	}

	private static void buildLumberjack(Direction toEnemy) throws GameActionException {
		for (int i = 0; i < 18; i++) {
			Direction buildDir = toEnemy.rotateLeftRads((float) (Math.PI * 2 / 18 * i));
			if (rc.canBuildRobot(RobotType.LUMBERJACK, buildDir)) {
				rc.buildRobot(RobotType.LUMBERJACK, buildDir);
			}
		}
	}

	static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team opponent = rc.getTeam().opponent();
        RobotType myType = rc.getType();
        float stride = myType.strideRadius;
        MapLocation goal = rc.getInitialArchonLocations(opponent)[0];
        boolean goalSet = true;
        int goalPriority = 0;
        

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	MapLocation myLocation = rc.getLocation();
            	//plan a move
            	MapLocation target = myLocation;
            	boolean targetSet = false;
            	RobotInfo[] friends = rc.senseNearbyRobots(-1, rc.getTeam());
            	RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
            	//if there are enemy lumberjacks / tanks that are closer than 5, move away
            	for (RobotInfo enemy : enemies) {
            		RobotType type = enemy.getType();
            		MapLocation loc = enemy.getLocation();
            		if ((type.equals(RobotType.LUMBERJACK) || type.equals(RobotType.TANK)) && loc.isWithinDistance(myLocation, 5) ) {
            			target = myLocation.add(loc.directionTo(myLocation), stride);
            			targetSet = true;
            			break;
            		}
            	}
            	if (!targetSet) {
            		for (RobotInfo enemy : enemies) {
            			RobotType type = enemy.getType();
            			MapLocation loc = enemy.getLocation();
                		if (type.equals(RobotType.ARCHON) || type.equals(RobotType.GARDENER)) {
                			target = myLocation.add(myLocation.directionTo(loc), stride);
                			targetSet = true;
                			break;
                		}
            		}
            	}
            	//if there are enemy archons / trees / farmers, target them
            	//if there are enemy soldiers, target them
        		if (!targetSet) {
            		for (RobotInfo enemy : enemies) {
            			RobotType type = enemy.getType();
            			MapLocation loc = enemy.getLocation();
                		if (type.equals(RobotType.SOLDIER)) {
                			target = myLocation.add(myLocation.directionTo(loc), stride);
                			targetSet = true;
                			break;
                		}
            		}
            	}
            	//if there are enemy scouts, target them if you have nothing better to do
        		if (!targetSet && !goalSet) {
            		for (RobotInfo enemy : enemies) {
            			RobotType type = enemy.getType();
            			MapLocation loc = enemy.getLocation();
                		if (type.equals(RobotType.SCOUT)) {
                			target = myLocation.add(myLocation.directionTo(loc), stride);
                			targetSet = true;
                			break;
                		}
            		}
            	}
        		
        		
        		//if close to goal, erase goal (and goal's broadcast channel)
            	if (myLocation.isWithinDistance(goal, 5)) {
            		goalSet = false;
            		rc.broadcast(goalPriority, 0);
            	}
        		
            	//broadcast locs of closest enemy in respective channel
            	//builder channel
            	//heavy channel
            	//soldier channel
            	//scout channel
        		boolean builderFound = false;
            	boolean heavyFound = false;
            	boolean soldierFound = false;
            	boolean scoutFound = false;
            	for (RobotInfo enemy : enemies) {
            		RobotType type = enemy.getType();
            		MapLocation loc = enemy.getLocation();
            		if (!builderFound && (type.equals(RobotType.ARCHON ) || type.equals(RobotType.GARDENER))) {
            			rc.broadcast(builderChannel, signalFromLocation(loc));
            			builderFound = true;
            		} else if (!heavyFound && (type.equals(RobotType.LUMBERJACK)) || type.equals(RobotType.TANK)) {
            			rc.broadcast(heavyChannel, signalFromLocation(loc));
            			heavyFound = true;
            		} else if (!soldierFound && (type.equals(RobotType.SOLDIER))) {
            			rc.broadcast(soldierChannel, signalFromLocation(loc));
            			soldierFound = true;
            		} else if (!scoutFound && type.equals(RobotType.SCOUT)) {
            			rc.broadcast(scoutChannel, signalFromLocation(loc));
            			scoutFound = true;
            		}
            	}
            	
            	
            	
            	
            	
            	
            	
            	//set goal according to priority
            	//builder
            	//heavy
            	//soldier
            	//scout.. maybe not?
            	if (goalPriority <= 4 && rc.readBroadcast(builderChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(builderChannel));
            		System.out.println("I have set goal as enemy loc");
            		goalSet = true;
            		goalPriority = 4;
            	} else if (goalPriority <= 3 && rc.readBroadcast(heavyChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(heavyChannel));
            		goalSet = true;
            		goalPriority = 3;
            	} else if (goalPriority <= 2 && rc.readBroadcast(soldierChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(soldierChannel));
            		goalSet = true;
            		goalPriority = 2;
            	} else if (goalPriority <= 1 && rc.readBroadcast(scoutChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(scoutChannel));
            		goalSet = true;
            		goalPriority = 1;
            	} 
            	
            	//if no target, set goal as target
            	if (!targetSet && goalSet) {
            		target = goal;
            		targetSet = true;
            	}
            	
            	//plan move to target, avoiding closest bullet / obstacles
            	if (targetSet) {
            		Direction plannedDirection = myLocation.directionTo(target);
	            	for (int offset = 0; offset < 180; offset = offset + 5) {
	            		Direction newDirection = plannedDirection.rotateLeftDegrees(offset);
	            		MapLocation plannedMove = myLocation.add(newDirection, rc.getType().strideRadius);
	            		if (rc.canMove(plannedMove) && !rc.hasMoved() ) {
	            			rc.move(plannedMove);
	            			break;
	            		} else {
	            			newDirection = plannedDirection.rotateRightDegrees(offset);
	                		plannedMove = myLocation.add(newDirection, rc.getType().strideRadius);
	                		if (rc.canMove(plannedMove) && !rc.hasMoved() ) {
	                			rc.move(plannedMove);
	                			break;
	                		}
	            		}	
	            	}
            	} else {
            	
	            	//if all else fails try a random direction
	            	Direction randomDir = randomDirection();
	            	if (!rc.hasMoved() && rc.canMove(randomDir)) {
	            		rc.move(randomDir);
	            	}
            	}
               
            	//plan a shot (single shots?)
            	
            	//list targets based on closeness and priority
            	List<RobotInfo> targets = new ArrayList<>();
            	
            	for (RobotInfo enemy : enemies) {
            		if (enemy.getType().equals(RobotType.LUMBERJACK) || enemy.getType().equals(RobotType.SOLDIER)) {
            			targets.add(enemy);
            		}
            	}
            	for (RobotInfo enemy:enemies) {
            		RobotType type = enemy.getType();
            		if (type.equals(RobotType.ARCHON) || type.equals(RobotType.TANK) || type.equals(RobotType.GARDENER)) {
            			targets.add(enemy);
            		}
            	}
            	for (RobotInfo enemy:enemies) {
            		RobotType type = enemy.getType();
            		if (type.equals(RobotType.SCOUT)) {
            			targets.add(enemy);
            		}
            	}
            	//.....maybe change to this:
            	//Priority rank:
            	//soldiers / lumberjacks (+0)
            	//tanks / archons / farmers (+2)
            	//scouts / enemy trees (+5)
            	myLocation = rc.getLocation();
            	//For target on list, if shot is clear, fire shot
            	//should strafe if shot is not clear for whatev reason
            	for (RobotInfo mark : targets) {
            		MapLocation markLoc = mark.getLocation();
            		if (!rc.hasAttacked()) {
            			Direction shotDirection = myLocation.directionTo(markLoc);
            			//should shoot pentad shots if bullets avilable and won't hit friends, single otherwise
            			//should check for trees?
            			if (!inFiringLine(shotDirection, friends)) {
            				if (rc.getTeamBullets() > 10 && !inFiringLinePentad(shotDirection, friends)) {
            					if (rc.canFirePentadShot()) {rc.firePentadShot(shotDirection);}
            				} else {
            					if (rc.canFireSingleShot()) {rc.fireSingleShot(shotDirection);}
            				}
            			}
            		}
            	}
            	
            	buyVictoryPoints();
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }
    
    private static boolean inFiringLinePentad(Direction shotDirection, RobotInfo[] friends) {
		for (int i = 1; i <= 2; i++) {
			if (inFiringLine(shotDirection.rotateLeftDegrees(i * 15), friends)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inFiringLine(Direction shotDirection, RobotInfo[] friends) {
    	
		Direction bulletDir = shotDirection;
		
		for (RobotInfo friend : friends) {
			float radius = friend.getRadius();
			MapLocation friendLoc = friend.getLocation();
			Direction toFriend = rc.getLocation().directionTo(friendLoc);
			float distToLoc = rc.getLocation().distanceTo(friendLoc);
			//MapLocation bulletMove = bulletLoc.add(bulletDir, speed);
			
			float theta = bulletDir.radiansBetween(toFriend);
	
	        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
	        if (Math.abs(theta) < Math.PI/2) {
	        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
	        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
	        // This corresponds to the smallest radius circle centered at our location that would intersect with the
	        // line that is the path of the bullet.
	        float perpendicularDist = (float)Math.abs(distToLoc * Math.sin(theta)); // soh cah toa :)
	        if (perpendicularDist <= radius) {return true;}
	        }
		}
		return false;
	}

	static void runTank() throws GameActionException {
		System.out.println("I'm a tank!");
        Team opponent = rc.getTeam().opponent();
        RobotType myType = rc.getType();
        float stride = myType.strideRadius;
        MapLocation goal = rc.getInitialArchonLocations(opponent)[0];
        boolean goalSet = true;
        int goalPriority = 0;
        

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	MapLocation myLocation = rc.getLocation();
            	//plan a move
            	MapLocation target = myLocation;
            	boolean targetSet = false;
            	RobotInfo[] friends = rc.senseNearbyRobots(-1, rc.getTeam());
            	RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
            	//if there are enemy lumberjacks / tanks that are closer than 5, move away
            	for (RobotInfo enemy : enemies) {
            		RobotType type = enemy.getType();
            		MapLocation loc = enemy.getLocation();
            		if ((type.equals(RobotType.LUMBERJACK) || type.equals(RobotType.TANK)) && loc.isWithinDistance(myLocation, 5) ) {
            			target = myLocation.add(loc.directionTo(myLocation), stride);
            			targetSet = true;
            			break;
            		}
            	}
            	if (!targetSet) {
            		for (RobotInfo enemy : enemies) {
            			RobotType type = enemy.getType();
            			MapLocation loc = enemy.getLocation();
                		if (type.equals(RobotType.ARCHON) || type.equals(RobotType.GARDENER)) {
                			target = myLocation.add(loc.directionTo(myLocation), stride);
                			targetSet = true;
                			break;
                		}
            		}
            	}
            	//if there are enemy archons / trees / farmers, target them
            	//if there are enemy soldiers, target them
        		if (!targetSet) {
            		for (RobotInfo enemy : enemies) {
            			RobotType type = enemy.getType();
            			MapLocation loc = enemy.getLocation();
                		if (type.equals(RobotType.SOLDIER)) {
                			target = myLocation.add(loc.directionTo(myLocation), stride);
                			targetSet = true;
                			break;
                		}
            		}
            	}
            	//if there are enemy scouts, target them
        		if (!targetSet) {
            		for (RobotInfo enemy : enemies) {
            			RobotType type = enemy.getType();
            			MapLocation loc = enemy.getLocation();
                		if (type.equals(RobotType.SCOUT)) {
                			target = myLocation.add(loc.directionTo(myLocation), stride);
                			targetSet = true;
                			break;
                		}
            		}
            	}
            	//broadcast locs of closest enemy in respective channel
            	//builder channel
            	//heavy channel
            	//soldier channel
            	//scout channel
        		boolean builderFound = false;
            	boolean heavyFound = false;
            	boolean soldierFound = false;
            	boolean scoutFound = false;
            	for (RobotInfo enemy : enemies) {
            		RobotType type = enemy.getType();
            		MapLocation loc = enemy.getLocation();
            		if (!builderFound && (type.equals(RobotType.ARCHON ) || type.equals(RobotType.GARDENER))) {
            			rc.broadcast(builderChannel, signalFromLocation(loc));
            			builderFound = true;
            		} else if (!heavyFound && (type.equals(RobotType.LUMBERJACK)) || type.equals(RobotType.TANK)) {
            			rc.broadcast(heavyChannel, signalFromLocation(loc));
            			heavyFound = true;
            		} else if (!soldierFound && (type.equals(RobotType.SOLDIER))) {
            			rc.broadcast(soldierChannel, signalFromLocation(loc));
            			soldierFound = true;
            		} else if (!scoutFound && type.equals(RobotType.SCOUT)) {
            			rc.broadcast(scoutChannel, signalFromLocation(loc));
            			scoutFound = true;
            		}
            	}
            	
            	
            	
            	//if close to goal, erase goal (and goal's broadcast channel)
            	if (myLocation.isWithinDistance(goal, 5)) {
            		goalSet = false;
            		rc.broadcast(goalPriority, 0);
            	}
            	
            	
            	
            	//set goal according to priority
            	//builder
            	//heavy
            	//soldier
            	//scout.. maybe not?
            	if (goalPriority <= 4 && rc.readBroadcast(builderChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(builderChannel));
            		System.out.println("I have set goal as enemy loc");
            		goalSet = true;
            		goalPriority = 4;
            	} else if (goalPriority <= 3 && rc.readBroadcast(heavyChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(heavyChannel));
            		goalSet = true;
            		goalPriority = 3;
            	} else if (goalPriority <= 2 && rc.readBroadcast(soldierChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(soldierChannel));
            		goalSet = true;
            		goalPriority = 2;
            	} else if (goalPriority <= 1 && rc.readBroadcast(scoutChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(scoutChannel));
            		goalSet = true;
            		goalPriority = 1;
            	} 
            	
            	//if no target, set goal as target
            	if (!targetSet && goalSet) {
            		target = goal;
            		targetSet = true;
            	}
            	
            	//plan move to target, avoiding closest bullet / obstacles
            	if (targetSet) {
            		Direction plannedDirection = myLocation.directionTo(target);
	            	for (int offset = 0; offset < 180; offset = offset + 5) {
	            		plannedDirection = plannedDirection.rotateLeftDegrees(offset);
	            		MapLocation plannedMove = myLocation.add(plannedDirection, rc.getType().strideRadius);
	            		if (rc.canMove(plannedMove) && !rc.hasMoved() ) {
	            			rc.move(plannedMove);
	            			break;
	            		} else {
	            			plannedDirection = plannedDirection.rotateRightDegrees(offset);
	                		plannedMove = myLocation.add(plannedDirection, rc.getType().strideRadius);
	                		if (rc.canMove(plannedMove) && !rc.hasMoved() ) {
	                			rc.move(plannedMove);
	                			break;
	                		}
	            		}	
	            	}
            	} else {
            	
	            	//if all else fails try a random direction
	            	Direction randomDir = randomDirection();
	            	if (!rc.hasMoved() && rc.canMove(randomDir)) {
	            		rc.move(randomDir);
	            	}
            	}
               
            	//plan a shot (single shots?)
            	
            	//list targets based on closeness and priority
            	List<RobotInfo> targets = new ArrayList<>();
            	
            	for (RobotInfo enemy : enemies) {
            		if (enemy.getType().equals(RobotType.LUMBERJACK) || enemy.getType().equals(RobotType.SOLDIER)) {
            			targets.add(enemy);
            		}
            	}
            	for (RobotInfo enemy:enemies) {
            		RobotType type = enemy.getType();
            		if (type.equals(RobotType.ARCHON) || type.equals(RobotType.TANK) || type.equals(RobotType.GARDENER)) {
            			targets.add(enemy);
            		}
            	}
            	for (RobotInfo enemy:enemies) {
            		RobotType type = enemy.getType();
            		if (type.equals(RobotType.SCOUT)) {
            			targets.add(enemy);
            		}
            	}
            	//.....maybe change to this:
            	//Priority rank:
            	//soldiers / lumberjacks (+0)
            	//tanks / archons / farmers (+2)
            	//scouts / enemy trees (+5)
            	myLocation = rc.getLocation();
            	//For target on list, if shot is clear, fire shot
            	//should strafe if shot is not clear for whatev reason
            	for (RobotInfo mark : targets) {
            		MapLocation markLoc = mark.getLocation();
            		if (!rc.hasAttacked()) {
            			Direction shotDirection = myLocation.directionTo(markLoc);
            			if (!inFiringLine(shotDirection, friends)) {
            				if (rc.canFireSingleShot()) {
            					rc.fireSingleShot(shotDirection);
            					break;
            				}
            			}
            		}
            	}
            	
            	buyVictoryPoints();
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }
    
    static void runScout() throws GameActionException {
    	MapLocation startingLocation = rc.getLocation();
    	Team myTeam = rc.getTeam();
        Team enemyTeam = myTeam.opponent();
        MapLocation originalLocation = rc.getLocation();
        Direction currentDirection = originalLocation.directionTo(rc.getInitialArchonLocations(enemyTeam)[0]);
        float stride = rc.getType().strideRadius;
    	float bodyRadius = rc.getType().bodyRadius;
        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	MapLocation myLocation = rc.getLocation();
            	TreeInfo[] trees = rc.senseNearbyTrees();
            	RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
            	MapLocation target = myLocation;
            	RobotInfo[] friends = rc.senseNearbyRobots(-1, myTeam);
            	Direction plannedDirection = currentDirection;
            	
            	boolean targetSet = false;
            	
                //shake if can shake
            	shakeNearby(trees);
            	
            	
            	//if tree that can be shaken within range, target it
            	for (TreeInfo tree : trees) {
            		if (tree.getContainedBullets() > 0) {
        				plannedDirection = myLocation.directionTo(tree.getLocation());
        				targetSet = true;
        				break;
        			}
            	}
            	
            	//broadcast locations of enemies within sight (different channels for builders / fighters)
            	boolean builderFound = false;
            	boolean heavyFound = false;
            	boolean soldierFound = false;
            	boolean scoutFound = false;
            	for (RobotInfo enemy : enemies) {
            		RobotType type = enemy.getType();
            		MapLocation loc = enemy.getLocation();
            		if (!builderFound && (type.equals(RobotType.ARCHON ) || type.equals(RobotType.GARDENER))) {
            			rc.broadcast(builderChannel, signalFromLocation(loc));
            			builderFound = true;
            		} else if (!heavyFound && (type.equals(RobotType.LUMBERJACK)) || type.equals(RobotType.TANK)) {
            			rc.broadcast(heavyChannel, signalFromLocation(loc));
            			heavyFound = true;
            		} else if (!soldierFound && (type.equals(RobotType.SOLDIER))) {
            			rc.broadcast(soldierChannel, signalFromLocation(loc));
            			soldierFound = true;
            		} else if (!scoutFound && type.equals(RobotType.SCOUT)) {
            			rc.broadcast(scoutChannel, signalFromLocation(loc));
            			scoutFound = true;
            		}
            	}
            	
            	//randomly make beeps?
            	if (Math.random()*10 < 1) {
            		rc.broadcast(10, 0);
            	}
            	
            	//move away from overly close friends, too
            	if (!targetSet) {
	            	for (RobotInfo friend : friends) {
	            		MapLocation friendLoc = friend.getLocation();
	            		if (friendLoc.isWithinDistance(myLocation, 4) && !friend.getType().equals(RobotType.GARDENER)) {
	            			plannedDirection = friendLoc.directionTo(myLocation);
	            			targetSet = true;
	            			break;
	            		}
	            	}
            	}
            	
            	//if there are fighter enemies closer than 5, set direction away from them
            	if (!targetSet) {
	            	for (RobotInfo enemy : enemies) {
	            		RobotType eType = enemy.getType();
	            		MapLocation enemyLoc = enemy.getLocation();
	            		Direction toEnemy = myLocation.directionTo(enemyLoc);
	            		float offset = toEnemy.degreesBetween(currentDirection);
	            		if (myLocation.isWithinDistance(enemyLoc, 5) &&
	            				!eType.equals(RobotType.ARCHON) && !eType.equals(RobotType.GARDENER)) {
	            			plannedDirection = toEnemy.opposite();
	            			if (offset < 0) {
	            				plannedDirection = plannedDirection.rotateLeftDegrees(40);
	            			} else {
	            				plannedDirection = plannedDirection.rotateRightDegrees(40);
	            			}
	            			break;
	            		} else if (friends.length > 0 && enemyLoc.isWithinDistance(friends[0].getLocation(), 4)) {
	            			plannedDirection = toEnemy.opposite();
	            			break;
	            		} else if (myLocation.isWithinDistance(enemyLoc, 6.8f)) {
	            			
	            			if (offset > 0) {
	            				plannedDirection = toEnemy.rotateLeftDegrees(90);
	            				break;
	            			} else {
	            				plannedDirection = toEnemy.rotateRightDegrees(90);
	            				break;
	            			}
	            		} else {
	            			plannedDirection = toEnemy;
	            		}
	            	}
	            	targetSet = true;
            	}
            	
            	
            	//should I maybe just ... ignore archons / farmers?
            	//maybe, if there are other teammates next to the enemies, move away?
            	//if friendly lumberjack / soldiers are closer than 4 to enemy, set direction away from enemy
            	
            	//if there are enemies closer than 7, set direction to 90 deg to the side from them
            	
            	//if there are enemies closer than 10, set direction towards them
            	
            	//if there no enemies, continue in direction
            	
            	//if near the edge of map, turn in different direction
            	
            	MapLocation plannedMove = myLocation.add(plannedDirection, stride);
            	if (!rc.onTheMap(plannedMove)) {
            		double degChange = (Math.random() - 0.5) * 40;
            		if (Math.random() < 0.5) {plannedDirection = plannedDirection.rotateLeftDegrees((float) (150 + degChange));}
            		else {plannedDirection = plannedDirection.rotateRightDegrees((float) (150 + degChange));}
            		plannedMove = myLocation.add(plannedDirection, stride);
            	}
            	
            	//Plan a move in desired direction. Avoid bullets, and other robots
            	//(if there is a bullet that will collide with planned move, continue
            	//altering move until bullet will not collide ... look at only the closest bullet? Closest two-three bullets?
            	//make the move
            	BulletInfo[] bullets = rc.senseNearbyBullets();
            	Direction adjustedDir = plannedDirection;
            	MapLocation adjustedMove = plannedMove;
            	int maxBullet = Math.min(bullets.length, 4);
            	for (int offset = 0; offset < 180 ; offset = (offset + 5)) {
            		boolean safe = true;
            		adjustedDir = plannedDirection.rotateLeftDegrees(offset);
            		adjustedMove = myLocation.add(adjustedDir, stride);
	            	for (int i = 0; i < maxBullet; i++) {
	            		if (willCollidewith(bullets[i], adjustedMove) || bullets[i].getLocation().isWithinDistance(adjustedMove, bodyRadius)) {
	            			safe = false;
	            			break;
	            		}
	            	}
	            	if (safe) {
	            		plannedDirection = adjustedDir;
	            		plannedMove = adjustedMove;
	            		break;
	            	} else {
	            		safe = true;
	            		adjustedDir = plannedDirection.rotateRightDegrees(offset);
	            		adjustedMove = myLocation.add(adjustedDir, stride);
		            	for (int i = 0; i < maxBullet; i++) {
		            		if (willCollidewith(bullets[i], adjustedMove) || bullets[i].getLocation().isWithinDistance(adjustedMove, bodyRadius)) {
		            			safe = false;
		            			break;
		            		}
		            	}
		            	if (safe) {
		            		plannedDirection = adjustedDir;
		            		plannedMove = adjustedMove;
		            		break;
		            	}
	            	}
            	}
            	for (int offset = 0; offset < 120; offset = offset + 5) {
            		adjustedDir = plannedDirection.rotateLeftDegrees(offset);
            		adjustedMove = myLocation.add(adjustedDir, stride);
            		//should I check for bullets?
            		if (rc.canMove(adjustedMove) && !rc.hasMoved()) {
            			rc.move(adjustedMove);
            			break;
            		} else {
            			adjustedDir = plannedDirection.rotateRightDegrees(offset);
                		adjustedMove = myLocation.add(adjustedDir, stride);
                		//should I check for bullets?
                		if (rc.canMove(adjustedMove) && !rc.hasMoved()) {
                			rc.move(adjustedMove);
                			break;
                		}
            		}
            	}
            	//set current direction to planned direction
            	currentDirection = plannedDirection;
            	
            	myLocation = rc.getLocation();
            
            	//shoot at farmers, archons if you are close
            	if (enemies.length > 0) {
            		MapLocation mark = enemies[0].getLocation();
            		RobotType eType = enemies[0].getType();
            		if (eType.equals(RobotType.ARCHON) || eType.equals(RobotType.GARDENER)) {
            			if (mark.isWithinDistance(myLocation, 1.5f) && !rc.hasAttacked()) {
            				if (rc.canFireSingleShot()) {
            					rc.fireSingleShot(myLocation.directionTo(mark));
            				}
            			}
            		}
            	}
            	//maybe I could try using trees as cover?
            	
            	buyVictoryPoints();
            	
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                rc.setIndicatorDot(rc.getLocation(), 200, 200, 200);
                e.printStackTrace();
            }
        }
    }
    
    private static boolean willCollidewith(BulletInfo bullet, MapLocation loc) {
		float radius = rc.getType().bodyRadius;
		float speed = bullet.getSpeed();
		Direction bulletDir = bullet.getDir();
		MapLocation bulletLoc = bullet.getLocation();
		Direction toLoc = bulletLoc.directionTo(loc);
		float distToLoc = bulletLoc.distanceTo(loc);
		//MapLocation bulletMove = bulletLoc.add(bulletDir, speed);
		if (!bulletLoc.isWithinDistance(loc, speed + radius)) {
			return false;
		} 
		float theta = bulletDir.radiansBetween(toLoc);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToLoc * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
	}

	static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team myTeam = rc.getTeam();
        Team enemyTeam = myTeam.opponent();
        MapLocation originalLocation = rc.getLocation();
        boolean goalSet = true;
        MapLocation goal = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
        int goalPriority = 0;
        //goalPriority builders = 4, heavy = 3, soldier = 2, scout = 1, initial / broadcaster = 0, 
        //channels -> builders = 4, heavy = 3, soldier = 2, scout = 1
        // The code you want your robot to perform every round should be in this loop
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	
            	MapLocation myLocation = rc.getLocation();
            	TreeInfo[] trees = rc.senseNearbyTrees();
            	
            	RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
            	MapLocation target = myLocation;
            	RobotInfo[] friends = rc.senseNearbyRobots(-1, myTeam);
            	boolean doingAction = false;
            	
            	boolean targetSet = false;
            	
            	//shake, strike, or chop if valid (don't strike if next to farmer?)
            	if (enemies.length > 0) {
	            	RobotInfo closestEnemy = enemies[0];
	            	if (rc.canStrike() && closestEnemy.getLocation().isWithinDistance(myLocation, 2 + closestEnemy.getRadius())) {
	            		rc.strike();
	            		doingAction = true;
	            	}
            	}
            	if (chopNearby(trees)) {doingAction = true;}
            	if (shakeNearby(trees)) {doingAction = true;}
            	
            	
            	if (enemies.length > 0) {
	            	//if there are enemy archons or farmers in sight, set target as them. Broadcast their loc(s)?
	            	for (RobotInfo enemy : enemies) {
	            		RobotType type = enemy.getType();
	            		if (type.equals(RobotType.ARCHON) || type.equals(RobotType.GARDENER)) {
	            			target = enemy.getLocation();
	            			targetSet = true;
	            			rc.broadcast(builderChannel, signalFromLocation(target));
	            			System.out.println("I have broadcast enemy location");
	            			break;
	            		}
	            	}
	            	
	            	//if there are enemy lumberjacks or tanks in sight, set target as them. Broadcast their loc (different channel)
	            	if (!targetSet) {
	            		for (RobotInfo enemy : enemies) {
	                		RobotType type = enemy.getType();
	                		if (type.equals(RobotType.LUMBERJACK) || type.equals(RobotType.TANK)) {
	                			target = enemy.getLocation();
	                			targetSet = true;
	                			rc.broadcast(heavyChannel, signalFromLocation(target));
	                			break;
	                		}
	                	}
	            	}
	            	
	            	//if there are trees close to initial creation location, in the way to goal, enemy, 
	            	//or contain a robot, or can be shaken, target them
	            	if (!targetSet) {
	            		for (TreeInfo tree : trees) {
	            			if (!tree.getTeam().equals(myTeam)) {
	            				MapLocation treeLocation = tree.getLocation();
		            			Team treeTeam = tree.getTeam();
		            			if (treeTeam.equals(Team.NEUTRAL) && treeLocation.isWithinDistance(originalLocation, 6.0f + tree.radius)) {
		            				target = tree.getLocation();
		            				targetSet = true;
		            				break;
		            			}
		            			if (!(tree.getContainedRobot() == null)) { //TODO how should I actually check for a null AH
		            				target = tree.getLocation();
		            				targetSet = true;
		            				break;
		            			}
		            			if (tree.getContainedBullets() > 0) {
		            				target = tree.getLocation();
		            				targetSet = true;
		            				break;
		            			}
		            			Direction directionToGoal = myLocation.directionTo(goal);
		            			Direction directionToTree = myLocation.directionTo(treeLocation);
		            			double offset = Math.abs(directionToGoal.degreesBetween(directionToTree));
		            			double maxDegreeOffset = Math.abs(Math.toDegrees(Math.atan(tree.getRadius() / myLocation.distanceTo(treeLocation))));
		            			if (offset < maxDegreeOffset) {
		            				target = tree.getLocation();
		            				targetSet = true;
		            				break;
		            			}
	            			}
	            		}
	            	}
	            	
	            	
	            	//if there are soldiers, or scouts, set target as them
	            	//Broadcast locs of enemy soldiers (in a different channel)
	            	if (!targetSet) {
	            		for (RobotInfo enemy : enemies) {
	                		RobotType type = enemy.getType();
	                		if (type.equals(RobotType.SOLDIER) || type.equals(RobotType.SCOUT)) {
	                			target = enemy.getLocation();
	                			targetSet = true;
	                			if (type.equals(RobotType.SOLDIER)) {
	                				rc.broadcast(soldierChannel, signalFromLocation(target));
	                			} else {
	                				rc.broadcast(scoutChannel, signalFromLocation(target));
	                			}
	                			break;
	                		}
	                	}
	            	}
            	
            	}
            	
            	
            	//if there are broadcasts as to enemy locs, set them as goal (by nearness?)
            	
            	if (goalPriority <= 4 && rc.readBroadcast(builderChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(builderChannel));
            		System.out.println("I have set goal as enemy loc");
            		goalSet = true;
            		goalPriority = 4;
            	} else if (goalPriority <= 3 && rc.readBroadcast(heavyChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(heavyChannel));
            		goalSet = true;
            		goalPriority = 3;
            	} else if (goalPriority <= 2 && rc.readBroadcast(soldierChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(soldierChannel));
            		goalSet = true;
            		goalPriority = 2;
            	} else if (goalPriority <= 1 && rc.readBroadcast(scoutChannel) != 0) {
            		goal = locationFromSignal(rc.readBroadcast(scoutChannel));
            		goalSet = true;
            		goalPriority = 1;
            	} 
            	
            	if (rc.readBroadcast(builderChannel) == 0) {
            		System.out.println("Builder channel empty");
            	}
            	
            	/**else if (!goalSet) {
            		MapLocation[] broadcasts = rc.senseBroadcastingRobotLocations();
            		goal = broadcasts[0];
            		goalSet = true;
            		goalPriority = 0;
            	}
	            	*/
	            	
            	
            	
            	
            	//if next to goal, erase goal. Erase broadcast channel which set the goal
            	if (!targetSet && myLocation.isWithinDistance(goal, 5)) {
            		goalSet = false;
            		if (goalPriority > 0) {
            			rc.broadcast(goalPriority, 0);
            			System.out.println("Nothing at my goal");
            		}
            		goalPriority = 0;
            	}
            	
            	
            	
            	if (!targetSet) { //if no target is set, set target as goal.
	            	if (goalSet) {
            			target = goal;
            			targetSet = true;
            		}
            	}
            	
            	if (targetSet) {rc.setIndicatorDot(target, 0, 220, 0);}
            	else {rc.setIndicatorDot(myLocation, 250, 0, 0);}
            	if (goalSet) {rc.setIndicatorDot(goal, 0, 0, 230);}
            	//Plan a move. If move is to a friendly lumberjack that has attacked, strafe sideways.
            	//Adjust plan to avoid obstacles. 
            	//move
            	
            	Direction plannedDirection = myLocation.directionTo(target);
            	MapLocation plannedMove = myLocation.add(plannedDirection, rc.getType().strideRadius);
            	if (friends.length > 0 && targetSet){
	            	List<RobotInfo> lumberjacks = filterForAttackingLumberjacks(friends);
	            	
	            	if (lumberjacks.size() > 0) { //should I only check for closest lumberjacks, or all?
	            		RobotInfo lumberjack = lumberjacks.get(0);
	            		MapLocation lumberjackLoc = lumberjack.getLocation();
	        			if (lumberjackLoc.isWithinDistance(plannedMove, 3.1f)) {
	        				
	        				float change = plannedDirection.degreesBetween(myLocation.directionTo(lumberjackLoc));
	    					for (int offset = 0; offset < 70; offset = offset + 5) {
	    						Direction newPlanDir;
	    						if (change < 0) {
	    							newPlanDir = plannedDirection.rotateLeftDegrees(offset);
	    						} else {
	    							newPlanDir = plannedDirection.rotateRightDegrees(offset);
	    						}
	    						MapLocation newPlanMove = myLocation.add(newPlanDir, rc.getType().strideRadius);
	    						if (!lumberjackLoc.isWithinDistance(newPlanMove, (float) 3.1)) {
	    							plannedMove = newPlanMove;
	    							plannedDirection = newPlanDir;
	    							break;
	    						}
	    					}
	        			}
	            	} 
            	} 
            	System.out.println("I got out the loop");
            	bytecodeWarning();
            	if (targetSet) {
            		int maxOffset = 0;
            		if (doingAction) {maxOffset = 20;} else {maxOffset = 180;}
	            	for (int offset = 0; offset < maxOffset; offset = offset + 5) {
	            		Direction newDirection = plannedDirection.rotateLeftDegrees(offset);
	            		plannedMove = myLocation.add(newDirection, rc.getType().strideRadius);
	            		if (rc.canMove(plannedMove) && !rc.hasMoved() ) {
	            			rc.move(plannedMove);
	            			break;
	            		} else {
	            			newDirection = plannedDirection.rotateRightDegrees(offset);
	                		plannedMove = myLocation.add(newDirection, rc.getType().strideRadius);
	                		if (rc.canMove(plannedMove) && !rc.hasMoved() ) {
	                			rc.move(plannedMove);
	                			break;
	                		}
	            		}	
	            	}
            	} else {
            	
	            	//if all else fails try a random direction
	            	Direction randomDir = randomDirection();
	            	if (!rc.hasMoved() && rc.canMove(randomDir)) {
	            		rc.move(randomDir);
	            	}
            	}
            	
            	myLocation = rc.getLocation();
            	//shake, strike, or chop if valid, once again
            	if (enemies.length > 0) {
	            	RobotInfo closestEnemy = enemies[0];
	            	if (rc.canStrike() && closestEnemy.getLocation().isWithinDistance(myLocation, 2 + closestEnemy.getRadius())) {
	            		rc.strike();
	            	}
            	}
            
            	chopNearby(trees);
            	shakeNearby(trees);
            	buyVictoryPoints();
            	
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                rc.setIndicatorDot(rc.getLocation(), 200, 200, 200);
                e.printStackTrace();
            }
        }
    }
    


	private static void bytecodeWarning() throws GameActionException {
		System.out.println("ByteCodeWarning");
		System.out.println(Clock.getBytecodeNum());
		
		if (Clock.getBytecodeNum() < 500) {
			//rc.setIndicatorDot(rc.getLocation(), 0, 0, 250);
		} if (Clock.getBytecodeNum() > 500) {
			//rc.setIndicatorDot(rc.getLocation(), 0, 250, 0);
		}
		
		
	}

	private static int signalFromLocation(MapLocation target) {
		int signal = ((int) (target.x))*1000 + ((int) (target.y));
		return signal;
	}

	private static MapLocation locationFromSignal(int message) {
		return new MapLocation( (float) (message/1000) , (float) (message%1000));
	}

	private static boolean shakeNearby(TreeInfo[] trees) throws GameActionException {
		for (TreeInfo tree : trees) {
			int treeID = tree.getID();
			if (tree.getContainedBullets() > 0 && rc.canShake(treeID)) {
				rc.shake(treeID);
				return true;
			}
		}
		return false;
	}

	private static boolean chopNearby(TreeInfo[] trees) throws GameActionException {
		for (TreeInfo tree : trees) {
			int treeID = tree.getID();
			Team treeTeam = tree.getTeam();
			if (rc.canChop(treeID) && !treeTeam.equals(rc.getTeam())) {
				rc.chop(treeID);
				return true;
			}
		}
		return false;
		
	}

	private static List<RobotInfo> filterForAttackingLumberjacks(RobotInfo[] teammates) {
		List<RobotInfo> attackers = new ArrayList<>();
		for (RobotInfo mate : teammates) {
			if (mate.getAttackCount() > 0 && mate.getType().equals(RobotType.LUMBERJACK)) {
				attackers.add(mate);
			}
		}
		return attackers;
	}

	/**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,30,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir) && !rc.hasMoved()) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck)) && !rc.hasMoved()) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))&& !rc.hasMoved()) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}
