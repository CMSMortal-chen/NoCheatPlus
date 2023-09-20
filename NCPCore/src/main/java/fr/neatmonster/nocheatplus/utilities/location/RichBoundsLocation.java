/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities.location;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeReference;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.components.location.IGetBlockPosition;
import fr.neatmonster.nocheatplus.components.location.IGetBox3D;
import fr.neatmonster.nocheatplus.components.location.IGetBukkitLocation;
import fr.neatmonster.nocheatplus.components.location.IGetPosition;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache.IBlockCacheNode;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MapUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;

/**
 * A location with bounds with a lot of extra stuff.
 * 
 * @author asofold
 *
 */
public class RichBoundsLocation implements IGetBukkitLocation, IGetBlockPosition, IGetBox3D {

    // TODO: Consider switching back from default to private visibility (use getters for other places).
    // TODO: Do any of these belong to RichEntityLocation?

    // Simple members // 
    /** Y parameter for growing the bounding box with the isOnGround check. */
    double yOnGround = 0.001;

    /** The player coordinates to block coordinates (int). */
    int blockX, blockY, blockZ;

    /** The player coordinates. */
    double x, y, z;

    /** The rotation. */
    float yaw, pitch; 

    /** Bounding box. */
    double minX, maxX, minY, maxY, minZ, maxZ;

    /** Horizontal margin for the bounding box (center towards edge). */
    double boxMarginHorizontal;

    /** Vertical margin for the bounding box (y towards top). */
    double boxMarginVertical;

    /** Minimal yOnGround for which the player is on ground. No extra xz/y margin.*/
    double onGroundMinY = Double.MAX_VALUE;
    
    /** Maximal yOnGround for which the player is not on ground. No extra xz/y margin.*/
    double notOnGroundMaxY = Double.MIN_VALUE;


    // "Light" object members (reset to null on set) //
    // TODO: primitive+isSet? AlmostBoolean?
    // TODO: All properties that can be set should have a "checked" flag, thus resetting the flag suffices.
    // TODO: nodeAbove ?

    /** Type node for the block at the position. */
    IBlockCacheNode node = null;

    /** Type node of the block below. */
    IBlockCacheNode nodeBelow = null;

    /** All block flags collected for maximum used bounds. */
    Long blockFlags = null;

    /** Is the player on ladder?. */
    Boolean onClimbable = null;

    /** Simple test if the exact position is passable. */
    Boolean passable = null;

    /** Bounding box collides with blocks. */
    Boolean passableBox = null;
    
    /** Is the player above stairs?. */
    Boolean aboveStairs = null;

    /** Is the player in lava?. */
    Boolean inLava = null;

    /** Is the player in water?. */
    Boolean inWater = null;

    /** Is the player in water logged block?. */
    Boolean inWaterLogged = null;

    /** Is the player in web?. */
    Boolean inWeb = null;

    /** Is the player on ice?. */
    Boolean onIce = null;

    /** Is the player on blue ice?. */
    Boolean onBlueIce = null;

    /** Is the player on the ground?. */
    Boolean onGround = null;
    
    /** Is the player IN soul sand. */
    Boolean inSoulSand = null;
    
    /** Is the player on a honey block. */
    Boolean onHoneyBlock = null;
    
    /** Is the player on a slime block? */
    Boolean onSlimeBlock = null;
    
    /** Is the player in a berry bush? */
    Boolean inBerryBush = null;
    
    /** Is the player in powder snow? */
    Boolean inPowderSnow = null;
    
    /** Is the player on a boncy block? (Bed, slime) */
    Boolean onBouncyBlock = null;

    /** Is the player in a bubblestream? */
    Boolean inBubblestream = null;


    // "Heavy" object members that need to be set to null on cleanup. //    
    /** Block property access. */
    BlockCache blockCache = null;

    /** Bukkit world. */
    World world = null;


    /**
     * Instantiates a new rich bounds location.
     *
     * @param blockCache
     *            BlockCache instance, may be null.
     */
    public RichBoundsLocation(final BlockCache blockCache) {
        this.blockCache = blockCache;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetBukkitLocation#getWorld()
     */
    @Override
    public World getWorld() {
        return world;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetLocation#getWorldName()
     */
    @Override
    public String getWorldName() {
        return world.getName();
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetPosition#getX()
     */
    @Override
    public double getX() {
        return x;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetPosition#getY()
     */
    @Override
    public double getY() {
        return y;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetPosition#getZ()
     */
    @Override
    public double getZ() {
        return z;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetLook#getYaw()
     */
    @Override
    public float getYaw() {
        return yaw;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetLook#getPitch()
     */
    @Override
    public float getPitch() {
        return pitch;
    }

    /**
     * Gets the vector.
     *
     * @return the vector
     */
    public Vector getVector() {
        return new Vector(x, y, z);
    }

    /**
     * Gets a new Location instance representing this position.
     *
     * @return the location
     */
    public Location getLocation() {
        if (this.world == null) {
            throw new NullPointerException("World is null.");
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetBlockPosition#getBlockX()
     */
    @Override
    public int getBlockX() {
        return blockX;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetBlockPosition#getBlockY()
     */
    @Override
    public int getBlockY() {
        return blockY;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.location.IGetBlockPosition#getBlockZ()
     */
    @Override
    public int getBlockZ() {
        return blockZ;
    }

    /**
     * Return the bounding box as a new double array (minX, minY, minZ, maxX,
     * maxY, maxZ).
     *
     * @return the bounds as doubles
     */
    public double[] getBoundsAsDoubles() {
        return new double[] {minX, minY, minZ, maxX, maxY, maxZ};
    }

    @Override
    public double getMinX() {
        return minX;
    }

    @Override
    public double getMinZ() {
        return minZ;
    }

    @Override
    public double getMaxX() {
        return maxX;
    }

    @Override
    public double getMaxZ() {
        return maxZ;
    }

    @Override
    public double getMinY() {
        return minY;
    }

    @Override
    public double getMaxY() {
        return maxY;
    }

    /**
     * Get the bounding box margin from the center (x ,z) to the edge of the
     * box. This value may be adapted from entity width or other input, and it
     * might be cut down to a certain resolution (e.g. 1/1000).
     *
     * @return the box margin horizontal
     */
    public double getBoxMarginHorizontal() {
        return boxMarginHorizontal;
    }

    /**
     * Get the bounding box margin from the y coordinate (feet for entities) to
     * the top.
     *
     * @return the box margin vertical
     */
    public double getBoxMarginVertical() {
        return boxMarginVertical;
    }

    /**
     * Compares block coordinates (not the world).
     *
     * @param other
     *            the other
     * @return true, if is same block
     */
    public final boolean isSameBlock(final IGetBlockPosition other) {
        return blockX == other.getBlockX() && blockZ == other.getBlockZ() && blockY == other.getBlockY();
    }

    /**
     * Block coordinate comparison.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if is same block
     */
    public final boolean isSameBlock(final int x, final int y, final int z) {
        return blockX == x && blockZ == z && blockY == y;
    }

    /**
     * Compares block coordinates (not the world).
     *
     * @param loc
     *            the loc
     * @return true, if is same block
     */
    public final boolean isSameBlock(final Location loc) {
        return blockX == loc.getBlockX() && blockZ == loc.getBlockZ() && blockY == loc.getBlockY();
    }

    /**
     * Check if this location is above the given one (blockY + 1).
     *
     * @param loc
     *            the loc
     * @return true, if is block above
     */
    public boolean isBlockAbove(final IGetBlockPosition loc) {
        return blockY == loc.getBlockY() + 1 && blockX == loc.getBlockX() && blockZ == loc.getBlockZ();
    }

    /**
     * Check if this location is above the given one (blockY + 1).
     *
     * @param loc
     *            the loc
     * @return true, if is block above
     */
    public boolean isBlockAbove(final Location loc) {
        return blockY == loc.getBlockY() + 1 && blockX == loc.getBlockX() && blockZ == loc.getBlockZ();
    }

    /**
     * Compares exact coordinates (not the world).
     *
     * @param loc
     *            the loc
     * @return true, if is same pos
     */
    public boolean isSamePos(final IGetPosition loc) {
        return x == loc.getX() && z == loc.getZ() && y == loc.getY();
    }

    /**
     * Compares exact coordinates (not the world).
     *
     * @param loc
     *            the loc
     * @return true, if is same pos
     */
    public boolean isSamePos(final Location loc) {
        return x == loc.getX() && z == loc.getZ() && y == loc.getY();
    }

    /**
     * Manhattan distance, see Trigutil.
     *
     * @param other
     *            the other
     * @return the int
     */
    public int manhattan(final IGetBlockPosition other) {
        // TODO: Consider using direct field access from other methods as well.
        return TrigUtil.manhattan(this.blockX, this.blockY, this.blockZ, other.getBlockX(), other.getBlockY(), other.getBlockZ());
    }

    /**
     * Maximum block distance comparing dx, dy, dz.
     *
     * @param other
     *            the other
     * @return the int
     */
    public int maxBlockDist(final IGetBlockPosition other) {
        // TODO: Consider using direct field access from other methods as well.
        return TrigUtil.maxDistance(this.blockX, this.blockY, this.blockZ, other.getBlockX(), other.getBlockY(), other.getBlockZ());
    }

    /**
     * Quick check for really bad coordinates (actual problem, if true is
     * returned.).
     *
     * @return true, if successful
     */
    public boolean hasIllegalCoords() {
        return LocUtil.isBadCoordinate(minX, maxX, minY, maxY, minZ, maxZ);
    }

    /**
     * Get the collected block-flags. This will return null if collectBlockFlags
     * has not been called.
     *
     * @return the block flags
     */
    public Long getBlockFlags() {
        return blockFlags;
    }

    /**
     * Set the block flags which are usually collected on base of bounding box,
     * yOnGround and other considerations, such as 1.5 high blocks.
     *
     * @param blockFlags
     *            the new block flags
     */
    public void setBlockFlags(Long blockFlags) {
        this.blockFlags = blockFlags;
    }

    /**
     * Not cached.
     *
     * @return the type id above
     */
    public Material getTypeIdAbove() {
        return blockCache.getType(blockX, blockY + 1,  blockZ);
    }

    /**
     * Get existing or create.
     * @return
     */
    public IBlockCacheNode getOrCreateBlockCacheNode() {
        if (node == null) {
            node = blockCache.getOrCreateBlockCacheNode(blockX, blockY, blockZ, false);
        }
        return node;
    }

    /**
     * Get existing or create.
     * @return
     */
    public IBlockCacheNode getOrCreateBlockCacheNodeBelow() {
        if (nodeBelow == null) {
            nodeBelow = blockCache.getOrCreateBlockCacheNode(blockX, blockY - 1, blockZ, false);
        }
        return nodeBelow;
    }

    /**
     * Gets the type id.
     *
     * @return the type id
     */
    public Material getTypeId() {
        if (node == null) {
            getOrCreateBlockCacheNode();
        }
        return node.getType();
    }

    /**
     * Gets the type id below.
     *
     * @return the type id below
     */
    public Material getTypeIdBelow() {
        if (nodeBelow == null) {
            getOrCreateBlockCacheNodeBelow();
        }
        return nodeBelow.getType();
    }

    /**
     * Gets the data.
     *
     * @return the data
     */
    public Integer getData() {
        if (node == null) {
            node = blockCache.getOrCreateBlockCacheNode(blockX, blockY, blockZ, false);
            return node.getData(blockCache, blockX, blockY, blockZ);
        }
        return node.getData();
    }

    /**
     * Uses id cache if present.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return the type id
     */
    public final Material getTypeId(final int x, final int y, final int z) {
        return blockCache.getType(x, y, z);
    }

    /**
     * Uses id cache if present.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return the data
     */
    public final int getData(final int x, final int y, final int z) {
        return blockCache.getData(x, y, z);
    }

    /**
     * Set the id cache for faster id getting.
     *
     * @param cache
     *            the new block cache
     */
    public void setBlockCache(final BlockCache cache) {
        this.blockCache = cache;
    }

    /**
     * Get the underlying BlockCache.
     *
     * @return the block cache
     */
    public final BlockCache getBlockCache() {
        return blockCache;
    }

    /**
     * Check if the player is in liquid within a given margin.
     * 
     * @param yMargin y margin to contract the player's bounding box with vertically.
     * @return 
     */
    public boolean isSubmerged(final double yMargin) {
        return BlockProperties.collides(blockCache, minX, minY + yMargin, minZ, maxX, maxY, maxZ, BlockFlags.F_LIQUID);
    }

    /**
     * Check if blocks with the attached flags hit the box.
     *
     * @param xzMargin
     *            the xz margin
     * @param flags
     * @return True, if is next to the block with the attached flags.
     */
    public boolean isNextToBlock(final double xzMargin, final long flags) {
        return BlockProperties.collides(blockCache, minX - xzMargin, minY, minZ - xzMargin, maxX + xzMargin, maxY, maxZ + xzMargin, flags);
    }

    /**
     * Check if ground-like blocks hit the box.
     *
     * @param xzMargin
     *            the xz margin
     * @param yMargin
     *            the y margin
     * @return true, if is next to ground
     */
    public boolean isNextToGround(final double xzMargin, final double yMargin) {
        return BlockProperties.collides(blockCache, minX - xzMargin, minY - yMargin, minZ - xzMargin, maxX + xzMargin, maxY + yMargin, maxZ + xzMargin, BlockFlags.F_GROUND);
    }

    /**
     * Check if solid blocks hit the box.
     *
     * @param xzMargin
     *            the xz margin
     * @param yMargin
     *            the y margin
     * @return true, if is next to ground
     */
    public boolean isNextToSolid(final double xMargin, final double zMargin) {
        return BlockProperties.collides(blockCache, minX - xMargin, minY, minZ - zMargin, maxX + xMargin, maxY, maxZ + zMargin, BlockFlags.F_SOLID);
    }
    
    /**
     * Test if the location is inside a block with the given flag(s), using Minecraft's margins.
     * 
     * @param flags The flags attached to the block.
     * @return True, if the player is inside the block with the attached flags.
     */
    public boolean isInsideBlock(final long flags) {
        return BlockProperties.collides(blockCache, minX + 0.001, minY + 0.001, minZ + 0.001, maxX - 0.001, maxY - 0.001, maxZ - 0.001, flags);
    }

    /**
     * Convenience method.
     * Check if the location is on ground and if it is hitting the bounding box of a block with the given flags. <br>
     * Does not check for any other condition, thus, this might return true for blocks that may not actually allow players to stand on. <br>
     * Uses the yOnGround parameter.
     *
     * @param flags
     *            the flags
     * @return True, if the player is on ground and standing on the block with the attached flag(s).
     */
    public boolean standsOnBlock(final long flags) {
        if (!isOnGround()) {
            return false;
        }
        return BlockProperties.collides(blockCache, minX, minY - yOnGround, minZ, maxX, minY, maxZ, flags);
    }

    /**
     * @return true, if is above stairs
     */
    public boolean isAboveStairs() {
        if (aboveStairs == null) {
            if (blockFlags != null && (blockFlags.longValue() & BlockFlags.F_STAIRS) == 0) {
                aboveStairs = false;
                return false;
            }
            aboveStairs = standsOnBlock(BlockFlags.F_STAIRS);
        }
        return aboveStairs;
    }

    /**
     * Checks if the location is in lava using Minecraft collision logic.
     * 
     * @return true, if the player is in lava
     */
    public boolean isInLava() {
        if (inLava == null) {
            if (blockFlags != null && (blockFlags.longValue() & BlockFlags.F_LAVA) == 0) {
                inLava = false;
                return false;
            }
            inLava = false;
            final int iMinX = MathUtil.floor(minX + 0.001);
            final int iMaxX = MathUtil.ceil(maxX - 0.001);
            final int iMinY = MathUtil.floor(minY + 0.001); // + 0.001 <- Minecraft actually deflates the AABB by this amount.
            final int iMaxY = MathUtil.ceil(maxY - 0.001);
            final int iMinZ = MathUtil.floor(minZ + 0.001);
            final int iMaxZ = MathUtil.ceil(maxZ - 0.001);
            // NMS collision method
            for (int iX = iMinX; iX < iMaxX; iX++) {
                for (int iY = iMinY; iY < iMaxY; iY++) {
                    for (int iZ = iMinZ; iZ < iMaxZ; iZ++) {
                        double liquidHeight = BlockProperties.getLiquidHeight(blockCache, iX, iY, iZ, BlockFlags.F_LAVA);
                        double liquidHeightToWorld = iY + liquidHeight;
                        if (liquidHeightToWorld > minY && liquidHeight != 0.0) {
                            // Collided.
                            inLava = true;
                            return inLava;
                        }
                    }
                }
            }
        }
        return inLava;
    }

    /**
     * Checks if the location is in water using Minecraft collision logic.
     * 
     * @return true, if is in water
     */
    public boolean isInWater() {
        if (inWater == null) {
            if (!isInWaterLogged() && blockFlags != null && (blockFlags.longValue() & BlockFlags.F_WATER) == 0) {
                inWater = false;
                return false;
            }
            inWater = isInWaterLogged();
            if (inWater) return true;
            final int iMinX = MathUtil.floor(minX + 0.001);
            final int iMaxX = MathUtil.ceil(maxX - 0.001);
            final int iMinY = MathUtil.floor(minY + 0.001); // + 0.001 <- Minecraft actually deflates the AABB by this amount.
            final int iMaxY = MathUtil.ceil(maxY - 0.001);
            final int iMinZ = MathUtil.floor(minZ + 0.001);
            final int iMaxZ = MathUtil.ceil(maxZ - 0.001);
            // NMS collision method
            for (int iX = iMinX; iX < iMaxX; iX++) {
                for (int iY = iMinY; iY < iMaxY; iY++) {
                    for (int iZ = iMinZ; iZ < iMaxZ; iZ++) {
                        double liquidHeight = BlockProperties.getLiquidHeight(blockCache, iX, iY, iZ, BlockFlags.F_WATER);
                        double liquidHeightToWorld = iY + liquidHeight;
                        if (liquidHeightToWorld >= minY && liquidHeight != 0.0) {
                            // Collided.
                            inWater = true;
                            return inWater;
                        }
                    }
                }
            }
        }
        return inWater;
    }
    
    /**
     * @return true, if is in a water logged block.
     */
    public boolean isInWaterLogged() {
        if (inWaterLogged == null) {
            inWaterLogged = BlockProperties.isWaterlogged(world, blockCache, minX, minY, minZ, maxX, maxY, maxZ);
        }
        return inWaterLogged;
    }

    /**
     * @return true, if is in iquid
     */
    public boolean isInLiquid() {
        if (!isInWaterLogged() && blockFlags != null && (blockFlags.longValue() & BlockFlags.F_LIQUID) == 0) {
            return false;
        }
        return isInWater() || isInLava();
    }

    /**
     * Contains special casing for trapdoors above ladders.
     * 
     * @return If so.
     */
    public boolean isOnClimbable() {
        if (onClimbable == null) {
            // Early return with flags set and no climbable nearby.
            final Material typeId = getTypeId();
            if (blockFlags != null && (blockFlags & BlockFlags.F_CLIMBABLE) == 0
                // Special case trap doors: // Better than increasing maxYOnGround.
                && (blockFlags & BlockFlags.F_PASSABLE_X4) == 0) {
                onClimbable = false;
                return false;
            }
            
            final long thisFlags = BlockFlags.getBlockFlags(typeId);
            onClimbable = (thisFlags & BlockFlags.F_CLIMBABLE) != 0;
            if (!onClimbable) {
                // Special case trap door (simplified preconditions check).
                // TODO: Distance to the wall?
                if ((thisFlags & BlockFlags.F_PASSABLE_X4) != 0
                    && BlockProperties.isTrapDoorAboveLadderSpecialCase(blockCache, blockX, blockY, blockZ)) {
                    onClimbable = true;
                }
            }
        }
        return onClimbable;
    }

    /**
     * Blocks that have "data-reset potential". <br>
     * Namely fall distance, but not exclusively (i.e.: jumping phase is reset if in/on these blocks). <br>
     * Mostly concerns stuck-speed blocks, but can/does include blocks that aren't strictly related from one another, such as liquids and climbables.
     * Does not check for ground (!)
     *
     * @return true, if is reset condition
     */
    public boolean isResetCond() {
        // NOTE: if optimizing, setYOnGround has to be kept in mind. 
        return isInLiquid() || isOnClimbable() || isInWeb() || isInBerryBush() || isInPowderSnow();
    }

    /**
     * Checks if the player is above a ladder or vine.<br>
     * Does not save back value to field.
     * 
     * @return If so.
     */
    public boolean isAboveLadder() {
        if (blockFlags != null && (blockFlags & BlockFlags.F_CLIMBABLE) == 0) return false;
        return (BlockFlags.getBlockFlags(getTypeIdBelow()) & BlockFlags.F_CLIMBABLE) != 0;
    }

    /**
     * @return true, if is in web
     */
    public boolean isInWeb() {
        if (inWeb == null) {
            if (blockFlags == null || (blockFlags & BlockFlags.F_COBWEB) != 0L) {
                inWeb = isInsideBlock(BlockFlags.F_COBWEB);
            }
            else {
                inWeb = false;
            }
        }
        return inWeb;
    }
    
    /**
     * Further conditions can be found in RichEntityLocation.
     * @return true, if is in powder snow.
     */
    public boolean isInPowderSnow() {
        if (inPowderSnow == null) {
            if (blockFlags == null || (blockFlags & BlockFlags.F_POWDERSNOW) != 0L) {
                // TODO/ISSUE: Players are considered in powder snow, only if feet collide with the block. (jumping)
                // TODO: This check needs to be re-done.
                // If you stand on the very edge of the block:
                // Vertically, you jump with standard motion (as if you were outside the block)
                // Horizontally, you DO get slowed-down (as if you were inside the block)
                inPowderSnow = isInsideBlock(BlockFlags.F_POWDERSNOW)
                               // To be considered "in" powder snow, the player needs to be supported by more powder snow (remember that pwdsnw has the ground flag) below (or other solid-ground blocks)
                               // This fixes an edge case with player jumping with powder snow above (in which case, they are not considered "inside", the game applies the ordinary gravity motion)
                               && BlockProperties.isGround(getTypeId(getBlockX(), Location.locToBlock(getY() - 0.01), getBlockZ()));
            }
            else {
                inPowderSnow = false;
            }
        }
        return inPowderSnow;
    }

    /**
     * Cross-version checking is done in RichEntityLocation.
     * @return true, if is in a berry bush.
     */
    public boolean isInBerryBush() {
        if (inBerryBush == null) {
            if (blockFlags == null || (blockFlags & BlockFlags.F_BERRY_BUSH) != 0L) {
                inBerryBush = isInsideBlock(BlockFlags.F_BERRY_BUSH);
            }
            else {
                inBerryBush = false;
            }
        }
        return inBerryBush;
    }

    /**     
     * @return true, if is on ice
     */
    public boolean isOnIce() {
        if (onIce == null) {
            if (blockFlags != null && (blockFlags & BlockFlags.F_ICE) == 0) {
                onIce = false;
            } 
            else {
                onIce = standsOnBlock(BlockFlags.F_ICE);
            }
        }
        return onIce;
    }

    /**
     * Check the location is on blue ice, only regarding the center. Currently
     * demands to be on ground as well.
     *
     * @return true, if is on blue ice
     */
    public boolean isOnBlueIce() {
        if (onBlueIce == null) {
            if (blockFlags != null && (blockFlags & BlockFlags.F_BLUE_ICE) == 0) {
                onBlueIce = false;
            } 
            else {
                onBlueIce = standsOnBlock(BlockFlags.F_BLUE_ICE);
            }
        }
        return onBlueIce;
    }

    /**
     * Further conditions can be found in RichEntityLocation.
     * @return true, if is in soul sand
     */
    public boolean isInSoulSand() {
        if (inSoulSand == null) {
            if (blockFlags != null && (blockFlags & BlockFlags.F_SOULSAND) == 0) {
                inSoulSand = false;
            } 
            else {
                inSoulSand = (BlockFlags.getBlockFlags(getTypeId()) & BlockFlags.F_SOULSAND) != 0;
            }
        }
        return inSoulSand;
    }

    /**
     * Check if the location is on slime only regarding the center. Currently
     * demands to be on ground as well.
     *
     * @return true, if is on slime block
     */
    public boolean isOnSlimeBlock() {
        if (onSlimeBlock == null) {
            if (blockFlags != null && (blockFlags & BlockFlags.F_SLIME) == 0) {
                onSlimeBlock = false;
            } 
            else { 
                final Material typeId = getTypeIdBelow();
                final long thisFlags = BlockFlags.getBlockFlags(typeId);
                onSlimeBlock = isOnGround() && (thisFlags & BlockFlags.F_SLIME) != 0;  
            }
        }
        return onSlimeBlock;
    }

    /**
     * Check if the location is on a bouncy block only regarding the center. Currently
     * demands to be on ground as well.
     *
     * @return true, if is on a bouncy block
     */
    public boolean isOnBouncyBlock() {
        if (onBouncyBlock == null) {
            if (isOnSlimeBlock()) {
                onBouncyBlock = true;
            }
            else if (blockFlags != null && (blockFlags & BlockFlags.F_BED) == 0) {
                onBouncyBlock = false;
            }
            else onBouncyBlock = standsOnBlock(BlockFlags.F_BED);
        }
        return onBouncyBlock;
    }

    /**
     * Check if the location is on honey block.
     *
     * @return true, if is on honey block
     */
    public boolean isOnHoneyBlock() {
        if (onHoneyBlock == null) {
            if (blockFlags != null && (blockFlags & BlockFlags.F_STICKY) == 0) {
                onHoneyBlock = false;
            } 
            else {
                // Only count in actually being in the honeyblock, players can jump normally on the very edge.
                // TODO: ^ No longer true with 1.20. Fix in RichEntityLocation.
                onHoneyBlock = (BlockFlags.getBlockFlags(getTypeId()) & BlockFlags.F_STICKY) != 0;
            }
        }
        return onHoneyBlock;
    }

    /**
     * Check if the location is in bubblestream
     *
     * @return true, if is an bubblestream
     */
    public boolean isInBubbleStream() {
        if (inBubblestream == null) {
            if (blockFlags == null || (blockFlags & BlockFlags.F_BUBBLECOLUMN) != 0L) {
               inBubblestream = isInsideBlock(BlockFlags.F_BUBBLECOLUMN) && !isDraggedByBubbleStream();
            }
            else {
                inBubblestream = false;
            }
        }
        return inBubblestream;
     
    }

    /**
     * Check the if the location is in a bubblestream with drag value
     *
     * @return true, if is dragged by a bubble stream.
     */
    public boolean isDraggedByBubbleStream() {
        return BlockProperties.isBubbleColumnDrag(world, blockCache, minX + 0.001, minY + 0.001, minZ + 0.001, maxX - 0.001, maxY - 0.001, maxZ - 0.001);
    }

    /**
     * Test if the location is on rails (assuming minecarts with some magic
     * bounds/behavior).
     *
     * @return true, if is on rails
     */
    public boolean isOnRails() {
        return BlockProperties.isRails(getTypeId()) || y - blockY < 0.3625 && BlockProperties.isAscendingRails(getTypeIdBelow(), getData(blockX, blockY - 1, blockZ));
    }

    /**
     * Checks if the thing is on ground, including entities (RichEntityLocation) such as Boat.
     * 
     * @return true, if the player is on ground
     */
    public boolean isOnGround() {
        if (onGround != null) {
            return onGround;
        }
        // Check cached values and simplifications.
        if (notOnGroundMaxY >= yOnGround) {
            onGround = false;
        }
        else if (onGroundMinY <= yOnGround) {
            onGround = true;
        }
        else {
            // Shortcut check (currently needed for being stuck + sf).
            if (blockFlags == null || (blockFlags.longValue() & BlockFlags.F_GROUND) != 0) {
                // TODO: Consider dropping this shortcut.
                final int bY = Location.locToBlock(y - yOnGround);
                final IBlockCacheNode useNode = bY == blockY ? getOrCreateBlockCacheNode() : (bY == blockY -1 ? getOrCreateBlockCacheNodeBelow() : blockCache.getOrCreateBlockCacheNode(blockX,  bY, blockZ, false));
                final Material id = useNode.getType();
                final long flags = BlockFlags.getBlockFlags(id);
                // TODO: Might remove check for variable ?
                if ((flags & BlockFlags.F_GROUND) != 0 && (flags & BlockFlags.F_VARIABLE) == 0) {
                    final double[] bounds = useNode.getBounds(blockCache, blockX, bY, blockZ);
                    // Check collision if not inside of the block. [Might be a problem for cauldron or similar + something solid above.]
                    // TODO: Might need more refinement.
                    if (bounds != null && y - bY >= bounds[4] && BlockProperties.collidesBlock(blockCache, x, minY - yOnGround, z, x, minY, z, blockX, bY, blockZ, useNode, null, flags)) {
                        // TODO: BlockHeight is needed for fences, use right away (above)?
                        if (!BlockProperties.isPassableWorkaround(blockCache, blockX, bY, blockZ, minX - blockX, minY - yOnGround - bY, minZ - blockZ, useNode, maxX - minX, yOnGround, maxZ - minZ,  1.0)
                            || (flags & BlockFlags.F_GROUND_HEIGHT) != 0 && BlockProperties.getGroundMinHeight(blockCache, blockX, bY, blockZ, useNode, flags) <= y - bY) {
                            //  NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, "*** onground SHORTCUT");
                            onGround = true;
                        }
                    }
                }
                if (onGround == null) {
                    //NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, "*** fetch onground std");
                    // Full on-ground check (blocks).
                    // Note: Might check for half-block height too (getTypeId), but that is much more seldom.
                    onGround = BlockProperties.isOnGround(world, blockCache, minX, minY - yOnGround, minZ, maxX, minY, maxZ, 0L);
                }
            }
            else {
                onGround = false;
            }
        }
        if (onGround) {
            onGroundMinY = Math.min(onGroundMinY, yOnGround);
        }
        else {
            notOnGroundMaxY = Math.max(notOnGroundMaxY, yOnGround);
        }
        return onGround;
    }

    /**
     * Simple block-on-ground check for given margin (no entities).<br> Meant for
     * checking bigger margin than the normal yOnGround.
     *
     * @param yOnGround
     *            Margin below the player.
     * @return true, if is on ground
     */
    public boolean isOnGround(final double yOnGround) {
        if (notOnGroundMaxY >= yOnGround) return false;
        else if (onGroundMinY <= yOnGround) return true;
        return isOnGround(yOnGround, 0D, 0D, 0L);
    }
    
    /**
     * Check for on ground status, ignoring the block with ignoreFlags attached (no entities).
     *
     * @param yOnGround
     *            the y on ground
     * @param ignoreFlags
     *            Flags to not regard as ground.
     * @return true, if is on ground
     */
    public boolean isOnGround(final long ignoreFlags) {
        // Full on-ground check (blocks).
        return BlockProperties.isOnGround(world, blockCache, minX, minY - yOnGround, minZ, maxX, minY, maxZ, ignoreFlags);
    }

    /**
     * Simple block-on-ground check for given margin (no entities). Meant for
     * checking bigger margin than the normal yOnGround.
     *
     * @param yOnGround
     *            the y on ground
     * @param ignoreFlags
     *            Flags to not regard as ground.
     * @return true, if is on ground
     */
    public boolean isOnGround(final double yOnGround, final long ignoreFlags) {
        if (ignoreFlags == 0) {
            if (notOnGroundMaxY >= yOnGround) return false;
            else if (onGroundMinY <= yOnGround) return true;
        }
        return isOnGround(yOnGround, 0D, 0D, ignoreFlags);
    }


    /**
     * Simple block-on-ground check for given margin (no entities). Meant for
     * checking bigger margin than the normal yOnGround.
     *
     * @param yOnGround
     *            Margin below the player.
     * @param xzMargin
     *            the xz margin
     * @param yMargin
     *            Extra margin added below and above.
     * @return true, if is on ground
     */
    public boolean isOnGround(final double yOnGround, final double xzMargin, final double yMargin) {
        if (xzMargin >= 0 && onGroundMinY <= yOnGround) return true;
        if (xzMargin <= 0 && yMargin == 0) {
            if (notOnGroundMaxY >= yOnGround) return false;
        }
        return isOnGround(yOnGround, xzMargin, yMargin, 0);
    }

    /**
     * Simple block-on-ground check for given margin (no entities, [RichEntityLocation]).<br> Meant for
     * checking bigger margin than the normal yOnGround.
     *
     * @param yOnGround
     *            Margin below the player.
     * @param xzMargin
     *            the xz margin
     * @param yMargin
     *            Extra margin added below and above.
     * @param ignoreFlags
     *            Flags to not regard as ground.
     * @return true, if is on ground
     */
    public boolean isOnGround(final double yOnGround, final double xzMargin, final double yMargin, final long ignoreFlags) {
        if (ignoreFlags == 0) {
            if (xzMargin >= 0 && onGroundMinY <= yOnGround) return true;
            if (xzMargin <= 0 && yMargin == 0) {
                if (notOnGroundMaxY >= yOnGround) return false;
            }
        }
        //NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, "*** Fetch on-ground: yOnGround=" + yOnGround + " xzM=" + xzMargin + " yM=" + yMargin + " ign=" + ignoreFlags);
        final boolean onGround = BlockProperties.isOnGround(world, blockCache, minX - xzMargin, minY - yOnGround - yMargin, minZ - xzMargin, maxX + xzMargin, minY + yMargin, maxZ + xzMargin, ignoreFlags);
        if (ignoreFlags == 0) {
            if (onGround) {
                if (xzMargin <= 0 && yMargin == 0) {
                    onGroundMinY = Math.min(onGroundMinY, yOnGround);
                }
            }
            else {
                if (xzMargin >= 0) {
                    notOnGroundMaxY = Math.max(notOnGroundMaxY, yOnGround);
                }
            }
        }
        return onGround;
    }

    /**
     * Check on-ground in a very opportunistic way, in terms of
     * fcfs+no-consistency+no-actual-side-condition-checks.
     * <hr>
     * Assume this gets called after the ordinary isOnGround has returned false.
     * 
     * @param loc
     * @param yShift
     * @param blockChangetracker
     * @param blockChangeRef
     * @return
     */
    public final boolean isOnGroundOpportune(final double yOnGround, final long ignoreFlags,
                                             final BlockChangeTracker blockChangeTracker, final BlockChangeReference blockChangeRef,
                                             final int tick) {
        // TODO: Consider updating onGround+dist cache.
        return blockChangeTracker.isOnGround(world, blockCache, blockChangeRef, tick, world.getUID(), minX, minY - yOnGround, minZ, maxX, maxY, maxZ, ignoreFlags);
    }

    /**
     * Convenience method for testing for either.<br>
     * Should be called if you want to be 100% sure that the player is only in air.
     *
     * @return true, if is on ground or reset cond
     */
    public boolean isOnGroundOrResetCond() {
        return isOnGround() || isResetCond();
    }

    /**
     * Gets the y on ground.
     *
     * @return the y on ground
     */
    public double getyOnGround() {
        return yOnGround;
    }

    /**
     * This resets onGround and blockFlags.
     *
     * @param yOnGround
     *            the new y on ground
     */
    public void setyOnGround(final double yOnGround) {
        this.yOnGround = yOnGround;
        this.onGround = null;
        blockFlags = null;
    }

    /**
     * Test if the foot location is passable (not the bounding box). <br>
     * The result is cached.
     *
     * @return true, if is passable
     */
    public boolean isPassable() {
        if (passable == null) {
            if (isBlockFlagsPassable()) {
                passable = true;
            }
            else {
                if (node == null) {
                    node = blockCache.getOrCreateBlockCacheNode(blockX, blockY, blockZ, false);
                }
                passable = BlockProperties.isPassable(blockCache, x, y, z, node, null);
            }
        }
        return passable;
    }

    /**
     * Test if the bounding box is colliding (passable check with accounting for
     * workarounds).
     *
     * @return true, if is passable box
     */
    public boolean isPassableBox() {
        // TODO: Might need a variation with margins as parameters.
        if (passableBox == null) {
            if (isBlockFlagsPassable()) {
                passableBox = true;
            }
            else if (passable != null && !passable) {
                passableBox = false;
            }
            else {
                // Fetch.
                passableBox = BlockProperties.isPassableBox(blockCache, minX, minY, minZ, maxX, maxY, maxZ);
            }
        }
        return passableBox;
    }

    /**
     * Checks if block flags are set and are (entirely) passable.
     *
     * @return true, if is block flags passable
     */
    private boolean isBlockFlagsPassable() {
        return blockFlags != null && (blockFlags & (BlockFlags.F_SOLID | BlockFlags.F_GROUND)) == 0;
    }

    /**
     * Set block flags using yOnGround, unless already set. <br>Check the maximally
     * used bounds for the block checking, to have flags ready for faster
     * denial.
     */
    public void collectBlockFlags() {
        if (blockFlags == null) {
            collectBlockFlags(yOnGround);
        }
    }

    /**
     * Check the maximally used bounds for the block checking, to have flags
     * ready for faster denial.
     *
     * @param maxYonGround
     *            the max yon ground
     */
    public void collectBlockFlags(double maxYonGround) {
        maxYonGround = Math.max(yOnGround, maxYonGround);
        // TODO: Clearly refine this for 1.5 high blocks.
        // TODO: Check which checks need blocks below.
        final double yExtra = 0.6; // y - blockY - maxYonGround > 0.5 ? 0.5 : 1.0;
        // TODO: xz margin still needed ?
        final double xzM = 0; //0.001;
        blockFlags = BlockProperties.collectFlagsSimple(blockCache, minX - xzM, minY - yExtra - maxYonGround, minZ - xzM, maxX + xzM, Math.max(maxY, minY + 1.5), maxZ + xzM);
    }

    /**
     * Check chunks within 1 block distance for if they are loaded and load unloaded chunks.
     * @return Number of chunks loaded.
     */
    public int ensureChunksLoaded() {
        return ensureChunksLoaded(1.0);
    }

    /**
     * Check chunks within xzMargin radius for if they are loaded and load
     * unloaded chunks.
     *
     * @param xzMargin
     *            the xz margin
     * @return Number of chunks loaded.
     */
    public int ensureChunksLoaded(final double xzMargin) {
        return MapUtil.ensureChunksLoaded(world, x, z, xzMargin);
    }

    /**
     * Check for tracked block changes, having moved a block into a certain
     * direction, using the full bounding box (pistons).
     * BlockChangeReference.updateSpan is called with the earliest entry found
     * (updateFinal has to be called extra). This is an opportunistic version
     * without any consistency checking done, just updating the span by the
     * earliest entry found.
     *
     * @param blockChangeTracker
     *            the block change tracker
     * @param ref
     *            the ref
     * @param direction
     *            Pass null to ignore the direction.
     * @param coverDistance
     *            The (always positive) distance to cover.
     * @return Returns true, iff an entry was found.
     */
    public boolean matchBlockChange(final BlockChangeTracker blockChangeTracker, final BlockChangeReference ref, 
                                    final Direction direction, final double coverDistance) {
        final int tick = TickTask.getTick();
        final UUID worldId = world.getUID();
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY);
        final int iMaxY = Location.locToBlock(maxY);
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        BlockChangeEntry minEntry = null;
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMinY; y <= iMaxY; y++) {
                    final BlockChangeEntry entry = blockChangeTracker.getBlockChangeEntry(ref, tick, worldId, x, y, z, direction);
                    if (entry != null && (minEntry == null || entry.id < minEntry.id)) {
                        // Check vs. coverDistance, exclude cases where the piston can't push that far.
                        if (coverDistance > 0.0 && coversDistance(x, y, z, direction, coverDistance)) {
                            minEntry = entry;
                        }
                    }
                }
            }
        }
        if (minEntry == null) {
            return false;
        }
        else {
            ref.updateSpan(minEntry);
            return true;
        }
    }

    /**
     * Check for tracked block changes, having moved a block into a certain
     * direction, confined to certain blocks hitting the player, using the full
     * bounding box (pistons), only regarding blocks having flags in common with
     * matchFlags. Thus not the replaced state at a position is regarded, but
     * the state that should result from a block having been pushed there.
     * BlockChangeReference.updateSpan is called with the earliest entry found
     * (updateFinal has to be called extra). This is an opportunistic version
     * without any consistency checking done, just updating the span by the
     * earliest entry found.
     *
     * @param blockChangeTracker
     *            the block change tracker
     * @param ref
     *            the ref
     * @param direction
     *            Pass null to ignore the direction.
     * @param coverDistance
     *            The (always positive) distance to cover.
     * @param matchFlags
     *            Only blocks with past states having any flags in common with
     *            matchFlags. If matchFlags is zero, the parameter is ignored.
     * @return Returns true, iff an entry was found.
     */
    public boolean matchBlockChangeMatchResultingFlags(final BlockChangeTracker blockChangeTracker, 
                                                       final BlockChangeReference ref, final Direction direction, final double coverDistance, 
                                                       final long matchFlags) {
        /*
         * TODO: Not sure with code duplication. Is it better to run
         * BlockChangeTracker.getBlockChangeMatchFlags for the other method too?
         */
        // TODO: Intended use is bouncing off slime, thus need confine to foot level ?
        final int tick = TickTask.getTick();
        final UUID worldId = world.getUID();
        // Shift the entire search box to the opposite direction (if direction is given).
        final BlockFace blockFace = direction == null ? BlockFace.SELF : direction.blockFace;
        final int iMinX = Location.locToBlock(minX) - blockFace.getModX();
        final int iMaxX = Location.locToBlock(maxX) - blockFace.getModX();
        final int iMinY = Location.locToBlock(minY) - blockFace.getModY();
        final int iMaxY = Location.locToBlock(maxY) - blockFace.getModY();
        final int iMinZ = Location.locToBlock(minZ) - blockFace.getModZ();
        final int iMaxZ = Location.locToBlock(maxZ) - blockFace.getModZ();
        BlockChangeEntry minEntry = null;
        
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMinY; y <= iMaxY; y++) {
                    final BlockChangeEntry entry = blockChangeTracker.getBlockChangeEntryMatchFlags(ref, tick, worldId, x, y, z, direction, matchFlags);
                    if (entry != null && (minEntry == null || entry.id < minEntry.id)) {
                        // Check vs. coverDistance, exclude cases where the piston can't push that far.
                        if (coverDistance > 0.0 
                            && coversDistance(x + blockFace.getModX(), y + blockFace.getModY(), z + blockFace.getModZ(), 
                                              direction, coverDistance)) {
                            minEntry = entry;
                        }
                    }
                }
            }
        }
        if (minEntry == null) {
            return false;
        }
        else {
            ref.updateSpan(minEntry);
            return true;
        }
    }

    /**
     * Test, if the block intersects the bounding box, if assuming full bounds.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if the block is intersecting
     */
    public boolean isBlockIntersecting(final int x, final int y, final int z) {
        return CollisionUtil.intersectsBlock(minX, maxX, x) && CollisionUtil.intersectsBlock(minY, maxY, y) && CollisionUtil.intersectsBlock(minZ, maxZ, z);
    }

    /**
     * Test, if either of two blocks intersects the bounding box, if assuming
     * full bounds.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param blockFace
     *            An additional block to check from the coordinates into that
     *            direction.
     * @return true, if either block is intersecting
     */
    public boolean isBlockIntersecting(final int x, final int y, final int z, final BlockFace blockFace) {
        return isBlockIntersecting(x, y, z) || isBlockIntersecting(x + blockFace.getModX(), y + blockFace.getModY(), z + blockFace.getModZ());
    }

    /**
     * Test, if a block fully moved into that direction can move the player by
     * coverDistance.
     *
     * @param x
     *            Block coordinates.
     * @param y
     *            the y
     * @param z
     *            the z
     * @param direction
     *            the direction
     * @param coverDistance
     *            the cover distance
     * @return true, if successful
     */
    private boolean coversDistance(final int x, final int y, final int z, final Direction direction, final double coverDistance) {
        switch (direction) {
            case Y_POS: {
                return y + 1.0 - Math.max(minY, (double) y) >= coverDistance;
            }
            case Y_NEG: {
                return Math.min(maxY, (double) y + 1) - y >= coverDistance;
            }
            case X_POS: {
                return x + 1.0 - Math.max(minX, (double) x) >= coverDistance;
            }
            case X_NEG: {
                return Math.min(maxX, (double) x + 1) - x >= coverDistance;
            }
            case Z_POS: {
                return z + 1.0 - Math.max(minZ, (double) z) >= coverDistance;
            }
            case Z_NEG: {
                return Math.min(maxZ, (double) z + 1) - z >= coverDistance;
            }
            default: {
                // Assume anything does (desired direction is NONE, read as ALL, thus accept all).
                return true;
            }
        }
    }

    /**
     * Set cached info according to other.<br>
     * Minimal optimizations: take block flags directly, on-ground max/min
     * bounds, only set stairs if not on ground and not reset-condition.
     *
     * @param other
     *            the other
     */
    public void prepare(final RichBoundsLocation other) {
        // Simple first.
        this.blockFlags = other.blockFlags; //  Assume set.
        this.notOnGroundMaxY = other.notOnGroundMaxY;
        this.onGroundMinY = other.onGroundMinY;
        this.passable = other.passable;
        this.passableBox = other.passableBox;
        // Access methods.
        this.node = other.node;
        this.nodeBelow = other.nodeBelow;
        this.onGround = other.isOnGround();
        this.inWater = other.isInWater();
        this.inWaterLogged = other.isInWaterLogged();
        this.inLava = other.isInLava();
        this.inWeb = other.isInWeb();
        this.inBerryBush = other.isInBerryBush();
        this.inBubblestream = other.isInBubbleStream();
        this.onHoneyBlock = other.isOnHoneyBlock();
        this.onSlimeBlock = other.isOnSlimeBlock();
        this.onIce = other.isOnIce();
        this.onBlueIce = other.isOnBlueIce();
        this.inSoulSand = other.isInSoulSand();
        this.inPowderSnow = other.isInPowderSnow();
        this.onClimbable = other.isOnClimbable();
        this.onBouncyBlock = other.isOnBouncyBlock();
        this.aboveStairs = other.isAboveStairs();
    }

    /**
     * @param location
     *            the location
     * @param fullWidth
     *            the full width
     * @param fullHeight
     *            the full height
     * @param yOnGround
     *            the y on ground
     */
    public void set(final Location location, final double fullWidth, final double fullHeight, final double yOnGround) {
        doSet(location, fullWidth, fullHeight, yOnGround);
    }

    /**
     * Do set.
     *
     * @param location
     *            the location
     * @param fullWidth
     *            the full width
     * @param fullHeight
     *            the full height
     * @param yOnGround
     *            the y on ground
     */
    protected void doSet(final Location location, final double fullWidth, final double fullHeight, final double yOnGround) {
        // Set coordinates.
        blockX = location.getBlockX();
        blockY = location.getBlockY();
        blockZ = location.getBlockZ();
        x = location.getX();
        y = location.getY();
        z = location.getZ();
        yaw = location.getYaw();
        pitch = location.getPitch();

        // Set bounding box.
        /*
         * For future reference, uhm...
         *  maxY (+ = Grow bounding box above head | - = Shrink bounding box below head)
         *  minY (- = Grow bounding box below feet | + = Shrink bounding box above feet)
         */
        // final double dxz = Math.round(fullWidth * 500.0) / 1000.0; // this.width / 2; // 0.3;
        final double dxz = Math.round(fullWidth * 500.0) / 1000.0; // fullWidth / 2f; <---- This -for some reasons- yields thisMove.headObstructed = true when moving against walls!
        minX = x - dxz;
        minY = y;
        minZ = z - dxz;
        maxX = x + dxz;
        maxY = y + fullHeight;
        maxZ = z + dxz;
        this.boxMarginHorizontal = dxz;
        this.boxMarginVertical = fullHeight;
        // TODO: With current bounding box the stance is never checked.

        // Set world / block access.
        world = location.getWorld();

        if (world == null) {
            throw new NullPointerException("World is null.");
        }

        // Reset cached values.
        node = nodeBelow = null;
        aboveStairs = inLava = inWater = inWaterLogged = inWeb = onIce = onBlueIce = inSoulSand  = onHoneyBlock = onSlimeBlock = inBerryBush = inPowderSnow = onGround = onClimbable = onBouncyBlock = passable = passableBox = inBubblestream = null;
        onGroundMinY = Double.MAX_VALUE;
        notOnGroundMaxY = Double.MIN_VALUE;
        blockFlags = null;

        this.yOnGround = yOnGround;
    }

    /**
     * Set some references to null.
     */
    public void cleanup() {
        world = null;
        blockCache = null; // No reset here.
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override    
    public int hashCode() {
        return LocUtil.hashCode(this);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(128);
        builder.append("RichBoundsLocation(");
        builder.append(world == null ? "null" : world.getName());
        builder.append('/');
        builder.append(Double.toString(x));
        builder.append(", ");
        builder.append(Double.toString(y));
        builder.append(", ");
        builder.append(Double.toString(z));
        builder.append(')');
        return builder.toString();
    }
}
