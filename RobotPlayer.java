package examplefuncsplayer;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {

    static RobotController rc;
    static Random rng = new Random();
    
    // Memory to store important locations (since we have limited vision)
    static MapLocation myKingLocation = null;
    static int turnCount = 0;

    // Threshold: How much cheese to carry before running home?
    // Carrying cheese slows you down (0.01 * amount). 
    // 5 cheese = 95% speed. 50 cheese = 50% speed.
    static final int CHEESE_CARRY_LIMIT = 20; 

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        rng.setSeed(rc.getID()); // Seeding with ID ensures deterministic behavior per bot

        while (true) {
            turnCount++;
            try {
                // Initial Setup: Where is my King?
                // On spawn, we are likely adjacent to the King.
                if (myKingLocation == null) {
                    findMyKing();
                }

                // Branch logic based on unit type
                switch (rc.getType()) {
                    case BABY_RAT:
                        runBabyRat();
                        break;
                    case RAT_KING:
                        runRatKing();
                        break;
                    default:
                        break;
                }

            } catch (GameActionException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    private static void runBabyRat() throws GameActionException {
        // 1. SURVIVAL: Check for Cats
        // Cats are the biggest threat. If we see one, everything else is irrelevant.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, Team.NEUTRAL); // Assuming Cats are NEUTRAL
        for (RobotInfo robot : enemies) {
            if (robot.getType().toString().contains("CAT")) { // Check if it's actually a cat
                // Run away!
                Direction away = rc.getLocation().directionTo(robot.location).opposite();
                if (tryMove(away)) {
                    rc.setIndicatorString("Fleeing Cat!");
                    return; // End turn logic
                }
            }
        }

        // 2. LOGISTICS: Drop off cheese if we have too much
        // Note: Spec says raw cheese slows us down.
        int myCheese = rc.getResourceAmount(ResourceType.CHEESE); // Assuming method for getting inventory
        if (myCheese > CHEESE_CARRY_LIMIT) {
             if (myKingLocation != null) {
                 if (rc.canSenseLocation(myKingLocation)) {
                     // If we are close enough to transfer, give it.
                     // Spec: "bring it back to a rat king"
                     if (rc.getLocation().isAdjacentTo(myKingLocation)) {
                         // Check API for give(). Usually give(location, amount) or give(robotID, amount)
                         // Here we assume standard 'give' or implicit transfer upon touching
                         if (rc.canGiveResource(myKingLocation, ResourceType.CHEESE, myCheese)) {
                             rc.giveResource(myKingLocation, ResourceType.CHEESE, myCheese);
                             rc.setIndicatorString("Delivered Cheese");
                             return;
                         }
                     }
                 }
                 // Move towards King
                 rc.setIndicatorString("Returning to King with " + myCheese + " cheese");
                 tryMove(rc.getLocation().directionTo(myKingLocation));
                 return;
             }
        }

        // 3. ECONOMY: Look for Cheese
        // We look for cheese on the ground.
        MapLocation[] cheeseLocations = rc.senseNearbyCheeseLocations(); // inferred method name
        if (cheeseLocations.length > 0) {
            MapLocation closestCheese = getClosest(cheeseLocations);
            if (closestCheese != null) {
                if (rc.getLocation().equals(closestCheese)) {
                    // We are standing on cheese, we assume auto-pickup or explicit pickup
                    // If explicit pickup is needed: rc.collectResource();
                    rc.setIndicatorString("Collecting Cheese");
                } else {
                    rc.setIndicatorString("Moving to Cheese");
                    tryMove(rc.getLocation().directionTo(closestCheese));
                }
                return;
            }
        }

        // 4. EXPLORATION: Patrol and Spin
        // We have a 90 degree vision cone. If we just move forward, we miss things.
        // Every few turns, turn to scan.
        if (turnCount % 7 == 0 && rc.canTurn(Direction.EAST)) { // Arbitrary turn to scan
            rc.turn(rc.getDirection().rotateRight());
        } else {
            // Wander randomly but prefer moving forward
            tryMove(randomDirection());
        }
    }

    private static void runRatKing() throws GameActionException {
        // King logic: Stay alive, spawn babies.
        // If we have cheese, spawn a baby rat.
        if (rc.isReady() && rc.getTeamCheese() > 100) { // Keep a buffer for food
            // Try to spawn in all directions
            for (Direction d : Direction.values()) {
                if (rc.canSpawn(d, RobotType.BABY_RAT)) { // Inferred spawn method
                    rc.spawn(d, RobotType.BABY_RAT);
                    break;
                }
            }
        }
    }

    // --- Helpers ---

    /**
     * Attempts to move in the given direction, or adjacent directions if blocked.
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        if (!rc.isReady()) return false; // Check cooldowns (< 10)

        // Try straight, then right, then left
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else if (rc.canMove(dir.rotateRight())) {
            rc.move(dir.rotateRight());
            return true;
        } else if (rc.canMove(dir.rotateLeft())) {
            rc.move(dir.rotateLeft());
            return true;
        }
        return false;
    }

    /**
     * Helper to update King location if we see it.
     */
    static void findMyKing() {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType() == RobotType.RAT_KING) {
                myKingLocation = ally.getLocation();
                break;
            }
        }
        // Fallback: If we just spawned, the King is likely behind us or very close.
        if (myKingLocation == null && turnCount < 5) {
             // Heuristic: Check immediate neighbors for a big unit or save spawn location
        }
    }

    static MapLocation getClosest(MapLocation[] locs) {
        if (locs.length == 0) return null;
        MapLocation myLoc = rc.getLocation();
        MapLocation closest = null;
        int minDist = 99999;
        
        for (MapLocation loc : locs) {
            int dist = myLoc.distanceSquaredTo(loc);
            if (dist < minDist) {
                minDist = dist;
                closest = loc;
            }
        }
        return closest;
    }

    static Direction randomDirection() {
        return Direction.values()[rng.nextInt(8)];
    }
}
